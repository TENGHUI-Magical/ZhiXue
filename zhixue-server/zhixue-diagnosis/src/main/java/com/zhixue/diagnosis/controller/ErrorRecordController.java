package com.zhixue.diagnosis.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.ConfirmRequest;
import com.zhixue.diagnosis.dto.ErrorRecordCreate;
import com.zhixue.diagnosis.dto.ErrorRecordPageView;
import com.zhixue.diagnosis.dto.ErrorRecordView;
import com.zhixue.diagnosis.service.StudentErrorRecordService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题记录接口（契约 V2 第 7 节 error 组）。
 * 对外统一返回 {@link ErrorRecordView}，不再直出实体。
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
    public ApiResponse<ErrorRecordView> create(@Valid @RequestBody ErrorRecordCreate request) {
        return ApiResponse.ok(recordService.create(request));
    }

    /** 当前学生的错题列表（分页，创建时间倒序） */
    @GetMapping
    public ApiResponse<ErrorRecordPageView> page(
            @Parameter(schema = @Schema(type = "integer", minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1") int page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50",
                    defaultValue = "10"))
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String kpId,
            @Parameter(schema = @Schema(allowableValues = {"DRAFT", "CONFIRMED"}))
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(recordService.page(page, size, kpId, status));
    }

    /** 错题详情 */
    @GetMapping("/{logId}")
    public ApiResponse<ErrorRecordView> detail(@PathVariable String logId) {
        return ApiResponse.ok(recordService.detail(logId));
    }

    /** DRAFT → CONFIRMED；重复确认返回 40900 */
    @PutMapping("/{logId}/confirm")
    public ApiResponse<ErrorRecordView> confirm(@PathVariable String logId,
                                                @Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.ok(recordService.confirm(logId, request));
    }
}
