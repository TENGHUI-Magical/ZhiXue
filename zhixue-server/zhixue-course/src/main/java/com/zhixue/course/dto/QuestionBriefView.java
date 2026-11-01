package com.zhixue.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 题目简要视图（契约 openapi QuestionBrief）。
 * 供按知识点列题/错题关联选择使用；不包含答案与解析。
 */
public record QuestionBriefView(
        String qId,
        String primaryKpId,
        @Schema(allowableValues = {"单选", "多选", "填空", "简答", "编程"})
        String questionType,
        String questionText,
        Integer difficulty) {
}
