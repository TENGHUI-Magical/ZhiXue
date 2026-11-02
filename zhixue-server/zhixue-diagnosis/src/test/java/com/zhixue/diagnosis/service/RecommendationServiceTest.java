package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.Recommendation;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.RecommendationRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 三层推荐规则单测（05 §3.1—§3.4）。
 * 覆盖：拓展层 min(3, avg+1)、同题/冷却去重、题量不足固定理由。
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private StudentErrorRecordService errorRecordService;

    @Mock
    private KnowledgePointService knowledgePointService;

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private RecommendationRecordMapper recommendationRecordMapper;

    @Mock
    private PracticeRecordMapper practiceRecordMapper;

    @Mock
    private RepairPriorityService repairPriorityService;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(errorRecordService, knowledgePointService,
                questionBankService, recommendationRecordMapper, practiceRecordMapper,
                repairPriorityService);
        lenient().when(practiceRecordMapper.selectList(any())).thenReturn(List.of());
        lenient().when(practiceRecordMapper.selectPracticedQids(anyLong(), anyLong(), any())).thenReturn(List.of());
        // 先修/后继为空时不查库；给批量查询一个空默认，避免严格 stub 误报
        lenient().when(questionBankService.listByKpIdsIncludeSecondary(anyLong(), anyList()))
                .thenReturn(List.of());
        // 默认无补漏评分：保持原有层序，理由走固定模板
        lenient().when(repairPriorityService.rank(anyLong(), anyLong()))
                .thenReturn(new RepairPriorityService.RankedCourse(List.of(), Map.of()));
        lenient().when(repairPriorityService.explain(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        kp.setCourseId(1L);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp);
    }

    @Test
    void advancePrefersAvgPlusOne() {
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0201", 1), question("Q_0213", 2)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of());
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of("KP0202"));
        when(questionBankService.listByKpIdsIncludeSecondary(1L, List.of("KP0202")))
                .thenReturn(List.of(question("Q_0219", 2), question("Q_0225", 3)));

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend(null, "KP0201", 1L);
        }

        assertThat(result).extracting(Recommendation::layer)
                .containsExactly("CONSOLIDATE", "VARIANT", "ADVANCE");
        assertThat(result.get(2).qId()).isEqualTo("Q_0225");
        assertThat(result.get(2).difficulty()).isEqualTo(3);
    }

    @Test
    void insufficientPoolUsesFixedReason() {
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0201", 1)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of());
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of());

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend(null, "KP0201", 1L);
        }

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reason()).isEqualTo("题量不足，先练这些");
    }

    @Test
    void cooldownSkipsRecentlyPracticedQuestion() {
        when(practiceRecordMapper.selectPracticedQids(anyLong(), anyLong(), any()))
                .thenReturn(List.of("Q_0201"));
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0201", 1), question("Q_0213", 1)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of());
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of());

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend(null, "KP0201", 1L);
        }

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).qId()).isEqualTo("Q_0213");
    }

    @Test
    void resolvesCourseIdFromKnowledgePointWhenNotProvided() {
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0201", 1)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of());
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of());

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend(null, "KP0201", null);
        }

        assertThat(result).isNotEmpty();
    }

    @Test
    void rejectsMismatchedCourseId() {
        assertThatThrownBy(() -> service.recommend(null, "KP0201", 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("courseId");
    }

    @Test
    void repairPriorityReordersLayersAndExplainsReason() {
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0201", 2)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of("KP0200"));
        when(questionBankService.listByKpIdsIncludeSecondary(1L, List.of("KP0200")))
                .thenReturn(List.of(question("Q_0200", 1, "KP0200")));
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of("KP0202"));
        when(questionBankService.listByKpIdsIncludeSecondary(1L, List.of("KP0202")))
                .thenReturn(List.of(question("Q_0202", 3, "KP0202")));
        when(repairPriorityService.rank(2L, 1L)).thenReturn(new RepairPriorityService.RankedCourse(
                List.of(),
                Map.of("KP0200", priority("KP0200", 0.9, 1),
                        "KP0201", priority("KP0201", 0.4, 0),
                        "KP0202", priority("KP0202", 0.2, -1))));
        when(repairPriorityService.explain(any(), any()))
                .thenReturn("前置知识点未掌握 + 距上次练习 38 天 + 概念混淆型错误");

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend(null, "KP0201", 1L);
        }

        // 先修点评分最高 → VARIANT 排到 CONSOLIDATE 前；同分再按层序稳定
        assertThat(result).extracting(Recommendation::qId)
                .containsExactly("Q_0200", "Q_0201", "Q_0202");
        assertThat(result.get(0).reason())
                .isEqualTo("前置知识点未掌握 + 距上次练习 38 天 + 概念混淆型错误");
    }

    private RepairPriorityService.Priority priority(String kpId, double score, int distance) {
        return new RepairPriorityService.Priority(kpId, score, distance, distance < 0,
                1.0, 1.0, "CONCEPT_CONFUSION", 38);
    }

    private QuestionView question(String qId, int difficulty) {
        return question(qId, difficulty, "KP0201");
    }

    private QuestionView question(String qId, int difficulty, String kpId) {
        return new QuestionView(qId, kpId, "单选", "题干-" + qId, List.of(), difficulty, "教材");
    }
}
