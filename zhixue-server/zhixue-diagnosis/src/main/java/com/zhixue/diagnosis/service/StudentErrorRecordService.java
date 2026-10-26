package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.ConfirmRequest;
import com.zhixue.diagnosis.dto.ErrorRecordCreate;
import com.zhixue.diagnosis.dto.ErrorRecordView;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 错题记录服务：DRAFT 创建、CONFIRMED 确认、按 logId 读取。
 * <p>
 * 对外返回 {@link ErrorRecordView}，不直出实体；幂等由 userId+clientToken 唯一键与
 * 条件更新（status=DRAFT）兜底，并发重复提交不会产生双记录或双确认。
 */
@Service
public class StudentErrorRecordService {

    private static final String DRAFT = "DRAFT";
    private static final String CONFIRMED = "CONFIRMED";
    /** log_id 列 VARCHAR(20)：EL_ + 12 位时间 + 3 位十六进制 = 18 字符 */
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    /** 契约 §1：日期时间统一 `yyyy-MM-dd HH:mm:ss` */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ERROR_TYPES = Set.of(
            "CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
            "CALCULATION_ERROR", "BOUNDARY_ERROR");

    private final StudentErrorRecordMapper mapper;
    private final KnowledgePointService knowledgePointService;

    public StudentErrorRecordService(StudentErrorRecordMapper mapper,
                                     KnowledgePointService knowledgePointService) {
        this.mapper = mapper;
        this.knowledgePointService = knowledgePointService;
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
        StudentErrorRecord existing = findByClientToken(userId, request.getClientToken());
        if (existing != null) {
            return toView(existing);
        }
        StudentErrorRecord record = new StudentErrorRecord();
        record.setLogId(nextLogId());
        record.setUserId(userId);
        record.setClientToken(request.getClientToken());
        record.setQId(request.getQId());
        record.setKpId(request.getKpId());
        record.setImageUrl(request.getImageUrl());
        record.setOcrText(request.getOcrText());
        record.setCorrectedText(request.getCorrectedText());
        record.setOcrConfidence(request.getOcrConfidence());
        record.setErrorType(request.getErrorType());
        record.setStudentNote(request.getStudentNote());
        record.setStatus(DRAFT);
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发竞态：另一同 token 请求先插入；回查首次结果
            StudentErrorRecord winner = findByClientToken(userId, request.getClientToken());
            if (winner != null) {
                return toView(winner);
            }
            throw e;
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
    @Transactional
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
                .set(StudentErrorRecord::getConfirmedAt, LocalDateTime.now());
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

    /** 实体 → 视图：补齐 kpName，隐藏 clientToken 等内部字段。 */
    private ErrorRecordView toView(StudentErrorRecord record) {
        String kpName = null;
        if (record.getKpId() != null && !record.getKpId().isBlank()) {
            KnowledgePoint kp = knowledgePointService.getById(record.getKpId());
            if (kp != null) {
                kpName = kp.getName();
            }
        }
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
                iso(record.getCreatedAt()));
    }

    private String iso(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }

    private String nextLogId() {
        return "EL_" + LocalDateTime.now().format(ID_FORMAT) + "_"
                + Integer.toHexString((int) (System.nanoTime() & 0xfff));
    }
}
