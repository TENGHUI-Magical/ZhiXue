package com.zhixue.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习计划视图（契约 openapi Plan）。
 */
public record PlanView(Long planId, LocalDate planDate, Integer planCount, Integer doneCount,
                       @Schema(allowableValues = {"PENDING", "DONE", "OVERDUE"}) String status,
                       List<PlanItemView> items) {

    public record PlanItemView(Long itemId, String qId, String kpId, Integer sortNo, boolean isDone) {
    }
}
