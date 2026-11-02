package com.zhixue.diagnosis.service;

import com.zhixue.course.entity.QuestionBank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判断题判分：中文/符号/英文等价写法必须归一，空答案判错（P1-1）。
 */
class AnswerGraderTest {

    @Test
    void judgeAcceptsChineseAndSymbolEquivalents() {
        assertTrue(AnswerGrader.grade(question("判断", "对"), "正确", null).correct());
        assertTrue(AnswerGrader.grade(question("判断", "√"), "对", null).correct());
        assertTrue(AnswerGrader.grade(question("判断题", "T"), "是", null).correct());
        assertTrue(AnswerGrader.grade(question("判断", "错"), "×", null).correct());
        assertTrue(AnswerGrader.grade(question("判断", "F"), "错误", null).correct());
    }

    @Test
    void judgeWrongAnswerIsRejected() {
        assertFalse(AnswerGrader.grade(question("判断", "对"), "错", null).correct());
        assertFalse(AnswerGrader.grade(question("判断", "F"), "T", null).correct());
    }

    @Test
    void judgeEmptyAnswerIsWrong() {
        assertFalse(AnswerGrader.grade(question("判断", "对"), "", null).correct());
        assertFalse(AnswerGrader.grade(question("判断", "对"), null, null).correct());
    }

    @Test
    void judgeIsAlwaysAutoGradedIgnoringSelfReport() {
        AnswerGrader.Grading grading = AnswerGrader.grade(question("判断", "对"), "错", true);
        assertTrue(AnswerGrader.AUTO.equals(grading.mode()));
        assertFalse(grading.correct());
    }

    @Test
    void fullWidthOptionIsNormalized() {
        assertTrue(AnswerGrader.grade(question("单选", "B"), "Ｂ", null).correct());
    }

    private QuestionBank question(String type, String answer) {
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setQuestionType(type);
        question.setAnswerText(answer);
        return question;
    }
}
