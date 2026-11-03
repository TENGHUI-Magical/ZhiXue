package com.zhixue.diagnosis.service;

import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.KnowledgePrereq;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.KpPracticeSignal;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * PRP 补漏排序单测（05 §3.7）：先修 1—2 跳、遗忘紧迫度、错因可修复性、稳定排序。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepairPriorityServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private CourseService courseService;
    @Mock
    private KnowledgePointService knowledgePointService;

    private RepairPriorityService service;

    @BeforeEach
    void setUp() {
        service = new RepairPriorityService(practiceRecordMapper, courseService,
                knowledgePointService, 30.0, 7);
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0101", 1), kp("KP0102", 2), kp("KP0201", 3),
                kp("KP0202", 4), kp("KP0301", 5)));
        when(knowledgePointService.listPrereqEdges(any())).thenReturn(List.of(
                edge("KP0201", "KP0101"), edge("KP0101", "KP0102"), edge("KP0202", "KP0201")));
        when(practiceRecordMapper.selectKpSignals(anyLong(), anyLong())).thenReturn(List.of(
                signal("KP0201", 1, "CONCEPT_CONFUSION", NOW.minusDays(38)),
                signal("KP0202", 0, null, NOW)));
    }

    @Test
    void directAndTwoHopPrerequisitesRankBeforeUnrelated() {
        RepairPriorityService.RankedCourse ranked = service.rank(2L, 1L, NOW);

        assertThat(ranked.knowledgePoints()).extracting(KnowledgePoint::getKpId)
                .startsWith("KP0101");
        // 后继点今天已练，紧迫度为 0，排在最后
        assertThat(ranked.knowledgePoints()).extracting(KnowledgePoint::getKpId)
                .endsWith("KP0202");
        assertThat(ranked.priorityByKp().get("KP0101").prereqDistance()).isEqualTo(1);
        assertThat(ranked.priorityByKp().get("KP0102").prereqDistance()).isEqualTo(2);
        assertThat(ranked.priorityByKp().get("KP0201").prereqDistance()).isZero();
        assertThat(ranked.priorityByKp().get("KP0301").prereqDistance()).isEqualTo(-1);
    }

    @Test
    void scoreUsesWeightUrgencyAndRepairability() {
        RepairPriorityService.RankedCourse ranked = service.rank(2L, 1L, NOW);

        RepairPriorityService.Priority prereq = ranked.priorityByKp().get("KP0101");
        // 从未练习：紧迫度 1.0；无错因：可修复性 0.5；1 跳权重 1.0
        assertThat(prereq.urgency()).isEqualTo(1.0);
        assertThat(prereq.repairability()).isEqualTo(0.5);
        assertThat(prereq.score()).isEqualTo(0.5);

        RepairPriorityService.Priority anchor = ranked.priorityByKp().get("KP0201");
        assertThat(anchor.repairability()).isEqualTo(1.0);
        assertThat(anchor.daysSinceLastCorrect()).isEqualTo(38);
        assertThat(anchor.urgency()).isBetween(0.5, 0.7);
        assertThat(anchor.score()).isBetween(0.3, 0.4);
    }

    @Test
    void urgencyGrowsWithDaysSincePractice() {
        RepairPriorityService.RankedCourse ranked = service.rank(2L, 1L, NOW);

        double today = ranked.priorityByKp().get("KP0202").urgency();
        double stale = service.rank(2L, 1L, NOW.plusDays(60))
                .priorityByKp().get("KP0201").urgency();
        assertThat(today).isZero();
        assertThat(stale).isGreaterThan(today);
        assertThat(stale).isLessThanOrEqualTo(1.0);
    }

    @Test
    void tieBreakIsStableBySortNoThenKpId() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0402", 1), kp("KP0401", 1)));
        when(practiceRecordMapper.selectKpSignals(anyLong(), anyLong())).thenReturn(List.of());

        List<String> first = service.rank(2L, 1L, NOW).knowledgePoints().stream()
                .map(KnowledgePoint::getKpId).toList();
        List<String> second = service.rank(2L, 1L, NOW).knowledgePoints().stream()
                .map(KnowledgePoint::getKpId).toList();

        assertThat(first).containsExactly("KP0401", "KP0402");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void explainIncludesRelationDaysAndError() {
        RepairPriorityService.Priority priority = service.rank(2L, 1L, NOW)
                .priorityByKp().get("KP0101");

        assertThat(service.explain(priority, "同知识点再练一道"))
                .isEqualTo("前置知识点未掌握 + 尚未答对");
        assertThat(service.explain(null, "同知识点再练一道"))
                .isEqualTo("同知识点再练一道");
    }

    // ---------- firstReason："为什么排第一"（算法评审 2026-11-03 唯一采纳项，05 §3.7）----------
    // 期望句全部手写写死：纯函数同一输入永远同一句话，文案漂移即测试红。

    /** 正常态：三因子齐备的完整叙事句（评审示例风格，人话、面向教师）。 */
    @Test
    void firstReasonNarratesThreeFactors() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0101", 0.583, 1, false, 0.583, 1.0, "CONCEPT_CONFUSION", 21, 30);

        assertThat(service.firstReason("关系模型", priority, "同知识点再练一道"))
                .isEqualTo("先补关系模型：它是当前错题知识点的直接先修，已 21 天未答对，"
                        + "概念混淆型错误，属可修复；综合先修、遗忘与错因三因子，它当前补漏优先级最高");
    }

    /** 边界①：无先修关系 → 按"最薄弱"叙事，不编造关联。 */
    @Test
    void firstReasonWithoutPrereqRelationFallsToWeakestNarrative() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0301", 0.2, -1, false, 0.5, 0.5, null, 45, 60);

        assertThat(service.firstReason(null, priority, null))
                .isEqualTo("先补KP0301：它是当前课程中最薄弱的环节，已 45 天未答对；"
                        + "综合先修、遗忘与错因三因子，它当前补漏优先级最高");
    }

    /** 边界②：证据不足（从未答对）→ 如实说，紧迫度按最高但不虚造天数。 */
    @Test
    void firstReasonWithNoCorrectEvidenceSaysSo() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0201", 0.6, 0, false, 1.0, 0.9, "METHOD_MISSING", null, 12);

        assertThat(service.firstReason("关系模型", priority, null))
                .isEqualTo("先补关系模型：它正是当前出错的知识点，至今尚未答对过，"
                        + "方法缺失型错误，属可修复；综合先修、遗忘与错因三因子，它当前补漏优先级最高");
    }

    /** 边界③：UNCLASSIFIED 错因 → "错因待归类"，不猜具体类型。 */
    @Test
    void firstReasonWithUnclassifiedErrorSaysPendingClassification() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0101", 0.4, 1, false, 0.6, 0.5, "UNCLASSIFIED", 33, 40);

        assertThat(service.firstReason("关系模型", priority, null))
                .isEqualTo("先补关系模型：它是当前错题知识点的直接先修，已 33 天未答对，"
                        + "错因待归类；综合先修、遗忘与错因三因子，它当前补漏优先级最高");
    }

    /** 边界④：冷却期内（3 天前刚练过，冷却 7 天）仍排第一 → 薄弱未消除、趁热补。 */
    @Test
    void firstReasonInCooldownSaysWeaknessUnresolved() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0201", 0.7, 0, false, 0.6, 1.0, "CONCEPT_CONFUSION", 25, 3);

        assertThat(service.firstReason("关系模型", priority, null))
                .isEqualTo("先补关系模型：它正是当前出错的知识点，已 25 天未答对，"
                        + "概念混淆型错误，属可修复；该点 3 天内刚练过仍排第一，薄弱未消除，趁热补最有效");
    }

    /** 兜底：无评分（知识点不在范围/题量不足）→ fallback，不编造叙事。 */
    @Test
    void firstReasonFallsBackWhenPriorityMissing() {
        assertThat(service.firstReason("关系模型", null, null))
                .isEqualTo("按补漏优先级常规推荐");
        assertThat(service.firstReason("关系模型", null, "题量不足，先练这些"))
                .isEqualTo("题量不足，先练这些");
    }

    /**
     * 新口径核心场景：昨天答错、最后一次答对在 40 天前 → 紧迫度必须高。
     * 手算：urgency = 1 - 0.5^(40/30) = 1 - 0.39685 = 0.60315
     * （若误用旧口径“距上次练习 1 天”，会得到 1 - 0.5^(1/30) ≈ 0.023，方向完全相反）。
     * 期望值独立手算写死，不用被测类计算。
     */
    @Test
    void recentErrorWithoutRecentCorrectIsUrgent() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0501", 1), kp("KP0502", 2)));
        when(practiceRecordMapper.selectKpSignals(anyLong(), anyLong())).thenReturn(List.of(
                // 昨天答错；最后一次正确作答在 40 天前
                signal("KP0501", 1, "CONCEPT_CONFUSION", NOW.minusDays(40)),
                // 对照组：今天刚答对 → 紧迫度 0
                signal("KP0502", 0, null, NOW)));

        RepairPriorityService.RankedCourse ranked = service.rank(2L, 1L, NOW);
        RepairPriorityService.Priority stale = ranked.priorityByKp().get("KP0501");
        RepairPriorityService.Priority fresh = ranked.priorityByKp().get("KP0502");

        assertThat(stale.urgency()).isCloseTo(0.60315, within(1e-4));
        assertThat(stale.daysSinceLastCorrect()).isEqualTo(40);
        assertThat(fresh.urgency()).isEqualTo(0.0);
        // “刚答错且正确证据已遗忘”必须排在“今天答对”之前
        assertThat(ranked.knowledgePoints()).extracting(KnowledgePoint::getKpId)
                .startsWith("KP0501");
    }

    @Test
    void emptyCourseDegradesToEmptyRanking() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of());

        RepairPriorityService.RankedCourse ranked = service.rank(2L, 1L, NOW);

        assertThat(ranked.knowledgePoints()).isEmpty();
        assertThat(ranked.priorityByKp()).isEmpty();
    }

    private KnowledgePoint kp(String kpId, int sortNo) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId(kpId);
        kp.setCourseId(1L);
        kp.setSortNo(sortNo);
        kp.setIsActive(1);
        return kp;
    }

    private KnowledgePrereq edge(String kpId, String prereqKpId) {
        KnowledgePrereq edge = new KnowledgePrereq();
        edge.setKpId(kpId);
        edge.setPrereqKpId(prereqKpId);
        return edge;
    }

    private KpPracticeSignal signal(String kpId, int wrongCount, String errorType,
                                    LocalDateTime lastCorrectAt) {
        KpPracticeSignal signal = new KpPracticeSignal();
        signal.setKpId(kpId);
        signal.setTotalCount(wrongCount + 1);
        signal.setWrongCount(wrongCount);
        signal.setLastErrorType(errorType);
        signal.setLastCorrectAt(lastCorrectAt);
        return signal;
    }
}
