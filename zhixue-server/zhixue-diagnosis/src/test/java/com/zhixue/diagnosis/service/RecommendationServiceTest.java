package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(errorRecordService, knowledgePointService,
                questionBankService, recommendationRecordMapper, practiceRecordMapper);
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of());
        when(practiceRecordMapper.selectPracticedQids(anyLong(), anyLong(), any())).thenReturn(List.of());
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        when(knowledgePointService.getById("KP0201")).thenReturn(kp);
    }

    @Test
    void advancePrefersAvgPlusOne() {
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0201", 1), question("Q_0213", 2)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of());
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of("KP0202"));
        when(questionBankService.listByKpIncludeSecondary(1L, "KP0202"))
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

    private QuestionView question(String qId, int difficulty) {
        return new QuestionView(qId, "KP0201", "单选", "题干-" + qId, List.of(), difficulty, "教材");
    }
}
