package com.zhixue.diagnosis.service;

import com.zhixue.course.entity.QuestionBank;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 练习错因推断（docs/05 §2.2）：答错时产出五类错因之一，无法归类落 UNCLASSIFIED，
 * 不允许 new record 再写 null，否则掌握度的严重度自变量没有数据。
 * <p>
 * 客观题（单选/多选/判断）按规则推断，不信任客户端 errorType；主观题（填空/简答等）
 * 无法自动判断，采用学生自评选择的错因，未选择时落 UNCLASSIFIED。</p>
 * <p>
 * 规则优先级（客观题，自上而下匹配）：
 * <ol>
 *   <li>空答案 → METHOD_MISSING（未作答/无解题入口）</li>
 *   <li>题库答案缺失 → UNCLASSIFIED（数据问题，不臆测）</li>
 *   <li>重复作答（第 3 次及以后，priorAttempts ≥ 2）仍错 → METHOD_MISSING（解题方法未建立）</li>
 *   <li>多选只选中部分正确项且无干扰项：距上次练习 ≥30 天 → CONCEPT_CONFUSION（已遗忘），
 *       否则 → STEP_OMISSION（步骤/选项遗漏）</li>
 *   <li>选中干扰项：首次作答且难度 3 → BOUNDARY_ERROR（难题边界），否则 → CONCEPT_CONFUSION</li>
 *   <li>其他 → UNCLASSIFIED</li>
 * </ol>
 * <p>
 * 第 3 条必须先于第 4/5 条判断：单选/判断题非空答错必然选中干扰项，
 * 若放在干扰项分支之后会导致“重复作答”规则永远不可达（05 §2.7）。
 */
public final class ErrorTypeClassifier {

    public static final String UNCLASSIFIED = "UNCLASSIFIED";
    public static final String CONCEPT_CONFUSION = "CONCEPT_CONFUSION";
    public static final String METHOD_MISSING = "METHOD_MISSING";
    public static final String STEP_OMISSION = "STEP_OMISSION";
    public static final String CALCULATION_ERROR = "CALCULATION_ERROR";
    public static final String BOUNDARY_ERROR = "BOUNDARY_ERROR";

    /** 契约允许的错因集合（含显式默认值，保证落库非 null） */
    public static final Set<String> ALLOWED = Set.of(CONCEPT_CONFUSION, METHOD_MISSING,
            STEP_OMISSION, CALCULATION_ERROR, BOUNDARY_ERROR, UNCLASSIFIED);

    /** 记忆回退阈值：超过该天数仍错，按概念遗忘而非步骤遗漏处理 */
    private static final int FORGETTING_DAYS = 30;
    /** 首次作答的难题才能判边界错误，避免把普通错题都推给“题太难” */
    private static final int HARD_DIFFICULTY = 3;
    /** 重复作答阈值：历史作答达到 2 次（本次为第 3 次）仍错，判“解题方法未建立” */
    private static final int REPEAT_ATTEMPTS = 2;

    private ErrorTypeClassifier() {
    }

    /**
     * 推断错因。
     *
     * @param question            题目（含题型/答案/难度）
     * @param myAnswer            学生作答
     * @param isCorrect           服务端最终对错；答对返回 null
     * @param selfReportedErrorType 学生自评错因，仅主观题采用
     * @param priorAttempts       该题历史作答次数（0 = 首次）
     * @param daysSinceLastPractice 距该知识点上次练习天数；无记录为 null
     */
    public static String classify(QuestionBank question, String myAnswer, boolean isCorrect,
                                  String selfReportedErrorType, int priorAttempts,
                                  Integer daysSinceLastPractice) {
        if (isCorrect) {
            return null;
        }
        Optional<String> quick = quickVerdict(question, myAnswer, selfReportedErrorType);
        if (quick.isPresent()) {
            return quick.get();
        }

        // 05 §2.7：重复作答（第 3 次及以后）仍错 → 方法缺失。
        // 必须先于“部分选中/干扰项”判断，否则单选/判断的非空错答会先落 CONCEPT_CONFUSION，本规则不可达。
        if (priorAttempts >= REPEAT_ATTEMPTS) {
            return METHOD_MISSING;
        }
        String type = question.getQuestionType().trim();
        String chosen = AnswerGrader.normalize(myAnswer);
        String answer = AnswerGrader.normalize(question.getAnswerText());
        Set<String> correct = AnswerGrader.optionSet(answer);
        Set<String> selected = AnswerGrader.optionSet(chosen);
        boolean partial = correct.containsAll(selected) && !selected.containsAll(correct);
        if ("多选".equals(type) && partial) {
            return daysSinceLastPractice != null && daysSinceLastPractice >= FORGETTING_DAYS
                    ? CONCEPT_CONFUSION : STEP_OMISSION;
        }
        boolean pickedDistractor = !correct.containsAll(selected);
        if (pickedDistractor) {
            Integer difficulty = question.getDifficulty();
            if (priorAttempts == 0 && difficulty != null && difficulty >= HARD_DIFFICULTY) {
                return BOUNDARY_ERROR;
            }
            return CONCEPT_CONFUSION;
        }
        return UNCLASSIFIED;
    }

    /**
     * 不依赖历史信号（priorAttempts / days）即可定案的分支：
     * 主观题、未作答、题库答案缺失。调用方可在这些分支上跳过 DB 查询。
     * 答对由 {@link #classify} 顶层短路，不在此处。
     */
    public static Optional<String> quickVerdict(QuestionBank question, String myAnswer,
                                                String selfReportedErrorType) {
        if (question == null) {
            return Optional.of(UNCLASSIFIED);
        }
        String type = question.getQuestionType() == null ? "" : question.getQuestionType().trim();
        if (!AnswerGrader.isObjective(type)) {
            return Optional.of(normalizeAllowed(selfReportedErrorType));
        }
        if (AnswerGrader.normalize(myAnswer).isEmpty()) {
            return Optional.of(METHOD_MISSING);
        }
        if (AnswerGrader.normalize(question.getAnswerText()).isEmpty()) {
            return Optional.of(UNCLASSIFIED);
        }
        return Optional.empty();
    }

    /** 大小写/空白归一后只接受契约枚举；非法或空值落 UNCLASSIFIED。 */
    private static String normalizeAllowed(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNCLASSIFIED;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return ALLOWED.contains(key) ? key : UNCLASSIFIED;
    }
}
