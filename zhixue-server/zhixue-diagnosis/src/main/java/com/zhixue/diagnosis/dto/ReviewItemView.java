package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 教师评阅队列项（契约 openapi ReviewItem）。
 *
 * <p>2026-10-31 C3：补评阅证据——题干/题型/选项/参考答案/解析/学生笔记/等待时长/评阅状态。
 * 说明：当前数据模型没有单独的“学生原答”字段，故不虚构；学生侧证据以学生笔记与错因呈现。</p>
 */
public record ReviewItemView(Long logPk, String explainId, String userName, String kpId,
                             String errorType, String model, boolean degraded, String content,
                             List<SourceRef> sources, String createdAt,
                             String questionText, String questionType, List<OptionRef> options,
                             String correctAnswer, String analysisText, String studentNote,
                             long waitingSeconds,
                             @Schema(allowableValues = {"PASS", "RETURN"}) String reviewResult,
                             @Schema(allowableValues = {"PENDING", "REVIEWED"}) String reviewStatus,
                             Integer teacherScore,
                             @Schema(nullable = true, description = "教师评语；未评阅为 null") String teacherComment,
                             @Schema(nullable = true, description = "评阅时间（yyyy-MM-dd HH:mm:ss）；未评阅为 null") String reviewedAt) {

    public record SourceRef(String kpId, String textbookPage, String title) {
    }

    public record OptionRef(String key, String text) {
    }
}
