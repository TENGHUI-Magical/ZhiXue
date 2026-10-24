package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.ConfirmRequest;
import com.zhixue.diagnosis.dto.ErrorRecordCreate;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 错题记录服务：DRAFT 创建、CONFIRMED 确认、按 logId 读取。
 */
@Service
public class StudentErrorRecordService {

    private static final String DRAFT = "DRAFT";
    private static final String CONFIRMED = "CONFIRMED";
    /** log_id 列 VARCHAR(20)：EL_ + 12 位时间 + 3 位十六进制 = 18 字符 */
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmss");
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
     *
     * @param request 创建请求
     * @return 已存在或新建的记录
     */
    public StudentErrorRecord create(ErrorRecordCreate request) {
        long userId = StpUtil.getLoginIdAsLong();
        List<StudentErrorRecord> existing = mapper.selectList(
                new LambdaQueryWrapper<StudentErrorRecord>()
                        .eq(StudentErrorRecord::getUserId, userId)
                        .eq(StudentErrorRecord::getClientToken, request.getClientToken())
                        .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            return existing.get(0);
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
        mapper.insert(record);
        return record;
    }

    /**
     * DRAFT → CONFIRMED；重复确认返回 40900。
     *
     * @param logId   错题记录编号
     * @param request 确认内容
     * @return 更新后的记录
     */
    public StudentErrorRecord confirm(String logId, ConfirmRequest request) {
        StudentErrorRecord record = getById(logId);
        if (CONFIRMED.equals(record.getStatus())) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "错题记录已确认: " + logId);
        }
        if (!ERROR_TYPES.contains(request.getErrorType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "errorType 必须为契约五类错因之一");
        }
        knowledgePointService.getById(request.getKpId());

        record.setKpId(request.getKpId());
        record.setErrorType(request.getErrorType());
        if (request.getCorrectedText() != null) {
            record.setCorrectedText(request.getCorrectedText());
        }
        if (request.getStudentNote() != null) {
            record.setStudentNote(request.getStudentNote());
        }
        record.setStatus(CONFIRMED);
        record.setConfirmedAt(LocalDateTime.now());
        mapper.updateById(record);
        return record;
    }

    /**
     * 按编号读取记录（详情页用）；只允许本人访问。
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

    private String nextLogId() {
        return "EL_" + LocalDateTime.now().format(ID_FORMAT) + "_"
                + Integer.toHexString((int) (System.nanoTime() & 0xfff));
    }
}
