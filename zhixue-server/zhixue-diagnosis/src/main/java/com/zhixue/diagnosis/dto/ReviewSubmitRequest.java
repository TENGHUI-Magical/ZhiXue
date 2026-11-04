package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交教师评阅（契约 POST /api/teacher/reviews/{logPk}）。
 * 契约要求 score/comment 必填；comment 可为空串，但最长 500 字。
 * result 可选：PASS（默认）/ RETURN（退回，必须填写评语）。
 * PASS 需证据完整（关联错题可解析出题干与参考答案），缺证据仅允许 RETURN，
 * 服务端校验返回 40000——与前端 C3 规则对齐，堵住直连 API 绕过。
 */
public record ReviewSubmitRequest(
        @NotNull(message = "score 不能为空")
        @Min(value = 1, message = "score 最小 1")
        @Max(value = 5, message = "score 最大 5") Integer score,

        @NotNull(message = "comment 不能为空")
        @Size(max = 500, message = "comment 最长 500 字") String comment,

        @Schema(allowableValues = {"PASS", "RETURN"}, defaultValue = "PASS",
                description = "评阅结论；缺省 PASS。PASS 需题干+参考答案证据，缺证据仅允许 RETURN（40000）")
        String result) {

    /** 兼容旧调用：缺省 PASS。 */
    public ReviewSubmitRequest(Integer score, String comment) {
        this(score, comment, "PASS");
    }
}
