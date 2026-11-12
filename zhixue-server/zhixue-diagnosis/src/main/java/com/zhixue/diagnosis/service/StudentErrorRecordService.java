package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.ConfirmRequest;
import com.zhixue.diagnosis.dto.ErrorRecordCreate;
import com.zhixue.diagnosis.dto.ErrorRecordPageView;
import com.zhixue.diagnosis.dto.ErrorRecordView;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 错题记录服务：DRAFT 创建、CONFIRMED 确认、按 logId 读取。
 * <p>
 * 对外返回 {@link ErrorRecordView}，不直出实体；幂等由 userId+clientToken 唯一键与
 * 条件更新（status=DRAFT）兜底，并发重复提交不会产生双记录或双确认。
 */
@Service
public class StudentErrorRecordService {

    /** 统一时区口径：确认时间入库不能随 JVM 默认时区漂移（docs/13 B 组） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final String DRAFT = "DRAFT";
    private static final String CONFIRMED = "CONFIRMED";
    /** log_id 列 VARCHAR(20)：EL_ + 12 位时间 + 4 位十六进制 = 19 字符 */
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    /** 主键碰撞时最多换号重试 3 次，避免把主键冲突误判成 clientToken 幂等冲突 */
    private static final int MAX_INSERT_ATTEMPTS = 3;
    /** 契约 §1：日期时间统一 `yyyy-MM-dd HH:mm:ss` */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ERROR_TYPES = Set.of(
            "CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
            "CALCULATION_ERROR", "BOUNDARY_ERROR");

    private final StudentErrorRecordMapper mapper;
    private final AiGenerationLogMapper aiLogMapper;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;
    private final CourseService courseService;

    public StudentErrorRecordService(StudentErrorRecordMapper mapper,
                                     AiGenerationLogMapper aiLogMapper,
                                     KnowledgePointService knowledgePointService,
                                     QuestionBankService questionBankService,
                                     CourseService courseService) {
        this.mapper = mapper;
        this.aiLogMapper = aiLogMapper;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
        this.courseService = courseService;
    }

    /**
     * 创建 DRAFT 记录；同一 userId + clientToken 幂等返回首次结果。
     * <p>
     * 并发竞态由唯一键兜底：插入冲突时回查首次记录；不能包事务——MySQL REPEATABLE READ
     * 下首次查询建立的快照会看不到竞态方已提交的行。
     *
     * @param request 创建请求
     * @return 已存在或新建的记录视图
     */
    public ErrorRecordView create(ErrorRecordCreate request) {
        long userId = StpUtil.getLoginIdAsLong();
        StudentErrorRecord existing = findByClientToken(userId, request.clientToken());
        if (existing != null) {
            return toView(existing);
        }
        // 课程必须存在且启用：否则会落出一条永远挂不上任何课程的孤儿记录（探针 E05/E06）
        courseService.getActiveById(request.courseId());
        // DRAFT 允许 kpId 为空；一旦非空就必须存在，否则插入后再查知识点会留下无法重试的脏记录
        if (request.kpId() != null && !request.kpId().isBlank()) {
            knowledgePointService.getById(request.kpId());
        }
        // qId 决定错题本能否直接练习；传入时必须指向启用中的题目，避免落库后变成死入口
        if (request.qId() != null && !request.qId().isBlank()) {
            QuestionBank question = questionBankService.getById(request.qId());
            if (question == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + request.qId());
            }
            if (question.getIsActive() != null && question.getIsActive() != 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "题目已停用: " + request.qId());
            }
            if (question.getCourseId() != null
                    && !Objects.equals(question.getCourseId(), request.courseId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "qId 不属于当前课程");
            }
        }
        StudentErrorRecord record = new StudentErrorRecord();
        record.setLogId(nextLogId());
        record.setUserId(userId);
        record.setClientToken(request.clientToken());
        record.setQId(request.qId());
        record.setKpId(request.kpId());
        record.setImageUrl(request.imageUrl());
        record.setOcrText(request.ocrText());
        record.setCorrectedText(request.correctedText());
        record.setOcrConfidence(request.ocrConfidence());
        record.setErrorType(request.errorType());
        record.setStudentNote(request.studentNote());
        record.setStatus(DRAFT);
        for (int attempt = 1; ; attempt++) {
            try {
                mapper.insert(record);
                break;
            } catch (DuplicateKeyException e) {
                // 并发竞态：另一同 token 请求先插入；回查首次结果
                StudentErrorRecord winner = findByClientToken(userId, request.clientToken());
                if (winner != null) {
                    return toView(winner);
                }
                // 不是 clientToken 冲突，说明 log_id 主键碰撞；换号重试，超过上限才抛出
                if (attempt >= MAX_INSERT_ATTEMPTS) {
                    throw e;
                }
                record.setLogId(nextLogId());
            }
        }
        StudentErrorRecord saved = mapper.selectById(record.getLogId());
        return toView(saved == null ? record : saved);
    }

    /**
     * DRAFT → CONFIRMED；重复确认返回 40900。
     * <p>
     * 用 status=DRAFT 条件更新做 CAS：两个并发确认只有一个能更新成功，另一个回查后返回 40900。
     *
     * @param logId   错题记录编号
     * @param request 确认内容
     * @return 更新后的记录视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ErrorRecordView confirm(String logId, ConfirmRequest request) {
        StudentErrorRecord record = getById(logId);
        if (CONFIRMED.equals(record.getStatus())) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "错题记录已确认: " + logId);
        }
        if (!ERROR_TYPES.contains(request.getErrorType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "errorType 必须为契约五类错因之一");
        }
        knowledgePointService.getById(request.getKpId());

        LambdaUpdateWrapper<StudentErrorRecord> update = new LambdaUpdateWrapper<StudentErrorRecord>()
                .eq(StudentErrorRecord::getLogId, logId)
                .eq(StudentErrorRecord::getStatus, DRAFT)
                .set(StudentErrorRecord::getKpId, request.getKpId())
                .set(StudentErrorRecord::getErrorType, request.getErrorType())
                .set(StudentErrorRecord::getStatus, CONFIRMED)
                .set(StudentErrorRecord::getConfirmedAt, LocalDateTime.now(ZONE));
        if (request.getCorrectedText() != null) {
            update.set(StudentErrorRecord::getCorrectedText, request.getCorrectedText());
        }
        if (request.getStudentNote() != null) {
            update.set(StudentErrorRecord::getStudentNote, request.getStudentNote());
        }
        int rows = mapper.update(null, update);
        if (rows == 0) {
            // 条件未命中：要么并发方已确认，要么记录被删除
            StudentErrorRecord fresh = getById(logId);
            if (CONFIRMED.equals(fresh.getStatus())) {
                throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "错题记录已确认: " + logId);
            }
            // 契约 §8：条件未命中且仍为 DRAFT，按状态冲突返回 40900，便于前端刷新重试
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "确认状态冲突，请刷新后重试");
        }
        return toView(getById(logId));
    }

    /**
     * 错题详情（只允许本人访问）。
     *
     * @param logId 错题记录编号
     * @return 记录视图；不存在抛 40400，非本人抛 40300
     */
    public ErrorRecordView detail(String logId) {
        return toView(getById(logId));
    }

    /**
     * 当前登录学生的错题列表（契约 GET /api/error/records）。
     * <p>
     * 分页用 count + LIMIT/OFFSET 两查实现，不引 PaginationInnerInterceptor：
     * 单用户错题量为百级，两查成本可忽略，避免为单一端点引入分页插件依赖。
     *
     * @param page   页码，从 1 起；越界返回 40000（契约 minimum: 1）
     * @param size   每页条数；越界返回 40000（契约 minimum: 1 / maximum: 50）
     * @param kpId   可选过滤：知识点编号
     * @param status 可选过滤：DRAFT / CONFIRMED；其他值返回 40000
     * @return 分页视图，创建时间倒序
     */
    public ErrorRecordPageView page(int page, int size, String kpId, String status) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "page 必须从 1 起");
        }
        if (size < 1 || size > 50) {
            throw new BizException(ErrorCode.PARAM_ERROR, "size 必须在 1—50 之间");
        }
        if (status != null && !status.isBlank() && !DRAFT.equals(status) && !CONFIRMED.equals(status)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 只能为 DRAFT 或 CONFIRMED");
        }
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<StudentErrorRecord> query = new LambdaQueryWrapper<StudentErrorRecord>()
                .eq(StudentErrorRecord::getUserId, userId)
                .eq(kpId != null && !kpId.isBlank(), StudentErrorRecord::getKpId, kpId)
                .eq(status != null && !status.isBlank(), StudentErrorRecord::getStatus, status)
                .orderByDesc(StudentErrorRecord::getCreatedAt)
                .orderByDesc(StudentErrorRecord::getLogId);
        long total = mapper.selectCount(query);
        // count 与 list 共用 wrapper；列表走参数绑定 LIMIT/OFFSET，不再使用 .last() 拼接
        List<StudentErrorRecord> rows = mapper.selectPageByWrapper(query, size, (long) (page - 1) * size);
        Map<String, KnowledgePoint> kpById = knowledgePointService.findByIds(
                rows.stream().map(StudentErrorRecord::getKpId).toList());
        // 评阅回流（05 §4.6）：一次批量取本页错题的最新已评阅讲解，避免逐条 N+1
        Map<String, AiGenerationLog> reviewByLogId = latestReviews(userId,
                rows.stream().map(StudentErrorRecord::getLogId).toList());
        List<ErrorRecordView> records = rows.stream()
                .map(row -> toView(row, kpById.get(row.getKpId()), reviewByLogId.get(row.getLogId())))
                .toList();
        return new ErrorRecordPageView(page, size, total, records);
    }

    /**
     * 按编号读取记录（内部使用，供讲解/推荐取 CONFIRMED 记录）。
     *
     * @param logId 错题记录编号
     * @return 记录；不存在抛 40400，非本人抛 40300
     */
    public StudentErrorRecord getById(String logId) {
        StudentErrorRecord record = mapper.selectById(logId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "错题记录不存在: " + logId);
        }
        if (!record.getUserId().equals(StpUtil.getLoginIdAsLong())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该错题记录");
        }
        return record;
    }

    /**
     * 取已确认的错题记录，供讲解与推荐使用。
     *
     * @param logId 错题记录编号
     * @return 记录；不存在抛 40400，未确认抛 40900
     */
    public StudentErrorRecord getConfirmed(String logId) {
        StudentErrorRecord record = getById(logId);
        if (!CONFIRMED.equals(record.getStatus())) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "错题记录尚未确认: " + logId);
        }
        if (record.getKpId() == null || record.getKpId().isBlank()) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "错题记录缺少知识点: " + logId);
        }
        return record;
    }

    private StudentErrorRecord findByClientToken(long userId, String clientToken) {
        return mapper.selectOne(new LambdaQueryWrapper<StudentErrorRecord>()
                .eq(StudentErrorRecord::getUserId, userId)
                .eq(StudentErrorRecord::getClientToken, clientToken)
                .last("LIMIT 1"));
    }

    /** 实体 → 视图：补齐 kpName 与复核状态，隐藏 clientToken 等内部字段。 */
    private ErrorRecordView toView(StudentErrorRecord record) {
        KnowledgePoint kp = null;
        if (record.getKpId() != null && !record.getKpId().isBlank()) {
            kp = knowledgePointService.findById(record.getKpId());
        }
        AiGenerationLog review = latestReviews(record.getUserId(), List.of(record.getLogId()))
                .get(record.getLogId());
        return toView(record, kp, review);
    }

    /** 实体 → 视图；知识点与评阅由调用方批量预取，避免列表 N+1。 */
    private ErrorRecordView toView(StudentErrorRecord record, KnowledgePoint kp,
                                   AiGenerationLog review) {
        String kpName = kp == null ? null : kp.getName();
        return new ErrorRecordView(
                record.getLogId(),
                record.getUserId(),
                record.getQId(),
                record.getKpId(),
                kpName,
                record.getImageUrl(),
                record.getOcrText(),
                record.getCorrectedText(),
                record.getOcrConfidence(),
                record.getErrorType(),
                record.getStudentNote(),
                record.getStatus(),
                iso(record.getConfirmedAt()),
                iso(record.getCreatedAt()),
                review == null ? null : review.getReviewResult(),
                review == null ? null : review.getTeacherComment(),
                review == null ? null : iso(review.getReviewedAt()));
    }

    /**
     * 评阅回流聚合（05 §4.6）：错题的复核状态 = 该错题下最新一条已评阅讲解
     * （scene=RAG 且 reviewed_at 非空，按 reviewed_at 取最大）的结论。
     * 重新生成讲解不清退旧结论——退回意见在教师评阅新讲解前持续可见。
     * logIds 为空时直接返回空 Map，不发起 SQL（空 IN 是语法错误）。
     */
    private Map<String, AiGenerationLog> latestReviews(long userId, List<String> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return Map.of();
        }
        Map<String, AiGenerationLog> latest = new HashMap<>();
        for (AiGenerationLog row : aiLogMapper.selectReviewedByUserAndLogIds(userId, logIds)) {
            if (row == null || row.getLogId() == null) {
                continue;
            }
            latest.merge(row.getLogId(), row, (oldRow, newRow) ->
                    isNewerReview(newRow, oldRow) ? newRow : oldRow);
        }
        return latest;
    }

    /** reviewed_at 更新（或同刻但 log_pk 更大）的评阅为准，保证聚合确定性。 */
    private static final Comparator<AiGenerationLog> REVIEW_ORDER = Comparator
            .comparing(AiGenerationLog::getReviewedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AiGenerationLog::getLogPk, Comparator.nullsLast(Comparator.naturalOrder()));

    private static boolean isNewerReview(AiGenerationLog candidate, AiGenerationLog current) {
        return REVIEW_ORDER.compare(candidate, current) > 0;
    }

    private String iso(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }

    private String nextLogId() {
        return "EL_" + LocalDateTime.now(ZONE).format(ID_FORMAT) + "_"
                + String.format("%04x", (int) (System.nanoTime() & 0xffff));
    }
}
