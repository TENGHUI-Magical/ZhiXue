package com.zhixue.diagnosis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交教师评阅（契约 POST /api/teacher/reviews/{logPk}）。
 * 契约要求 score/comment 必填；comment 可为空串，但最长 500 字。
 */
public record ReviewSubmitRequest(
        @NotNull(message = "score 不能为空")
        @Min(value = 1, message = "score 最小 1")
        @Max(value = 5, message = "score 最大 5") Integer score,

        @NotNull(message = "comment 不能为空")
        @Size(max = 500, message = "comment 最长 500 字") String comment) {
}
