package com.zhixue.diagnosis.service;

import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.DueReviewItem;
import com.zhixue.diagnosis.dto.KpPracticeSignal;
import com.zhixue.diagnosis.dto.QuestionPracticeSignal;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 到期复习队列单测（05 §3.8）。期望值全部手算写死，不用被测类反推：
 * <ul>
 *   <li>halfLife=30、cooldown=7；mastery = (2+sw)/(4+sw+ss)（Beta(2,2) 先验后验均值）。</li>
 *   <li>KP0201：1 次对(40 天前) + 1 次概念混淆错(10 天前)
 *       → sw=0.5^(40/30)=0.39685、ss=1.0×0.5^(10/30)=0.79370
 *       → mastery=2.39685/5.19055=0.46177→0.462；score=(1-0.5^(40/30))×(1-0.46177)=0.32463。</li>
 *   <li>KP0202：2 次对(45 天前) 无错 → sw=2×0.5^1.5=0.70711、ss=0
 *       → mastery=2.70711/4.70711=0.57511→0.575；score=(1-0.5^1.5)×0.42489=0.27467。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DueReviewServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private PortraitService portraitService;
    @Mock
    private CourseService courseService;
    @Mock
    private QuestionBankService questionBankService;

    private DueReviewService service;

    @BeforeEach
    void setUp() {
        service = new DueReviewService(practiceRecordMapper, portraitService, courseService,
                questionBankService, 30.0, 7);
    }

    @Test
    void noPracticeHistoryYieldsEmptyQueue() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp("KP0201", 1)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of());

        assertThat(service.dueReviews(2L, 1L, NOW)).isEmpty();
    }

    /** 三种不到期：从未答对（走 PRP 补漏）、未过半衰期、刚复习过（冷却内）；未知知识点信号被忽略。 */
    @Test
    void neverCorrectFreshOrCoolingKpsAreNotDue() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0201", 1), kp("KP0202", 2), kp("KP0203", 3)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                // 从未答对：只错过，不是“复习”是“补漏”
                signal("KP0201", 1, 1, null, NOW.minusDays(40), "CONCEPT_CONFUSION"),
                // 未过半衰期：28 天前答对，保留率仍 > 50%
                signal("KP0202", 1, 0, NOW.minusDays(28), NOW.minusDays(28), null),
                // 刚复习过：40 天前答对但 3 天前又练过，冷却内不出队
                signal("KP0203", 2, 1, NOW.minusDays(40), NOW.minusDays(3), "METHOD_MISSING"),
                // 停用/未知知识点不进队列
                signal("KP9999", 1, 0, NOW.minusDays(40), NOW.minusDays(40), null)));

        assertThat(service.dueReviews(2L, 1L, NOW)).isEmpty();
    }

    /** 半衰期边界：距上次答对恰好 30 天即到期（判定是 >=，不是“超过 30 天”）。 */
    @Test
    void exactlyHalfLifeIsDue() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp("KP0201", 1)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 1, 0, NOW.minusDays(30), NOW.minusDays(30), null)));
        stubTail(new HashMap<>(), List.of(qSignal("KP0201", "Q01", null, NOW.minusDays(30))),
                List.of(question("Q01", "KP0201", 2)));

        List<DueReviewItem> items = service.dueReviews(2L, 1L, NOW);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).daysSinceLastCorrect()).isEqualTo(30);
    }

    /** 半衰期边界下侧：距上次答对 29 天仍不触发（保留率仍 > 50%）。 */
    @Test
    void justUnderHalfLifeIsNotDue() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp("KP0201", 1)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 1, 0, NOW.minusDays(29), NOW.minusDays(29), null)));

        assertThat(service.dueReviews(2L, 1L, NOW)).isEmpty();
    }

    /** 冷却边界：距上次任意作答恰好 7 天即出队（判定是 >=，不是“超过 7 天”）。 */
    @Test
    void exactlyCooldownDaysIsDue() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp("KP0201", 1)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 2, 1, NOW.minusDays(40), NOW.minusDays(7), "METHOD_MISSING")));
        stubTail(new HashMap<>(), List.of(
                qSignal("KP0201", "Q01", NOW.minusDays(7), NOW.minusDays(40))),
                List.of(question("Q01", "KP0201", 2)));

        List<DueReviewItem> items = service.dueReviews(2L, 1L, NOW);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).daysSinceLastCorrect()).isEqualTo(40);
    }

    /** 冷却边界下侧：距上次任意作答 6 天仍在冷却内，不出队。 */
    @Test
    void justInsideCooldownIsNotDue() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp("KP0201", 1)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 1, 0, NOW.minusDays(40), NOW.minusDays(6), null)));

        assertThat(service.dueReviews(2L, 1L, NOW)).isEmpty();
    }

    /** 到期项排序（score 降序）+ 可解释理由 + 掌握度与画像同源；期望值手算见类注释。 */
    @Test
    void dueQueueOrdersByForgettingAndMastery() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0201", 1), kp("KP0202", 2)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 2, 1, NOW.minusDays(40), NOW.minusDays(10), "CONCEPT_CONFUSION"),
                signal("KP0202", 2, 0, NOW.minusDays(45), NOW.minusDays(45), null)));
        Map<String, UserKpAgg> aggs = new HashMap<>();
        aggs.put("KP0201", agg("KP0201", 0.39685, 0.79370));
        aggs.put("KP0202", agg("KP0202", 0.70711, 0.0));
        stubTail(aggs, List.of(
                qSignal("KP0201", "Q01", NOW.minusDays(10), NOW.minusDays(40)),
                qSignal("KP0202", "Q02", null, NOW.minusDays(45))),
                List.of(question("Q01", "KP0201", 2), question("Q02", "KP0202", 2),
                        question("Q03", "KP0201", 1)));

        List<DueReviewItem> items = service.dueReviews(2L, 1L, NOW);

        assertThat(items).hasSize(2);
        // score 0.32463 > 0.27467：久未答对 + 掌握度更低的 KP0201 排前
        assertThat(items).extracting(DueReviewItem::kpId)
                .containsExactly("KP0201", "KP0202");
        DueReviewItem first = items.get(0);
        assertThat(first.source()).isEqualTo("DUE_REVIEW");
        assertThat(first.kpName()).isEqualTo("关系模型");
        assertThat(first.qId()).isEqualTo("Q01");
        assertThat(first.daysSinceLastCorrect()).isEqualTo(40);
        assertThat(first.mastery()).isEqualTo(0.462);
        assertThat(first.sampleCount()).isEqualTo(2);
        assertThat(first.reason()).isEqualTo("距上次答对 40 天，早该复习了，把两个概念记混了");
        DueReviewItem second = items.get(1);
        assertThat(second.qId()).isEqualTo("Q02");
        assertThat(second.mastery()).isEqualTo(0.575);
        assertThat(second.reason()).isEqualTo("距上次答对 45 天，早该复习了，该回来练一遍");
    }

    /** 选题：最近答错的题优先重做；只对过的知识点重做最近答对的题，不用未练题。 */
    @Test
    void picksMostRecentWrongQuestionFirst() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0201", 1), kp("KP0202", 2)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 3, 2, NOW.minusDays(40), NOW.minusDays(10), "CONCEPT_CONFUSION"),
                signal("KP0202", 1, 0, NOW.minusDays(40), NOW.minusDays(40), null)));
        stubTail(new HashMap<>(), List.of(
                // KP0201 练过三题：最近答错的是 Q_w1（10 天前），优先于 35 天前错的 Q_w2 与 40 天前对的 Q_c
                qSignal("KP0201", "Q_w2", NOW.minusDays(35), NOW.minusDays(50)),
                qSignal("KP0201", "Q_w1", NOW.minusDays(10), null),
                qSignal("KP0201", "Q_c", null, NOW.minusDays(40)),
                // KP0202 只对过：重做 Q_c2 而不是未练过的 Q_new
                qSignal("KP0202", "Q_c2", null, NOW.minusDays(40))),
                List.of(question("Q_w1", "KP0201", 3), question("Q_w2", "KP0201", 1),
                        question("Q_c", "KP0201", 1), question("Q_c2", "KP0202", 2),
                        question("Q_new", "KP0202", 1)));

        List<DueReviewItem> items = service.dueReviews(2L, 1L, NOW);

        assertThat(items).extracting(DueReviewItem::kpId).containsExactly("KP0201", "KP0202");
        assertThat(items.get(0).qId()).isEqualTo("Q_w1");
        assertThat(items.get(1).qId()).isEqualTo("Q_c2");
    }

    /** 练过的题已不在题库：用该知识点未练过的题兜底（难度升序）。 */
    @Test
    void fallsBackToUnseenQuestionWhenPracticedMissingFromBank() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp("KP0201", 1)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 1, 0, NOW.minusDays(40), NOW.minusDays(40), null)));
        stubTail(new HashMap<>(), List.of(qSignal("KP0201", "Q_gone", null, NOW.minusDays(40))),
                List.of(question("Q_hard", "KP0201", 3), question("Q_easy", "KP0201", 1)));

        List<DueReviewItem> items = service.dueReviews(2L, 1L, NOW);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).qId()).isEqualTo("Q_easy");
    }

    /** 知识点无任何可用题目（内容缺口）：跳过并返回空，不抛错。 */
    @Test
    void skipsKpWithoutAnyQuestion() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0201", 1), kp("KP0202", 2)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 1, 0, NOW.minusDays(40), NOW.minusDays(40), null)));
        stubTail(new HashMap<>(), List.of(qSignal("KP0201", "Q_gone", null, NOW.minusDays(40))),
                List.of(question("Q_other", "KP0202", 1)));

        assertThat(service.dueReviews(2L, 1L, NOW)).isEmpty();
    }

    /** 聚合窗口外无证据：掌握度按 Beta(2,2) 先验 0.5 计，不阻断到期判定。 */
    @Test
    void missingAggFallsBackToPriorMastery() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp("KP0201", 1)));
        when(practiceRecordMapper.selectKpSignals(2L, 1L)).thenReturn(List.of(
                signal("KP0201", 1, 0, NOW.minusDays(40), NOW.minusDays(40), null)));
        stubTail(new HashMap<>(), List.of(qSignal("KP0201", "Q01", null, NOW.minusDays(40))),
                List.of(question("Q01", "KP0201", 2)));

        List<DueReviewItem> items = service.dueReviews(2L, 1L, NOW);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).mastery()).isEqualTo(0.5);
    }

    /** 聚合尾部依赖（掌握度/题目信号/题库）：候选为空时这些查询不会发生，统一 lenient。 */
    private void stubTail(Map<String, UserKpAgg> aggs,
                          List<QuestionPracticeSignal> questionSignals,
                          List<QuestionView> questions) {
        lenient().when(portraitService.userKpAggregates(2L)).thenReturn(aggs);
        lenient().when(practiceRecordMapper.selectQuestionSignals(2L, 1L))
                .thenReturn(questionSignals);
        lenient().when(questionBankService.listByCourse(1L)).thenReturn(questions);
    }

    private KnowledgePoint kp(String kpId, int sortNo) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId(kpId);
        kp.setName(kpId.equals("KP0201") ? "关系模型" : "知识点" + kpId);
        kp.setCourseId(1L);
        kp.setSortNo(sortNo);
        kp.setIsActive(1);
        return kp;
    }

    private KpPracticeSignal signal(String kpId, int totalCount, int wrongCount,
                                    LocalDateTime lastCorrectAt, LocalDateTime lastPracticedAt,
                                    String lastErrorType) {
        KpPracticeSignal signal = new KpPracticeSignal();
        signal.setKpId(kpId);
        signal.setTotalCount(totalCount);
        signal.setWrongCount(wrongCount);
        signal.setLastCorrectAt(lastCorrectAt);
        signal.setLastPracticedAt(lastPracticedAt);
        signal.setLastErrorType(lastErrorType);
        return signal;
    }

    private QuestionPracticeSignal qSignal(String kpId, String qId, LocalDateTime lastWrongAt,
                                           LocalDateTime lastCorrectAt) {
        QuestionPracticeSignal signal = new QuestionPracticeSignal();
        signal.setKpId(kpId);
        signal.setQId(qId);
        signal.setLastWrongAt(lastWrongAt);
        signal.setLastCorrectAt(lastCorrectAt);
        return signal;
    }

    private UserKpAgg agg(String kpId, double successWeight, double severitySum) {
        UserKpAgg agg = new UserKpAgg();
        agg.setUserId(2L);
        agg.setKpId(kpId);
        agg.setEvents(1);
        agg.setSuccessWeight(successWeight);
        agg.setSeveritySum(severitySum);
        agg.setErrorCount(severitySum > 0 ? 1 : 0);
        return agg;
    }

    private QuestionView question(String qId, String primaryKpId, int difficulty) {
        return new QuestionView(qId, primaryKpId, "单选", "题干 " + qId, List.of(), difficulty,
                "教材");
    }
}
