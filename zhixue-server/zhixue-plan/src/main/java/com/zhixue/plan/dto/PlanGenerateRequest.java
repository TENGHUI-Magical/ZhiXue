package com.zhixue.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 生成一周计划请求（契约 openapi /api/plans/generate）。
 */
public record PlanGenerateRequest(
        @NotNull(message = "courseId 不能为空") Long courseId,

        @Schema(description = "自然周起始日，必须为周一", example = "2026-11-02")
        @NotNull(message = "weekStart 不能为空") LocalDate weekStart,

        @Schema(minimum = "3", maximum = "5", defaultValue = "3")
        @Min(value = 3, message = "dailyQuestionCount 最小 3")
        @Max(value = 5, message = "dailyQuestionCount 最大 5")
        Integer dailyQuestionCount) {
}
