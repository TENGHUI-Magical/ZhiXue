package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交作答请求（契约 openapi PracticeCreate）。
 * 计划内作答不走本接口，必须走 /api/plans/{planId}/checkin（source=PLAN）。
 *
 * <p>客观题（单选/多选）由服务端按题目 answerText 判分，isCorrect 可传但会被忽略；
 * 主观题（填空/简答/编程等）保留自评，此时 isCorrect 必填。</p>
 */
public record PracticeCreateRequest(
        @NotBlank(message = "qId 不能为空") String qId,

        @NotBlank(message = "kpId 不能为空") String kpId,

        @Schema(nullable = true, description = "兼容旧客户端；客观题由服务端判分并忽略此值，主观题必填")
        Boolean isCorrect,

        @Size(max = 20000, message = "myAnswer 最长 20000 字") String myAnswer,

        @Schema(allowableValues = {"CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
                "CALCULATION_ERROR", "BOUNDARY_ERROR", "UNCLASSIFIED"}, nullable = true,
                description = "主观题自评错因；未选或客观题由服务端推断，无法归类落 UNCLASSIFIED")
        String errorType,

        @Min(value = 0, message = "costSeconds 不能为负")
        @Max(value = 86400, message = "costSeconds 不能超过 86400") Integer costSeconds,

        @Schema(nullable = true, description = "begin 打点返回的作答开始令牌；携带时服务端以"
                + "（提交时刻 - begin 时刻）计时并忽略客户端 costSeconds；未携带时 costSeconds 落 null")
        @Size(max = 64, message = "startToken 最长 64 字") String startToken,

        @Schema(allowableValues = {"RECOMMEND", "SELF"}, defaultValue = "SELF")
        String source,

        Long recommendationId,

        @NotBlank(message = "clientToken 不能为空")
        @Size(max = 64, message = "clientToken 最长 64 字") String clientToken) {
}
