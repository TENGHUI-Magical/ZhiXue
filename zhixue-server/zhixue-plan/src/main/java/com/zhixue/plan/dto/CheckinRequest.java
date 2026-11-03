package com.zhixue.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 计划打卡请求（契约 openapi /api/plans/{planId}/checkin）。
 *
 * <p>区分"已作答"与"仅记录进度"。
 * answered=true 时按真实作答写入 practice_record（真实 is_correct）；
 * answered=false 时仅推进计划进度，不写 practice_record、不计正确率。</p>
 */
public record CheckinRequest(
        @NotBlank(message = "qId 不能为空") String qId,

        @NotBlank(message = "clientToken 不能为空")
        @Size(max = 64, message = "clientToken 最长 64 字") String clientToken,

        @NotNull(message = "answered 不能为空") Boolean answered,

        @Size(max = 20000, message = "myAnswer 最长 20000 字") String myAnswer,

        Boolean isCorrect,

        @Min(value = 0, message = "costSeconds 不能为负")
        @Max(value = 86400, message = "costSeconds 不能超过 86400") Integer costSeconds,

        @Schema(allowableValues = {"CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
                "CALCULATION_ERROR", "BOUNDARY_ERROR", "UNCLASSIFIED"}, nullable = true,
                description = "主观题自评错因；未选或客观题由服务端推断，无法归类落 UNCLASSIFIED")
        @Pattern(regexp = "CONCEPT_CONFUSION|METHOD_MISSING|STEP_OMISSION"
                + "|CALCULATION_ERROR|BOUNDARY_ERROR|UNCLASSIFIED",
                message = "errorType 仅允许 CONCEPT_CONFUSION/METHOD_MISSING"
                + "/STEP_OMISSION/CALCULATION_ERROR/BOUNDARY_ERROR/UNCLASSIFIED")
        String errorType,

        @Size(max = 200, message = "note 最长 200 字") String note) {
}
