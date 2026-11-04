package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.Recommendation;
import com.zhixue.diagnosis.entity.RecommendationRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.RecommendationRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
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

    @Mock
    private PlatformTransactionManager transactionManager;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(null);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        service = new RecommendationService(errorRecordService, knowledgePointService,
                questionBankService, recommendationRecordMapper, practiceRecordMapper,
                repairPriorityService, 7, transactionTemplate);
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
        lenient().when(knowledgePointService.getById("KP0201")).thenReturn(kp);
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
        assertThat(result.get(0).reason()).isEqualTo("这章题不多，先练这几道");
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
                .thenReturn("这道题要用到它，已 38 天没答对，把两个概念记混了");

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend(null, "KP0201", 1L);
        }

        // 先修点评分最高 → VARIANT 排到 CONSOLIDATE 前；同分再按层序稳定
        assertThat(result).extracting(Recommendation::qId)
                .containsExactly("Q_0200", "Q_0201", "Q_0202");
        assertThat(result.get(0).reason())
                .isEqualTo("这道题要用到它，已 38 天没答对，把两个概念记混了");
    }

    /** 05 §3.7：top1Reason 只在补漏重排后的第一条非空，其余条为 null（前端据此渲染“最优先”标记）。 */
    @Test
    void top1ReasonOnlyPresentOnFirstAfterRerank() {
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0201", 1)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of("KP0200"));
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of("KP0202"));
        when(questionBankService.listByKpIdsIncludeSecondary(anyLong(), anyList()))
                .thenReturn(List.of(question("Q_0200", 1, "KP0200"),
                        question("Q_0202", 1, "KP0202")));
        KnowledgePoint firstKp = new KnowledgePoint();
        firstKp.setKpId("KP0200");
        firstKp.setName("关系模型");
        when(repairPriorityService.rank(2L, 1L)).thenReturn(new RepairPriorityService.RankedCourse(
                List.of(firstKp),
                Map.of("KP0200", priority("KP0200", 0.9, 1),
                        "KP0201", priority("KP0201", 0.4, 0),
                        "KP0202", priority("KP0202", 0.2, -1))));
        when(repairPriorityService.explain(any(), any())).thenReturn("这道题要用到它，已 38 天没答对");
        when(repairPriorityService.firstReason(eq("关系模型"), any(), any()))
                .thenReturn("先练关系模型：这道题要用到它，先练它见效最快");

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend(null, "KP0201", 1L);
        }

        assertThat(result).extracting(Recommendation::qId)
                .containsExactly("Q_0200", "Q_0201", "Q_0202");
        assertThat(result.get(0).top1Reason())
                .isEqualTo("先练关系模型：这道题要用到它，先练它见效最快");
        assertThat(result.get(1).top1Reason()).isNull();
        assertThat(result.get(2).top1Reason()).isNull();
    }

    /**
     * 冷却窗口 cutoff 按上海时区计算：把 JVM 默认时区拨到 UTC 后，传给练习记录查询的下界
     * 仍应为上海当前时间减 7 天。把 {@code now(ZONE)} 回退成裸 {@code now()} 即红（差 8 小时）。
     */
    @Test
    void practicedCutoffUsesShanghaiZoneRegardlessOfJvmDefault() throws Exception {
        Method practicedQids = RecommendationService.class
                .getDeclaredMethod("practicedQids", long.class, long.class, int.class);
        practicedQids.setAccessible(true);

        LocalDateTime shanghaiNow;
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            shanghaiNow = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            practicedQids.invoke(service, 2L, 1L, 7);
        } finally {
            TimeZone.setDefault(original);
        }

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(practiceRecordMapper).selectPracticedQids(eq(2L), eq(1L), captor.capture());
        assertThat(Duration.between(shanghaiNow.minusDays(7), captor.getValue()).abs().getSeconds())
                .isLessThan(5);
    }

    /**
     * 并发重复生成：insert 命中唯一键 uk_user_log_q 后的回查必须发生在事务结束之后。
     * 事务内 REPEATABLE READ 快照看不到并发方已提交行（selectOne 返回 null 即等价模拟），
     * 只有事务结束后的新连接查询才能取到已落库批次。修复前直接抛 DuplicateKeyException。
     */
    @Test
    void duplicatePersistReloadsExistingBatchAfterTransactionEnds() {
        StudentErrorRecord record = new StudentErrorRecord();
        record.setLogId("EL_1");
        record.setUserId(2L);
        record.setQId("Q_0301");
        record.setKpId("KP0201");
        when(errorRecordService.getConfirmed("EL_1")).thenReturn(record);
        when(questionBankService.listByKp(1L, "KP0201"))
                .thenReturn(List.of(question("Q_0301", 1), question("Q_0302", 2)));
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of());
        when(knowledgePointService.successors("KP0201")).thenReturn(List.of());
        when(recommendationRecordMapper.insert(any(RecommendationRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_user_log_q"));
        RecommendationRecord existing = new RecommendationRecord();
        existing.setRecommendationId(9L);
        existing.setUserId(2L);
        existing.setLogId("EL_1");
        existing.setQId("Q_0302");
        existing.setLayer("CONSOLIDATE");
        existing.setReason("同一块知识，再练一道");
        existing.setRankNo(1);
        existing.setIsAdopted(1);
        // 事务内快照看不到并发提交：旧实现据此判定“不存在”并重抛
        lenient().when(recommendationRecordMapper.selectOne(any())).thenReturn(null);
        when(recommendationRecordMapper.selectList(any())).thenReturn(List.of(existing));

        List<Recommendation> result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = service.recommend("EL_1", null, 1L);
        }

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).recommendationId()).isEqualTo(9L);
        assertThat(result.get(0).isAdopted()).isTrue();
    }

    private RepairPriorityService.Priority priority(String kpId, double score, int distance) {
        return new RepairPriorityService.Priority(kpId, score, distance, distance < 0,
                1.0, 1.0, "CONCEPT_CONFUSION", 38, null);
    }

    private QuestionView question(String qId, int difficulty) {
        return question(qId, difficulty, "KP0201");
    }

    private QuestionView question(String qId, int difficulty, String kpId) {
        return new QuestionView(qId, kpId, "单选", "题干-" + qId, List.of(), difficulty, "教材");
    }
}
