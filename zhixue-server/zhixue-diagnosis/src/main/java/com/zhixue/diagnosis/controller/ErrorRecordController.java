package com.zhixue.diagnosis.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.ConfirmRequest;
import com.zhixue.diagnosis.dto.ErrorRecordCreate;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.service.StudentErrorRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题记录接口（契约 V2 第 7 节 error 组）。
 */
@RestController
@RequestMapping("/api/error/records")
public class ErrorRecordController {

    private final StudentErrorRecordService recordService;

    public ErrorRecordController(StudentErrorRecordService recordService) {
        this.recordService = recordService;
    }

    /** 创建 DRAFT 记录；clientToken 幂等 */
    @PostMapping
    public ApiResponse<StudentErrorRecord> create(@Valid @RequestBody ErrorRecordCreate request) {
        return ApiResponse.ok(recordService.create(request));
    }

    /** 错题详情 */
    @GetMapping("/{logId}")
    public ApiResponse<StudentErrorRecord> detail(@PathVariable String logId) {
        return ApiResponse.ok(recordService.getById(logId));
    }

    /** DRAFT → CONFIRMED；重复确认返回 40900 */
    @PutMapping("/{logId}/confirm")
    public ApiResponse<StudentErrorRecord> confirm(@PathVariable String logId,
                                                   @Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.ok(recordService.confirm(logId, request));
    }
}
