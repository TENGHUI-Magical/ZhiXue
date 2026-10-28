package com.zhixue.course.dto;

/**
 * 参考答案视图（契约 openapi QuestionAnswer）。
 * 仅在学生显式点击“查看答案”后返回，列表/详情接口不下发。
 */
public record QuestionAnswerView(String qId, String answerText, String analysisText) {
}
