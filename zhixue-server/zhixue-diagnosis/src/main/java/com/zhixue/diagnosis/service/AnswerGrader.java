package com.zhixue.diagnosis.service;

import com.zhixue.course.entity.QuestionBank;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 客观题服务端判分：单选/多选按 answerText 比对，不信任客户端 isCorrect。
 * 填空/简答/编程等无法自动判分的题型保留自评，由调用方读取 SELF 模式并校验自评值。
 */
public final class AnswerGrader {

    public static final String AUTO = "AUTO";
    public static final String SELF = "SELF";

    private static final Set<String> OBJECTIVE_TYPES = Set.of("单选", "多选", "判断", "判断题");

    private AnswerGrader() {
    }

    /** 判分结果：mode=AUTO/SELF；correct 在 SELF 模式下等于客户端自评。 */
    public record Grading(String mode, boolean correct) {
    }

    public static boolean isObjective(String questionType) {
        return questionType != null && OBJECTIVE_TYPES.contains(questionType.trim());
    }

    /**
     * 服务端判分。
     * 客观题忽略 selfReported，只比较 myAnswer 与 answerText；
     * 主观题无法自动判分，correct 取客户端自评（是否允许为空由调用方决定）。
     */
    public static Grading grade(QuestionBank question, String myAnswer, Boolean selfReported) {
        if (question == null || !isObjective(question.getQuestionType())) {
            return new Grading(SELF, Boolean.TRUE.equals(selfReported));
        }
        String answer = normalize(question.getAnswerText());
        if (answer.isEmpty()) {
            // 题库答案缺失时不能把空答案判成“对”，按错误记录，便于内容线修正
            return new Grading(AUTO, false);
        }
        if ("多选".equals(question.getQuestionType().trim())) {
            return new Grading(AUTO, optionSet(answer).equals(optionSet(myAnswer)));
        }
        return new Grading(AUTO, answer.equals(normalize(myAnswer)));
    }

    /** 单选/判断：去空白、转大写，只保留字母数字，避免 “B ”/“b”/“B.” 这类格式差异。 */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** 多选：忽略顺序与分隔符，按选项键集合比较；部分选中即错误。 */
    private static Set<String> optionSet(String raw) {
        String cleaned = normalize(raw);
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < cleaned.length(); i++) {
            keys.add(String.valueOf(cleaned.charAt(i)));
        }
        return keys;
    }
}
