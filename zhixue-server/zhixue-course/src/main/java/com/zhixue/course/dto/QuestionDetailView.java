package com.zhixue.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 题目详情视图（契约 openapi QuestionDetail）。
 * 明确不包含 answerText/analysisText：学生先独立作答，再调用答案接口。
 */
public record QuestionDetailView(String qId, Long courseId, String kpId, String kpName,
                                 @Schema(allowableValues = {"单选", "多选", "填空", "简答", "编程"})
                                 String questionType, String questionText,
                                 List<QuestionOption> options, Integer difficulty) {

    public record QuestionOption(String key, String text) {
    }
}
