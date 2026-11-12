package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建错题记录（契约 V2.0.8：POST /api/error/records）。
 * 允许字段不全，先落 DRAFT；确认后进入 CONFIRMED。
 * <p>
 * 使用 record 保证 qId 组件名原样保留：POJO getter 内省会让 springdoc 额外生成 qid，
 * 造成生成规范与冻结契约字段名漂移。
 * <p>
 * 长度约束与 {@code docs/openapi.yaml} 2.0.8 保持一致；contract-first 下契约是源，代码 @Size 只做运行时保护。
 */
public record ErrorRecordCreate(
        @NotNull(message = "courseId 不能为空")
        @Positive(message = "courseId 必须为正数") Long courseId,

        /** 客户端幂等键，与 userId 组成唯一键 */
        @NotBlank(message = "clientToken 不能为空")
        @Size(max = 64, message = "clientToken 最长 64 字") String clientToken,

        /** P1-6：与 student_error_record.q_id VARCHAR(20) 对齐 */
        @Size(max = 20, message = "qId 最长 20 字") String qId,
        /** P1-6：与 student_error_record.kp_id VARCHAR(20) 对齐 */
        @Size(max = 20, message = "kpId 最长 20 字") String kpId,
        /** P1-6：与 student_error_record.image_url VARCHAR(300) 对齐 */
        @Size(max = 300, message = "imageUrl 最长 300 字") String imageUrl,
        @Size(max = 8000) String ocrText,
        @Size(max = 3000) String correctedText,

        /** OCR 置信度，契约 2.2.9 定义 [0, 1]；越界必须在入口拒绝，不能落库 */
        @DecimalMin(value = "0", message = "ocrConfidence 不能小于 0")
        @DecimalMax(value = "1", message = "ocrConfidence 不能大于 1")
        BigDecimal ocrConfidence,

        @Schema(allowableValues = {"CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
                "CALCULATION_ERROR", "BOUNDARY_ERROR"}, nullable = true)
        @Pattern(regexp = "CONCEPT_CONFUSION|METHOD_MISSING|STEP_OMISSION"
                + "|CALCULATION_ERROR|BOUNDARY_ERROR",
                message = "errorType 必须是契约枚举之一")
        String errorType,

        @Size(max = 500) String studentNote) {
}