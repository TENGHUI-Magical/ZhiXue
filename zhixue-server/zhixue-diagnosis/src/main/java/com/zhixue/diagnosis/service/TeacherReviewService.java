package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.common.util.CsvWriter;
import com.zhixue.common.util.NameMasker;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师评阅：待评阅队列 + 提交评阅。
 * 待评阅口径：scene=RAG 且 reviewed_at IS NULL；重复评阅 40900，非教师 40300，
 * 提交评阅超出登录教师可管理课程范围 40300（读写同口径 scopedCourseIds）；
 * 归属不明的记录不归属任何教师课程，只有创建者本人可读/可评阅（P1-6）。
 *
 * <p>队列补评阅证据（题干/题型/选项/参考答案/解析/学生笔记/等待时长），
 * 支持 status / kpId / from / to 筛选。</p>
 */
@Service
public class TeacherReviewService {

    private static final Logger log = LoggerFactory.getLogger(TeacherReviewService.class);
    /** 统一时区口径：入库时间与“距上次作答”计算不能随 JVM 默认时区漂移（docs/13 B 组） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiGenerationLogMapper logMapper;
    private final StudentErrorRecordMapper errorRecordMapper;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;
    private final UserQueryService userQueryService;
    private final ObjectMapper objectMapper;
    private final CourseService courseService;

    public TeacherReviewService(AiGenerationLogMapper logMapper,
                                StudentErrorRecordMapper errorRecordMapper,
                                KnowledgePointService knowledgePointService,
                                QuestionBankService questionBankService,
                                UserQueryService userQueryService,
                                ObjectMapper objectMapper,
                                CourseService courseService) {
        this.logMapper = logMapper;
        this.errorRecordMapper = errorRecordMapper;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
        this.userQueryService = userQueryService;
        this.objectMapper = objectMapper;
        this.courseService = courseService;
    }

    /**
     * 评阅队列，按 createdAt 倒序。课程范围 = 登录教师可管理课程（与导出同一口径）：
     * 明确属于其他教师课程的记录不可见；归属不明的记录也不可见（无教师可归属，P1-6）。
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
        String st = status == null || status.isBlank() ? "PENDING"
                : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "REVIEWED", "ALL").contains(st)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 仅允许 PENDING/REVIEWED/ALL");
        }
        // null=全部；true=已评阅；false=待评阅
        Boolean reviewed = switch (st) {
            case "REVIEWED" -> Boolean.TRUE;
            case "ALL" -> null;
            default -> Boolean.FALSE;
        };
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.atTime(23, 59, 59);
        String kp = kpId == null || kpId.isBlank() ? null : kpId.trim();
        List<Long> courseIds = scopedCourseIds();
        long total = logMapper.countForReview(reviewed, kp, fromTime, toTime, courseIds);
        // 页码从 1 起；page 由前端传入，超大页码时 (page - 1) * size 在 int 上会溢出成负数，
        // 直接拼进 SQL OFFSET 会报错/越界。用 long 计算并把越过总数的页直接返回空页。
        long offset = (long) (page - 1) * size;
        List<AiGenerationLog> rows = total == 0 || offset >= total || offset > Integer.MAX_VALUE
                ? List.of()
                : logMapper.selectForReview(reviewed, kp, fromTime, toTime, courseIds,
                size, (int) offset);
        return new ReviewPageView(page, size, total, toViews(rows));
    }

    /** 兼容旧调用：默认待评阅、无筛选。 */
    public ReviewPageView page(int page, int size) {
        return page(page, size, "PENDING", null, null, null);
    }

    /**
     * 登录教师可管理课程范围（评阅队列与留痕导出共用，勿另造口径）：
     * 无课程教师传哨兵 -1——SQL 的 NOT IN (-1) 恒真，所有能定位课程的记录被排除；
     * 归属不明的记录再由 Mapper 的“可归属”守卫（{@code q.course_id IS NOT NULL}）一并排除，
     * 因此无课程教师看不到任何留痕（P1-6）。
     */
    private List<Long> scopedCourseIds() {
        List<Long> ids = courseService.listActiveCourseIdsByTeacher(StpUtil.getLoginIdAsLong());
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private static final List<String> REVIEW_CSV_HEADER = List.of(
            "留痕ID", "讲解ID", "学生（脱敏）", "知识点", "错因", "题干", "模型",
            "是否降级", "创建时间", "评阅结果", "教师评分", "教师评语", "评阅时间");

    /**
     * 导出行数上限：导出结果常驻内存为投影行 + 关联映射（流式写出，不再有实体/视图/整份字节副本）。
     * 超限拒绝并引导缩小日期范围——不做截断：静默少行会让老师以为数据丢了，
     * 与“证据列为空比整行消失诚实”（docs/01 §7.2）同一原则。
     * 5 万 = 真实学期规模（单课程数百至数千行）的 10—50 倍余量；
     * 实测过程与原始输出见 spark-output/review/export-perf-2026-11-03.md。
     */
    static final int MAX_EXPORT_ROWS = 50_000;

    /** toViews 批量回查错题的分块大小：避免无界 IN（红线），500 为参数绑定安全值。 */
    private static final int ERROR_LOOKUP_CHUNK = 500;

    private static final Map<String, String> ERROR_TYPE_ZH = Map.of(
            "CONCEPT_CONFUSION", "概念混淆", "METHOD_MISSING", "方法缺失",
            "STEP_OMISSION", "步骤遗漏", "CALCULATION_ERROR", "计算失误",
            "BOUNDARY_ERROR", "边界条件", "UNCLASSIFIED", "未归类");

    /**
     * 评阅留痕 CSV 导出（学期材料）：筛选条件与队列完全一致（含课程范围），导出完整筛选结果而非当前分页。
     * 权限与队列一致（TEACHER + 登录教师可管理课程范围）；学生姓名脱敏（NameMasker，与班级学情同口径）；
     * UTF-8 + BOM、中文表头；不含讲解正文（正文在系统内按讲解 ID 可查）。
     *
     * <p>只读事务：COUNT 与取数依赖同一快照，不依赖数据库默认隔离级别；显式声明
     * REPEATABLE_READ，保证"数过上限却取到更多行"（TOCTOU）不会因部署环境默认值不同而失效。</p>
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StreamingResponseBody exportCsv(String status, String kpId, LocalDate from, LocalDate to) {
        StpUtil.checkRole("TEACHER");
        String st = status == null || status.isBlank() ? "PENDING"
                : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "REVIEWED", "ALL").contains(st)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 仅允许 PENDING/REVIEWED/ALL");
        }
        Boolean reviewed = switch (st) {
            case "REVIEWED" -> Boolean.TRUE;
            case "ALL" -> null;
            default -> Boolean.FALSE;
        };
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.atTime(23, 59, 59);
        String kp = kpId == null || kpId.isBlank() ? null : kpId.trim();
        List<Long> courseIds = scopedCourseIds();
        // 先 COUNT 再取数：超限直接拒绝（不截断），避免全量进内存后才失败
        long total = logMapper.countForReview(reviewed, kp, fromTime, toTime, courseIds);
        if (total > MAX_EXPORT_ROWS) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "导出数据过大（" + total + " 行，上限 " + MAX_EXPORT_ROWS
                            + "），请设置导出的起止日期缩小筛选范围后重试");
        }
        // 投影查询不含 content_text（见 Mapper 注释）：COUNT 与取数在同一个 REPEATABLE_READ 快照内。
        List<AiGenerationLog> rows = logMapper.selectForReviewExport(reviewed, kp, fromTime, toTime,
                courseIds);
        Map<String, StudentErrorRecord> errorById = loadErrors(rows);
        Set<String> qIds = errorById.values().stream()
                .map(StudentErrorRecord::getQId)
                .filter(Objects::nonNull).filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, QuestionBank> questionById = qIds.isEmpty() ? Map.of()
                : questionBankService.findByIds(qIds);
        Set<Long> userIds = rows.stream().map(AiGenerationLog::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> userNameById = userQueryService.findDisplayNames(userIds);

        // 响应在事务结束后才写出：只保留投影行与关联映射（无正文、无 View/行列表/byte[] 副本），
        // 逐行转义写 OutputStream，50,000 行的内存占用不再随正文长度线性膨胀。
        return out -> {
            CsvWriter.writeHeader(out, REVIEW_CSV_HEADER);
            for (AiGenerationLog row : rows) {
                CsvWriter.writeRow(out, exportLine(row, errorById, questionById, userNameById));
            }
        };
    }

    /** 导出单行：只用 CSV 需要的列与关联，不构造含正文/选项/解析的 ReviewItemView。 */
    private List<String> exportLine(AiGenerationLog row,
                                    Map<String, StudentErrorRecord> errorById,
                                    Map<String, QuestionBank> questionById,
                                    Map<Long, String> userNameById) {
        StudentErrorRecord error = errorById.get(row.getLogId());
        QuestionBank question = error == null || error.getQId() == null
                ? null : questionById.get(error.getQId());
        List<String> sourceKpIds = parseSourceKpIds(row.getSourceIds());
        String kpId = error != null && error.getKpId() != null ? error.getKpId()
                : sourceKpIds.isEmpty() ? null : sourceKpIds.get(0);
        String userName = userNameById.getOrDefault(row.getUserId(), "用户" + row.getUserId());
        return List.of(
                String.valueOf(row.getLogPk()),
                row.getExplainId() == null ? "" : row.getExplainId(),
                NameMasker.mask(userName),
                kpId == null ? "" : kpId,
                error == null || error.getErrorType() == null ? ""
                        : ERROR_TYPE_ZH.getOrDefault(error.getErrorType(), error.getErrorType()),
                question == null || question.getQuestionText() == null ? "" : question.getQuestionText(),
                row.getModel() == null ? "" : row.getModel(),
                Objects.equals(row.getIsDegraded(), 1) ? "是" : "否",
                row.getCreatedAt() == null ? "" : DATE_TIME.format(row.getCreatedAt()),
                "PASS".equals(row.getReviewResult()) ? "通过"
                        : "RETURN".equals(row.getReviewResult()) ? "退回" : "",
                row.getTeacherScore() == null ? "" : String.valueOf(row.getTeacherScore()),
                row.getTeacherComment() == null ? "" : row.getTeacherComment(),
                row.getReviewedAt() == null ? "" : DATE_TIME.format(row.getReviewedAt()));
    }

    /**
     * 提交评阅：课程范围与读侧队列/导出同一口径（{@link #scopedCourseIds()}，勿另造口径）；
     * reviewed_at IS NULL 才允许写；重复/并发抢先返回 40900。
     */
    @Transactional
    public void submit(long logPk, ReviewSubmitRequest request) {
        StpUtil.checkRole("TEACHER");
        AiGenerationLog row = logMapper.selectById(logPk);
        if (row == null || !"RAG".equals(row.getScene())) {
            throw new BizException(ErrorCode.NOT_FOUND, "评阅记录不存在: " + logPk);
        }
        // 写侧课程范围校验（2026-11-03，P1-6 收紧）：可归属记录可定位到登录教师可管理课程之外 → 40300；
        // 归属不明（无错题关联/题库查不到课程）只有创建者本人可见，其他教师一律 40300。
        // 先于 reviewed_at 检查，避免向范围外教师泄漏评阅状态。
        long loginId = StpUtil.getLoginIdAsLong();
        List<Long> linkedCourseIds = logMapper.selectLinkedCourseIds(row.getLogId());
        if (linkedCourseIds.isEmpty()) {
            if (!Objects.equals(row.getUserId(), loginId)) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "不能评阅归属不明的记录: " + logPk);
            }
        } else {
            List<Long> scope = scopedCourseIds();
            for (Long courseId : linkedCourseIds) {
                if (!scope.contains(courseId)) {
                    throw new BizException(ErrorCode.FORBIDDEN,
                            "不能评阅自己可管理课程之外的记录: " + logPk);
                }
            }
        }
        if (row.getReviewedAt() != null) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该讲解已评阅: " + logPk);
        }
        String result = request.result() == null || request.result().isBlank()
                ? "PASS" : request.result().trim().toUpperCase(Locale.ROOT);
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
                .set(AiGenerationLog::getReviewedAt, LocalDateTime.now(ZONE)));
        if (rows == 0) {
            AiGenerationLog fresh = logMapper.selectById(logPk);
            if (fresh == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "评阅记录不存在: " + logPk);
            }
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该讲解已评阅: " + logPk);
        }
    }

    /** 按 logIds 分块回查错题记录（每块 500）：导出可达数万行，一次性 IN 全部 logId 是无界查询（红线）。 */
    private Map<String, StudentErrorRecord> loadErrors(List<AiGenerationLog> rows) {
        Set<String> logIds = rows.stream().map(AiGenerationLog::getLogId)
                .filter(Objects::nonNull).filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, StudentErrorRecord> errorById = new HashMap<>();
        List<String> idList = new ArrayList<>(logIds);
        for (int i = 0; i < idList.size(); i += ERROR_LOOKUP_CHUNK) {
            List<String> chunk = idList.subList(i, Math.min(idList.size(), i + ERROR_LOOKUP_CHUNK));
            for (StudentErrorRecord record : errorRecordMapper.selectBatchIds(chunk)) {
                errorById.putIfAbsent(record.getLogId(), record);
            }
        }
        return errorById;
    }

    private List<ReviewItemView> toViews(List<AiGenerationLog> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, StudentErrorRecord> errorById = loadErrors(rows);

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

        LocalDateTime now = LocalDateTime.now(ZONE);
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
                    row.getReviewedAt() == null ? "PENDING" : "REVIEWED",
                    row.getTeacherScore(),
                    row.getTeacherComment(),
                    row.getReviewedAt() == null ? null : DATE_TIME.format(row.getReviewedAt())));
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
