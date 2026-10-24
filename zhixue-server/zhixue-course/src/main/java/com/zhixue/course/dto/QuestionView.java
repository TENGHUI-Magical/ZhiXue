package com.zhixue.course.dto;

import java.util.List;

/**
 * 学生端题目视图：只返回作答所需字段，答案与解析不随题下发。
 *
 * @param qId           题目编号
 * @param primaryKpId   主知识点
 * @param questionType  单选/多选/填空/简答/编程
 * @param questionText  题干
 * @param options       选项；非选择题为空数组
 * @param difficulty    1 简单，2 中等，3 困难
 * @param source        来源：教材/习题册/自编
 */
public record QuestionView(String qId, String primaryKpId, String questionType, String questionText,
                           List<Option> options, Integer difficulty, String source) {

    /** 选项：key 为 A/B/C/D，text 为选项正文 */
    public record Option(String key, String text) {
    }
}
