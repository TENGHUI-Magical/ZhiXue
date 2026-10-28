package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交作答请求（契约 openapi PracticeCreate）。
 * 计划内作答不走本接口，必须走 /api/plans/{planId}/checkin（source=PLAN）。
 */
public record PracticeCreateRequest(
        @NotBlank(message = "qId 不能为空") String qId,

        @NotBlank(message = "kpId 不能为空") String kpId,

        @NotNull(message = "isCorrect 不能为空") Boolean isCorrect,

        @Size(max = 20000, message = "myAnswer 最长 20000 字") String myAnswer,

        @Schema(allowableValues = {"CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
                "CALCULATION_ERROR", "BOUNDARY_ERROR"}, nullable = true)
        String errorType,

        @Min(value = 0, message = "costSeconds 不能为负")
        @Max(value = 86400, message = "costSeconds 不能超过 86400") Integer costSeconds,

        @Schema(allowableValues = {"RECOMMEND", "SELF"}, defaultValue = "SELF")
        String source,

        Long recommendationId,

        @NotBlank(message = "clientToken 不能为空")
        @Size(max = 64, message = "clientToken 最长 64 字") String clientToken) {
}
