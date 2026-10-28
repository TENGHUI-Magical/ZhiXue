package com.zhixue.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 计划打卡请求（契约 openapi /api/plans/{planId}/checkin）。
 */
public record CheckinRequest(
        @NotBlank(message = "qId 不能为空") String qId,

        @NotBlank(message = "clientToken 不能为空")
        @Size(max = 64, message = "clientToken 最长 64 字") String clientToken,

        @Size(max = 200, message = "note 最长 200 字") String note) {
}
