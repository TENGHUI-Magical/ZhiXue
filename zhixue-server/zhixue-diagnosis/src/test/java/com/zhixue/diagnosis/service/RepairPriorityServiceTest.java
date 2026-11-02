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
                knowledgePointService, 30.0);
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
        assertThat(anchor.daysSinceLastPractice()).isEqualTo(38);
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
                .isEqualTo("前置知识点未掌握 + 尚未练习");
        assertThat(service.explain(null, "同知识点再练一道"))
                .isEqualTo("同知识点再练一道");
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
                                    LocalDateTime lastPracticedAt) {
        KpPracticeSignal signal = new KpPracticeSignal();
        signal.setKpId(kpId);
        signal.setTotalCount(wrongCount + 1);
        signal.setWrongCount(wrongCount);
        signal.setLastErrorType(errorType);
        signal.setLastPracticedAt(lastPracticedAt);
        return signal;
    }
}
