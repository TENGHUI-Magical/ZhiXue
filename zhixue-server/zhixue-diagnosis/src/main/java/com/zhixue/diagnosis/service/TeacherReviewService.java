package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.csv.CsvExportFile;
import com.zhixue.common.csv.CsvExportSupport;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.ReviewItemView;
import com.zhixue.diagnosis.dto.ReviewPageView;
import com.zhixue.diagnosis.dto.ReviewSubmitRequest;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import com.zhixue.user.service.UserQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师评阅：待评阅队列 + 提交评阅。
 * 待评阅口径：scene=RAG 且 reviewed_at IS NULL；重复评阅 40900，非教师 40300。
 *
 * <p>2026-10-31 C3：队列补评阅证据（题干/题型/选项/参考答案/解析/学生笔记/等待时长），
 * 支持 status / kpId / from / to 筛选。</p>
 */
@Service
public class TeacherReviewService {

    private static final Logger log = LoggerFactory.getLogger(TeacherReviewService.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EXPORT_BATCH_SIZE = 200;
    private static final List<String> EXPORT_HEADERS = List.of(
            "logPk", "explainId", "studentKey", "maskedName", "qId", "kpId", "errorType",
            "reviewResult", "teacherScore", "teacherComment", "reviewedAt", "model",
            "degraded", "createdAt");

    private final AiGenerationLogMapper logMapper;
    private final StudentErrorRecordMapper errorRecordMapper;
    private final CourseService courseService;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;
    private final UserQueryService userQueryService;
    private final ObjectMapper objectMapper;

    public TeacherReviewService(AiGenerationLogMapper logMapper,
                                StudentErrorRecordMapper errorRecordMapper,
                                CourseService courseService,
                                KnowledgePointService knowledgePointService,
                                QuestionBankService questionBankService,
                                UserQueryService userQueryService,
                                ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.errorRecordMapper = errorRecordMapper;
        this.courseService = courseService;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
        this.userQueryService = userQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 评阅队列，按 createdAt 倒序。
     *
     * @param status PENDING（默认）/ REVIEWED / ALL
     * @param kpId   按知识点筛选；为空不限
     * @param from   起始日期（含）；为空不限
     * @param to     结束日期（含）；为空不限
     */
    public ReviewPageView page(int page, int size, String status, String kpId,
                               LocalDate from, LocalDate to) {
        StpUtil.checkRole("TEACHER");
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "page 必须大于 0");
        }
        if (size < 1 || size > 50) {
            throw new BizException(ErrorCode.PARAM_ERROR, "size 必须在 1—50 之间");
        }
        ReviewFilter filter = reviewFilter(status, kpId, from, to);
        long total = logMapper.countForReview(filter.reviewed(), filter.kpId(),
                filter.fromTime(), filter.toTime());
        List<AiGenerationLog> rows = total == 0 ? List.of()
                : logMapper.selectForReview(filter.reviewed(), filter.kpId(),
                filter.fromTime(), filter.toTime(), size, (page - 1) * size);
        return new ReviewPageView(page, size, total, toViews(rows));
    }

    /** 兼容旧调用：默认待评阅、无筛选。 */
    public ReviewPageView page(int page, int size) {
        return page(page, size, "PENDING", null, null, null);
    }

    /** 当前课程与筛选下完整评阅导出；只读现有证据关系，不进入单条评阅状态机。 */
    public CsvExportFile exportCsv(long courseId, String status, String kpId,
                                   LocalDate from, LocalDate to) {
        StpUtil.checkRole("TEACHER");
        ReviewFilter filter = reviewFilter(status, kpId, from, to);
        ensureOwnership(courseId);
        String summary = "courseId=" + courseId + ";status=" + filter.status()
                + ";kpId=" + value(filter.kpId(), "all")
                + ";from=" + (from == null ? "all" : from)
                + ";to=" + (to == null ? "all" : to);
        List<AiGenerationLog> logs = logMapper.selectForReviewExport(
                courseId, filter.reviewed(), filter.kpId(), filter.fromTime(), filter.toTime());
        Map<String, StudentErrorRecord> errorById = loadExportErrors(logs);
        List<AiGenerationLog> courseLogs = filterToCourse(logs, errorById, courseId);
        Set<Long> userIds = courseLogs.stream().map(AiGenerationLog::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> usernames = userQueryService.findUsernames(userIds);
        Map<Long, String> names = userQueryService.findDisplayNames(userIds);
        List<List<?>> rows = new ArrayList<>(courseLogs.size());
        for (AiGenerationLog row : courseLogs) {
            StudentErrorRecord error = errorById.get(row.getLogId());
            Long userId = row.getUserId();
            String username = usernames.get(userId);
            String studentKey = username == null || username.isBlank()
                    ? userId == null ? "" : String.valueOf(userId) : username;
            rows.add(List.of(value(row.getLogPk()), value(row.getExplainId()), studentKey,
                    mask(names.get(userId)), error == null ? "" : value(error.getQId()),
                    error == null ? "" : value(error.getKpId()),
                    error == null ? "" : value(error.getErrorType()), value(row.getReviewResult()),
                    value(row.getTeacherScore()), value(row.getTeacherComment()),
                    row.getReviewedAt() == null ? "" : DATE_TIME.format(row.getReviewedAt()),
                    value(row.getModel()), Objects.equals(row.getIsDegraded(), 1),
                    row.getCreatedAt() == null ? "" : DATE_TIME.format(row.getCreatedAt())));
        }
        CsvExportFile file = CsvExportSupport.create("teacher-reviews", summary, EXPORT_HEADERS, rows);
        log.info("TEACHER_EXPORT_AUDIT teacherUserId={} exportedAt={} endpoint={} filters={} rowCount={}",
                StpUtil.getLoginIdAsLong(), file.exportedAt(), "/api/teacher/reviews/export",
                summary.replaceAll("[\\r\\n]+", " "), file.rowCount());
        return file;
    }

    private void ensureOwnership(long courseId) {
        if (courseId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "courseId 非法");
        }
        Course course = courseService.getActiveById(courseId);
        long teacherId = StpUtil.getLoginIdAsLong();
        if (course.getTeacherId() != null && !Objects.equals(course.getTeacherId(), teacherId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看其他教师的课程");
        }
    }

    /** 与 SQL 同口径的防御性过滤：他课程或无 error→question 可靠课程证据的旧日志均排除。 */
    private List<AiGenerationLog> filterToCourse(List<AiGenerationLog> logs,
                                                 Map<String, StudentErrorRecord> errorById,
                                                 long courseId) {
        if (logs.isEmpty()) {
            return List.of();
        }
        Map<String, QuestionBank> questionById = loadExportQuestions(errorById);
        List<AiGenerationLog> result = new ArrayList<>(logs.size());
        for (AiGenerationLog row : logs) {
            StudentErrorRecord error = errorById.get(row.getLogId());
            QuestionBank question = error == null || error.getQId() == null
                    ? null : questionById.get(error.getQId());
            if (question != null && Objects.equals(question.getCourseId(), courseId)) {
                result.add(row);
            }
        }
        return result;
    }

    private Map<String, QuestionBank> loadExportQuestions(
            Map<String, StudentErrorRecord> errorById) {
        List<String> qIds = errorById.values().stream().map(StudentErrorRecord::getQId)
                .filter(Objects::nonNull).filter(id -> !id.isBlank()).distinct().toList();
        Map<String, QuestionBank> result = new java.util.HashMap<>();
        for (int from = 0; from < qIds.size(); from += EXPORT_BATCH_SIZE) {
            result.putAll(questionBankService.findByIds(
                    qIds.subList(from, Math.min(qIds.size(), from + EXPORT_BATCH_SIZE))));
        }
        return result;
    }

    private Map<String, StudentErrorRecord> loadExportErrors(List<AiGenerationLog> logs) {
        List<String> logIds = logs.stream().map(AiGenerationLog::getLogId)
                .filter(Objects::nonNull).filter(id -> !id.isBlank()).distinct().toList();
        Map<String, StudentErrorRecord> result = new java.util.HashMap<>();
        for (int from = 0; from < logIds.size(); from += EXPORT_BATCH_SIZE) {
            List<String> batch = logIds.subList(from, Math.min(logIds.size(), from + EXPORT_BATCH_SIZE));
            for (StudentErrorRecord error : errorRecordMapper.selectBatchIds(batch)) {
                result.putIfAbsent(error.getLogId(), error);
            }
        }
        return result;
    }

    private ReviewFilter reviewFilter(String status, String kpId, LocalDate from, LocalDate to) {
        String st = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase();
        if (!Set.of("PENDING", "REVIEWED", "ALL").contains(st)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 仅允许 PENDING/REVIEWED/ALL");
        }
        Boolean reviewed = switch (st) {
            case "REVIEWED" -> Boolean.TRUE;
            case "ALL" -> null;
            default -> Boolean.FALSE;
        };
        String kp = kpId == null || kpId.isBlank() ? null : kpId.trim();
        return new ReviewFilter(st, reviewed, kp,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.atTime(23, 59, 59));
    }

    private record ReviewFilter(String status, Boolean reviewed, String kpId,
                                LocalDateTime fromTime, LocalDateTime toTime) {
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String value(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static String mask(String name) {
        if (name == null || name.isBlank()) {
            return "学生";
        }
        String value = name.trim();
        if (value.length() == 1) {
            return value;
        }
        if (value.length() == 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    /** 提交评阅：reviewed_at IS NULL 才允许写；重复/并发抢先返回 40900。 */
    @Transactional
    public void submit(long logPk, ReviewSubmitRequest request) {
        StpUtil.checkRole("TEACHER");
        AiGenerationLog row = logMapper.selectById(logPk);
        if (row == null || !"RAG".equals(row.getScene())) {
            throw new BizException(ErrorCode.NOT_FOUND, "评阅记录不存在: " + logPk);
        }
        if (row.getReviewedAt() != null) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该讲解已评阅: " + logPk);
        }
        String result = request.result() == null || request.result().isBlank()
                ? "PASS" : request.result().trim().toUpperCase();
        if (!Set.of("PASS", "RETURN").contains(result)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "result 仅允许 PASS/RETURN");
        }
        if ("RETURN".equals(result)
                && (request.comment() == null || request.comment().isBlank())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退回必须填写评语");
        }
        int rows = logMapper.update(null, new LambdaUpdateWrapper<AiGenerationLog>()
                .eq(AiGenerationLog::getLogPk, logPk)
                .isNull(AiGenerationLog::getReviewedAt)
                .set(AiGenerationLog::getTeacherScore, request.score())
                .set(AiGenerationLog::getTeacherComment, request.comment())
                .set(AiGenerationLog::getReviewResult, result)
                .set(AiGenerationLog::getReviewedAt, LocalDateTime.now()));
        if (rows == 0) {
            AiGenerationLog fresh = logMapper.selectById(logPk);
            if (fresh == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "评阅记录不存在: " + logPk);
            }
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该讲解已评阅: " + logPk);
        }
    }

    private List<ReviewItemView> toViews(List<AiGenerationLog> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> logIds = rows.stream().map(AiGenerationLog::getLogId)
                .filter(Objects::nonNull).filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, StudentErrorRecord> errorById = logIds.isEmpty() ? Map.of()
                : errorRecordMapper.selectBatchIds(logIds).stream()
                .collect(Collectors.toMap(StudentErrorRecord::getLogId, r -> r, (a, b) -> a));

        Set<String> kpIds = new LinkedHashSet<>();
        rows.forEach(row -> kpIds.addAll(parseSourceKpIds(row.getSourceIds())));
        rows.forEach(row -> {
            StudentErrorRecord error = errorById.get(row.getLogId());
            if (error != null && error.getKpId() != null && !error.getKpId().isBlank()) {
                kpIds.add(error.getKpId());
            }
        });
        Map<String, KnowledgePoint> kpById = knowledgePointService.findByIds(kpIds);

        // C3：批量取题干/选项/参考答案/解析
        Set<String> qIds = errorById.values().stream()
                .map(StudentErrorRecord::getQId)
                .filter(Objects::nonNull).filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, QuestionBank> questionById = qIds.isEmpty() ? Map.of()
                : questionBankService.findByIds(qIds);

        Set<Long> userIds = rows.stream().map(AiGenerationLog::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> userNameById = userQueryService.findDisplayNames(userIds);

        LocalDateTime now = LocalDateTime.now();
        List<ReviewItemView> result = new ArrayList<>(rows.size());
        for (AiGenerationLog row : rows) {
            StudentErrorRecord error = errorById.get(row.getLogId());
            QuestionBank question = error == null || error.getQId() == null
                    ? null : questionById.get(error.getQId());
            List<ReviewItemView.SourceRef> sources = buildSources(row, kpById, error);
            String kpId = error != null && error.getKpId() != null ? error.getKpId()
                    : sources.isEmpty() ? null : sources.get(0).kpId();
            String userName = userNameById.getOrDefault(row.getUserId(), "用户" + row.getUserId());
            String content = row.getContentText() == null ? "" : row.getContentText();
            long waitingSeconds = row.getCreatedAt() == null ? 0
                    : Math.max(0, Duration.between(row.getCreatedAt(), now).getSeconds());
            result.add(new ReviewItemView(
                    row.getLogPk(), row.getExplainId(), userName, kpId,
                    error == null ? null : error.getErrorType(), row.getModel(),
                    Objects.equals(row.getIsDegraded(), 1), content, sources,
                    row.getCreatedAt() == null ? null : DATE_TIME.format(row.getCreatedAt()),
                    question == null ? null : question.getQuestionText(),
                    question == null ? null : question.getQuestionType(),
                    parseOptions(question == null ? null : question.getOptionsJson()),
                    question == null ? null : question.getAnswerText(),
                    question == null ? null : question.getAnalysisText(),
                    error == null ? null : error.getStudentNote(),
                    waitingSeconds,
                    row.getReviewResult(),
                    row.getReviewedAt() == null ? "PENDING" : "REVIEWED"));
        }
        return result;
    }

    private List<ReviewItemView.SourceRef> buildSources(AiGenerationLog row,
                                                        Map<String, KnowledgePoint> kpById,
                                                        StudentErrorRecord error) {
        List<String> sourceKpIds = parseSourceKpIds(row.getSourceIds());
        if (sourceKpIds.isEmpty() && error != null && error.getKpId() != null
                && !error.getKpId().isBlank()) {
            sourceKpIds = List.of(error.getKpId());
        }
        List<ReviewItemView.SourceRef> sources = new ArrayList<>(sourceKpIds.size());
        for (String kpId : sourceKpIds) {
            KnowledgePoint kp = kpById.get(kpId);
            sources.add(new ReviewItemView.SourceRef(kpId,
                    kp == null ? null : kp.getTextbookPage(),
                    kp == null ? null : kp.getName()));
        }
        return sources;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSourceKpIds(String sourceIds) {
        if (sourceIds == null || sourceIds.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(sourceIds, List.class);
            return raw.stream().map(String::valueOf).filter(id -> !id.isBlank()).distinct().toList();
        } catch (Exception e) {
            log.warn("ai_generation_log.source_ids 解析失败，按空来源处理");
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ReviewItemView.OptionRef> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<ReviewItemView.OptionRef> options = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof Map<?, ?> map) {
                    Object key = map.get("key");
                    if (key == null) {
                        continue;
                    }
                    Object text = map.get("text");
                    options.add(new ReviewItemView.OptionRef(String.valueOf(key),
                            text == null ? "" : String.valueOf(text)));
                }
            }
            return options;
        } catch (Exception e) {
            log.warn("question_bank.options_json 解析失败，按空选项处理");
            return List.of();
        }
    }
}
