package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.KnowledgePrereq;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.KpPracticeSignal;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
    private StudentErrorRecordMapper errorRecordMapper;
    @Mock
    private CourseService courseService;
    @Mock
    private KnowledgePointService knowledgePointService;

    private RepairPriorityService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                StudentErrorRecord.class);
    }

    @BeforeEach
    void setUp() {
        service = new RepairPriorityService(practiceRecordMapper, errorRecordMapper, courseService,
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
        // 从未作答（无任何证据）：紧迫度 0.8（低于“从未答对”的 1.0）；
        // 无错因：可修复性 0.5；1 跳权重 1.0 → score=0.4
        assertThat(prereq.urgency()).isEqualTo(0.8);
        assertThat(prereq.repairability()).isEqualTo(0.5);
        assertThat(prereq.score()).isEqualTo(0.4);

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

        assertThat(service.explain(priority, "同一块知识，再练一道"))
                .isEqualTo("这道题要用到它，还没答对过");
        assertThat(service.explain(null, "同一块知识，再练一道"))
                .isEqualTo("同一块知识，再练一道");
    }

    // ---------- firstReason："为什么排第一"（学生语言版，2026-11-03 复核重写）----------
    // 期望句全部手写写死：纯函数同一输入永远同一句话，文案漂移即测试红。
    // 硬约束（与实现注释一致）：≤45 字、禁用词（先修/权重/优先级/因子/可修复/紧迫度/补漏/聚合/后验）。

    /**
     * 正常态：三因子齐备的学生叙事句。
     * 正例（本断言）："先练关系模型：这道题要用到它，已 21 天没答对，把两个概念记混了，先练它见效最快"
     * 反例（写成这样就说明又回术语了）："先补关系模型：它是当前错题知识点的直接先修……综合三因子补漏优先级最高"
     */
    @Test
    void firstReasonNarratesThreeFactors() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0101", 0.583, 1, false, 0.583, 1.0, "CONCEPT_CONFUSION", 21, 30);

        assertThat(service.firstReason("关系模型", priority, "同一块知识，再练一道"))
                .isEqualTo("先练关系模型：这道题要用到它，已 21 天没答对，"
                        + "把两个概念记混了，先练它见效最快");
    }

    /**
     * 边界①：无先修关系 → "最没把握的一块"，不编造关联。
     * 正例："先练KP0301：它是你现在最没把握的一块，已 45 天没答对，先练它见效最快"
     * 反例："它是当前课程中最薄弱的环节，综合先修权重与遗忘紧迫度最高"
     */
    @Test
    void firstReasonWithoutPrereqRelationFallsToWeakestNarrative() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0301", 0.2, -1, false, 0.5, 0.5, null, 45, 60);

        assertThat(service.firstReason(null, priority, null))
                .isEqualTo("先练KP0301：它是你现在最没把握的一块，已 45 天没答对，"
                        + "先练它见效最快");
    }

    /**
     * 边界②：从未答对（证据不足）→ 如实说"还没答对过"，不虚造天数。
     * 正例："先练关系模型：你刚错的就是这块，还没答对过，方法还没掌握，先练它见效最快"
     * 反例："证据不足（daysSinceLastCorrect=null），紧迫度按最高 1.0 处理"
     */
    @Test
    void firstReasonWithNoCorrectEvidenceSaysSo() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0201", 0.6, 0, false, 1.0, 0.9, "METHOD_MISSING", null, 12);

        assertThat(service.firstReason("关系模型", priority, null))
                .isEqualTo("先练关系模型：你刚错的就是这块，还没答对过，"
                        + "方法还没掌握，先练它见效最快");
    }

    /**
     * 边界③：错因待归类 → "错在哪再做一遍才知道"，不猜具体类型。
     * 正例："先练关系模型：这道题要用到它，已 33 天没答对，错在哪再做一遍才知道，先练它见效最快"
     * 反例："错因 UNCLASSIFIED，无法归类，建议人工复核"
     */
    @Test
    void firstReasonWithUnclassifiedErrorSaysPendingClassification() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0101", 0.4, 1, false, 0.6, 0.5, "UNCLASSIFIED", 33, 40);

        assertThat(service.firstReason("关系模型", priority, null))
                .isEqualTo("先练关系模型：这道题要用到它，已 33 天没答对，"
                        + "错在哪再做一遍才知道，先练它见效最快");
    }

    /**
     * 边界④：冷却期内（3 天前刚练过，冷却 7 天）仍排第一 → "还是错"说明没学会，趁热再练。
     * 正例："先练关系模型：你刚错的就是这块，3 天前刚练过还是错，趁热再练一遍最有效"
     * 反例："该知识点处于冷却期内仍排名第一，薄弱程度未消除，建议立即复习"
     */
    @Test
    void firstReasonInCooldownSaysWeaknessUnresolved() {
        RepairPriorityService.Priority priority = new RepairPriorityService.Priority(
                "KP0201", 0.7, 0, false, 0.6, 1.0, "CONCEPT_CONFUSION", 25, 3);

        assertThat(service.firstReason("关系模型", priority, null))
                .isEqualTo("先练关系模型：你刚错的就是这块，3 天前刚练过还是错，趁热再练一遍最有效");
    }

    /** 兜底：无评分（知识点不在范围/题量不足）→ fallback，不编造叙事。 */
    @Test
    void firstReasonFallsBackWhenPriorityMissing() {
        assertThat(service.firstReason("关系模型", null, null))
                .isEqualTo("先练它，见效最快");
        assertThat(service.firstReason("关系模型", null, "这章题不多，先练这几道"))
                .isEqualTo("这章题不多，先练这几道");
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

    /**
     * 边界：从未作答 ≠ 从未答对。
     * 从未作答（无任何练习/错题证据）紧迫度 0.8；有错题证据但从未答对 1.0。
     * 若不区分，新知识点会和真正漏洞一起挤在 1.0，排序失去解释力。
     */
    @Test
    void neverAnsweredIsLessUrgentThanNeverCorrect() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0601", 1), kp("KP0602", 2)));
        when(practiceRecordMapper.selectKpSignals(anyLong(), anyLong())).thenReturn(List.of(
                // 有错题证据、从未答对
                signal("KP0602", 1, "CONCEPT_CONFUSION", null)));

        RepairPriorityService.RankedCourse ranked = service.rank(2L, 1L, NOW);

        assertThat(ranked.priorityByKp().get("KP0601").urgency()).isEqualTo(0.8);
        assertThat(ranked.priorityByKp().get("KP0602").urgency()).isEqualTo(1.0);
    }

    /**
     * 边界：PRP 纳入 CONFIRMED 上传错题（与掌握度同口径）。
     * 只有上传错题、没有练习记录的 kp 也必须被视为“有证据但从未答对”；
     * 已被练习覆盖的 qId 不重复计。
     */
    @Test
    void confirmedUploadBecomesEvidenceButNotCorrect() {
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(
                kp("KP0601", 1), kp("KP0602", 2)));
        when(practiceRecordMapper.selectKpSignals(anyLong(), anyLong())).thenReturn(List.of());
        StudentErrorRecord upload = new StudentErrorRecord();
        upload.setLogId("11");
        upload.setUserId(2L);
        upload.setKpId("KP0602");
        upload.setQId("Q_upload");
        upload.setErrorType("CONCEPT_CONFUSION");
        upload.setStatus("CONFIRMED");
        upload.setConfirmedAt(NOW.minusDays(1));
        upload.setCreatedAt(NOW.minusDays(1));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of(upload));
        when(practiceRecordMapper.selectDistinctQidsByUser(2L)).thenReturn(List.of());

        RepairPriorityService.RankedCourse ranked = service.rank(2L, 1L, NOW);

        RepairPriorityService.Priority withUpload = ranked.priorityByKp().get("KP0602");
        assertThat(withUpload.urgency()).isEqualTo(1.0);
        assertThat(withUpload.daysSinceLastCorrect()).isNull();
        assertThat(withUpload.repairability()).isEqualTo(1.0);
        assertThat(withUpload.daysSinceLastPractice()).isEqualTo(1);
        assertThat(ranked.priorityByKp().get("KP0601").urgency()).isEqualTo(0.8);
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
