package com.zhixue.diagnosis.service;

import com.zhixue.course.entity.QuestionBank;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错因推断单测（05 §2.7）：客观题规则、主观题自评、默认值兜底。
 */
class ErrorTypeClassifierTest {

    @Test
    void correctAnswerHasNoErrorType() {
        assertThat(ErrorTypeClassifier.classify(question("单选", "B", 2), "B", true,
                null, 0, null)).isNull();
    }

    @Test
    void singleChoiceDistractorIsConceptConfusion() {
        assertThat(ErrorTypeClassifier.classify(question("单选", "B", 2), "A", false,
                null, 0, 10)).isEqualTo(ErrorTypeClassifier.CONCEPT_CONFUSION);
    }

    @Test
    void firstAttemptHardQuestionDistractorIsBoundaryError() {
        assertThat(ErrorTypeClassifier.classify(question("单选", "B", 3), "A", false,
                null, 0, null)).isEqualTo(ErrorTypeClassifier.BOUNDARY_ERROR);
    }

    @Test
    void repeatedHardQuestionIsConceptConfusionNotBoundary() {
        assertThat(ErrorTypeClassifier.classify(question("单选", "B", 3), "A", false,
                null, 1, null)).isEqualTo(ErrorTypeClassifier.CONCEPT_CONFUSION);
    }

    @Test
    void multiChoicePartialIsStepOmissionWhenRecent() {
        assertThat(ErrorTypeClassifier.classify(question("多选", "ABC", 2), "AC", false,
                null, 0, 7)).isEqualTo(ErrorTypeClassifier.STEP_OMISSION);
    }

    @Test
    void multiChoicePartialAfterLongGapIsConceptConfusion() {
        assertThat(ErrorTypeClassifier.classify(question("多选", "ABC", 2), "AC", false,
                null, 0, 45)).isEqualTo(ErrorTypeClassifier.CONCEPT_CONFUSION);
    }

    /** 自审补：无历史练习（daysSince=null）的部分对按“近期”处理 → 步骤遗漏，不落概念混淆。 */
    @Test
    void multiChoicePartialWithoutHistoryIsStepOmission() {
        assertThat(ErrorTypeClassifier.classify(question("多选", "ABC", 2), "AC", false,
                null, 0, null)).isEqualTo(ErrorTypeClassifier.STEP_OMISSION);
    }

    @Test
    void multiChoiceDistractorIsConceptConfusion() {
        assertThat(ErrorTypeClassifier.classify(question("多选", "ABC", 2), "ABD", false,
                null, 0, 3)).isEqualTo(ErrorTypeClassifier.CONCEPT_CONFUSION);
    }

    @Test
    void emptyAnswerIsMethodMissing() {
        assertThat(ErrorTypeClassifier.classify(question("单选", "B", 1), "", false,
                null, 0, null)).isEqualTo(ErrorTypeClassifier.METHOD_MISSING);
    }

    @Test
    void subjectiveUsesSelfReportedType() {
        assertThat(ErrorTypeClassifier.classify(question("简答", "参考答案", 2), "我的答案", false,
                "CALCULATION_ERROR", 0, null)).isEqualTo(ErrorTypeClassifier.CALCULATION_ERROR);
    }

    @Test
    void subjectiveWithoutSelectionDefaultsToUnclassified() {
        assertThat(ErrorTypeClassifier.classify(question("简答", "参考答案", 2), "我的答案", false,
                null, 0, null)).isEqualTo(ErrorTypeClassifier.UNCLASSIFIED);
        assertThat(ErrorTypeClassifier.classify(question("简答", "参考答案", 2), "我的答案", false,
                "UNKNOWN_KEY", 0, null)).isEqualTo(ErrorTypeClassifier.UNCLASSIFIED);
    }

    @Test
    void missingAnswerTextIsUnclassifiedNotGuess() {
        assertThat(ErrorTypeClassifier.classify(question("单选", "", 2), "A", false,
                null, 0, null)).isEqualTo(ErrorTypeClassifier.UNCLASSIFIED);
    }

    private QuestionBank question(String type, String answer, int difficulty) {
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setQuestionType(type);
        question.setAnswerText(answer);
        question.setDifficulty(difficulty);
        return question;
    }

    /**
     * 重复作答口径（05 §2.7）：第 3 次及以后（priorAttempts≥2）仍错 → 方法缺失；
     * 第 2 次仍错仍按干扰项规则归概念混淆，避免过早把“还没学会”判成“方法没建立”。
     */
    @Test
    void thirdAttemptStillWrongIsMethodMissing() {
        QuestionBank single = question("单选", "B", 2);
        assertThat(ErrorTypeClassifier.classify(single, "A", false, null, 2, null))
                .isEqualTo(ErrorTypeClassifier.METHOD_MISSING);
        assertThat(ErrorTypeClassifier.classify(single, "A", false, null, 1, null))
                .isEqualTo(ErrorTypeClassifier.CONCEPT_CONFUSION);
    }

    /** 重复作答规则优先于“多选部分选中/选中干扰项”细分，第三次仍错不能落步骤遗漏。 */
    @Test
    void repeatedPartialOrDistractorIsMethodMissing() {
        QuestionBank multi = question("多选", "ABC", 2);
        assertThat(ErrorTypeClassifier.classify(multi, "AC", false, null, 2, 3))
                .isEqualTo(ErrorTypeClassifier.METHOD_MISSING);
        assertThat(ErrorTypeClassifier.classify(multi, "ABD", false, null, 2, 3))
                .isEqualTo(ErrorTypeClassifier.METHOD_MISSING);
    }

    /** 快路径：不依赖历史信号的分支直接定案，调用方可跳过 DB 查询。 */
    @Test
    void quickVerdictCoversHistoryIndependentBranches() {
        assertThat(ErrorTypeClassifier.quickVerdict(question("简答", "参考答案", 2), "我的答案",
                "CALCULATION_ERROR")).contains(ErrorTypeClassifier.CALCULATION_ERROR);
        assertThat(ErrorTypeClassifier.quickVerdict(question("简答", "参考答案", 2), "我的答案",
                null)).contains(ErrorTypeClassifier.UNCLASSIFIED);
        assertThat(ErrorTypeClassifier.quickVerdict(question("单选", "B", 1), "",
                null)).contains(ErrorTypeClassifier.METHOD_MISSING);
        assertThat(ErrorTypeClassifier.quickVerdict(question("单选", "", 1), "A",
                null)).contains(ErrorTypeClassifier.UNCLASSIFIED);
        assertThat(ErrorTypeClassifier.quickVerdict(null, "A", null))
                .contains(ErrorTypeClassifier.UNCLASSIFIED);
    }

    /** 客观题作答完整时快路径不越权判案，必须回落到历史信号分支。 */
    @Test
    void quickVerdictLeavesObjectiveAnsweredCasesToFullClassification() {
        assertThat(ErrorTypeClassifier.quickVerdict(question("单选", "B", 2), "A", null)).isEmpty();
        assertThat(ErrorTypeClassifier.quickVerdict(question("多选", "ABC", 2), "AC", null)).isEmpty();
    }
}
