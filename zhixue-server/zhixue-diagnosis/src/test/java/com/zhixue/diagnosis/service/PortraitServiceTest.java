package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.CourseView;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.ClassPortraitView;
import com.zhixue.diagnosis.dto.ErrorTypeAgg;
import com.zhixue.diagnosis.dto.PortraitView;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.dto.UserPracticeAgg;
import com.zhixue.diagnosis.entity.LearningPortraitSnapshot;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.LearningPortraitSnapshotMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortraitServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private LearningPortraitSnapshotMapper snapshotMapper;
    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private StudentErrorRecordMapper errorRecordMapper;
    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private CourseService courseService;
    @Mock
    private QuestionBankService questionBankService;

    private PortraitService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entity : List.of(LearningPortraitSnapshot.class, PracticeRecord.class,
                StudentErrorRecord.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = newService(3);
    }

    private PortraitService newService(int minSamples) {
        // 唯一聚合入口用真实实现 + mock mapper，保证测试走的就是生产聚合链路
        return new PortraitService(snapshotMapper, practiceRecordMapper,
                new MasteryAggregationService(practiceRecordMapper, errorRecordMapper, 30, 180),
                knowledgePointService, courseService, questionBankService,
                new ObjectMapper(), minSamples, 180);
    }

    /**
     * 构造单学生单知识点的聚合行（模拟 SQL 聚合输出，含按事件时间衰减后的权重）。
     * 画像服务只做 Beta 后验与维度加权，时间衰减由 SQL 完成——
     * 该口径由 IT（classPortrait...HalfLife / teacherAndStudentMasteryAgreePerKp）在真库上锁定。
     */
    private UserKpAgg kpAgg(String kpId, int events, int errors,
                            double successWeight, double severitySum) {
        UserKpAgg agg = new UserKpAgg();
        agg.setUserId(2L);
        agg.setKpId(kpId);
        agg.setEvents(events);
        agg.setErrorCount(errors);
        agg.setSuccessWeight(successWeight);
        agg.setSeveritySum(severitySum);
        return agg;
    }

    private UserPracticeAgg totals(int practiceCount, int wrongCount) {
        UserPracticeAgg agg = new UserPracticeAgg();
        agg.setUserId(2L);
        agg.setPracticeCount(practiceCount);
        agg.setWrongCount(wrongCount);
        return agg;
    }

    private void mockAggregates(List<UserKpAgg> kpAggs, List<ErrorTypeAgg> errorAggs,
                                UserPracticeAgg userTotals, List<String> practicedQids) {
        when(practiceRecordMapper.aggregateByUserKp(isNull(), anyList(), anyDouble(), any(),
                any())).thenReturn(kpAggs);
        when(practiceRecordMapper.aggregateErrorType(isNull(), any(), any())).thenReturn(errorAggs);
        when(practiceRecordMapper.aggregateByUser(isNull(), anyList(), any()))
                .thenReturn(userTotals == null ? List.of() : List.of(userTotals));
        when(practiceRecordMapper.selectDistinctQidsByUser(2L)).thenReturn(practicedQids);
    }

    private ErrorTypeAgg errorAgg(String type, int cnt) {
        ErrorTypeAgg agg = new ErrorTypeAgg();
        agg.setErrorType(type);
        agg.setCnt(cnt);
        return agg;
    }

    private StudentErrorRecord confirmed(String kpId, String qId, String errorType,
                                         LocalDateTime at) {
        StudentErrorRecord record = new StudentErrorRecord();
        record.setKpId(kpId);
        record.setQId(qId);
        record.setUserId(2L);
        record.setStatus("CONFIRMED");
        record.setErrorType(errorType);
        record.setConfirmedAt(at);
        // 生产数据 created_at 必非空；同为空时聚合会跳过该条（无法做自然日去重）
        record.setCreatedAt(at != null ? at : LocalDateTime.now(ZONE));
        return record;
    }

    private KnowledgePoint kp(String kpId, String name, String dimensionKey) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId(kpId);
        kp.setName(name);
        kp.setDimensionKey(dimensionKey);
        return kp;
    }

    @Test
    void noSnapshotAndNoPracticeReturnsNull() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        mockAggregates(List.of(), List.of(), null, List.of());
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertNull(service.me());
        }
    }

    @Test
    void snapshotIsMappedToContractView() {
        LearningPortraitSnapshot snapshot = new LearningPortraitSnapshot();
        snapshot.setStatDate(LocalDate.now()); // P0 修复：新鲜度门限要求快照在 3 天内
        snapshot.setDimensionScores("{\"concept\":0.72}");
        snapshot.setWeakestKps("[{\"kpId\":\"KP0201\",\"mastery\":0.4,\"sampleCount\":3}]");
        snapshot.setErrorDist("{\"CONCEPT_CONFUSION\":2}");
        snapshot.setTotalPractice(5);
        snapshot.setTotalError(2);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);

        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = service.me();
            assertEquals(LocalDate.now(), view.statDate());
            assertEquals(0.72, view.dimensionScores().get("concept"));
            assertEquals("关系模型", view.weakestKps().get(0).name());
            assertEquals(2, view.errorDist().get("CONCEPT_CONFUSION"));
        }
    }

    @Test
    void transientPortraitComputesDimensionAndWeakKp() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        // SQL 聚合口径：3 事件（1 对 2 错，今日）→ sw=1.0、sev=2.0（概念混淆 1.0×2）
        mockAggregates(List.of(kpAgg("KP0201", 3, 2, 1.0, 2.0)),
                List.of(errorAgg("CONCEPT_CONFUSION", 2)),
                totals(3, 2), List.of("Q_0201", "Q_0202", "Q_0203"));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());

        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of(new CourseView(1L, "数据库原理",
                null, null, List.of(new CourseView.DimensionDef("concept", "概念理解")))));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = service.me();
            assertEquals(3, view.totalPractice());
            assertEquals(2, view.totalError());
            // 手算：Beta(2+1, 2+2) 均值 = 3/7 ≈ 0.429
            assertEquals(0.429, view.dimensionScores().get("concept"));
            assertEquals(1, view.weakestKps().size());
            assertEquals(0.429, view.weakestKps().get(0).mastery());
            assertEquals(2, view.errorDist().get("CONCEPT_CONFUSION"));
        }
    }

    @Test
    void confirmedUploadWithoutPracticeIsMergedAsOneErrorEvent() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        // 练习侧聚合：1 对（今日）→ sw=1.0；错题 Q_0999 无对应练习，将补一条错误事件
        mockAggregates(List.of(kpAgg("KP0201", 1, 0, 1.0, 0.0)),
                List.of(), totals(1, 0), List.of("Q_0201"));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of(
                confirmed("KP0201", "Q_0999", "CONCEPT_CONFUSION", null)));
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = newService(1).me();
            // 手算：补 1 条错（sev +1.0）后 Beta(2+1, 2+1) = 3/6 = 0.5
            assertEquals(0.5, view.weakestKps().get(0).mastery());
            assertEquals(2, view.weakestKps().get(0).sampleCount());
            assertEquals(2, view.totalPractice());
            assertEquals(1, view.totalError());
            assertEquals(1, view.errorDist().get("CONCEPT_CONFUSION"));
        }
    }

    @Test
    void errorUploadedTwiceIsCountedOnce() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        // 练习侧已含 Q_0201 的错误；错题同 qId → 跳过，不重复计入
        mockAggregates(List.of(kpAgg("KP0201", 1, 1, 0.0, 1.0)),
                List.of(errorAgg("CONCEPT_CONFUSION", 1)), totals(1, 1), List.of("Q_0201"));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of(
                confirmed("KP0201", "Q_0201", "CONCEPT_CONFUSION", null)));
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = newService(1).me();
            assertEquals(1, view.weakestKps().get(0).sampleCount());
            assertEquals(1, view.totalError());
            // 手算：单条错误 + 无正确证据 → Beta(2, 3) = 0.4
            assertEquals(0.4, view.weakestKps().get(0).mastery());
        }
    }

    @Test
    void oldErrorDecaysByHalfLife() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        // SQL 半衰后的聚合：今日 1 对（sw=1.0）+ 30 天前的错误（sev=1.0×0.5=0.5）。
        // 半衰由 SQL 完成，此处给聚合结果；真库口径由 IT classPortrait...HalfLife 锁定
        mockAggregates(List.of(kpAgg("KP0201", 2, 1, 1.0, 0.5)),
                List.of(errorAgg("CONCEPT_CONFUSION", 1)), totals(2, 1), List.of("Q_0201", "Q_0202"));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = newService(1).me();
            // 手算：Beta(2+1, 2+0.5) = 3/5.5 ≈ 0.545（错误衰减后不再逼近 1.0）
            assertEquals(0.545, view.weakestKps().get(0).mastery());
        }
    }

    @Test
    void severityDiffersByErrorType() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        // SQL 聚合：1 错（计算失误 sev=0.6）+ 1 对（sw=1.0）
        mockAggregates(List.of(kpAgg("KP0201", 2, 1, 1.0, 0.6)),
                List.of(errorAgg("CALCULATION_ERROR", 1)), totals(2, 1), List.of("Q_0201", "Q_0202"));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = newService(1).me();
            // 手算：计算失误权重 0.6 → Beta(2+1, 2+0.6) = 3/5.6 ≈ 0.536
            assertEquals(0.536, view.weakestKps().get(0).mastery());
        }
    }

    @Test
    void classPortraitUsesPerStudentMacroAverage() {
        Course course = new Course();
        course.setCourseId(1L);
        course.setTeacherId(2L);
        when(courseService.getActiveById(1L)).thenReturn(course);
        when(questionBankService.listQidsByCourse(1L)).thenReturn(List.of("Q_0201", "Q_0202"));

        UserPracticeAgg s10 = new UserPracticeAgg();
        s10.setUserId(10L);
        s10.setPracticeCount(10);
        s10.setLastActiveAt(LocalDateTime.now(ZONE));
        UserPracticeAgg s11 = new UserPracticeAgg();
        s11.setUserId(11L);
        s11.setPracticeCount(1);
        s11.setLastActiveAt(LocalDateTime.now(ZONE));
        when(practiceRecordMapper.aggregateByUser(anyList(), any(), any()))
                .thenReturn(List.of(s10, s11));

        UserKpAgg k10 = new UserKpAgg();
        k10.setUserId(10L);
        k10.setKpId("KP0201");
        k10.setEvents(10);
        k10.setErrorCount(1);
        k10.setSuccessWeight(9.0);
        k10.setSeveritySum(1.0);
        UserKpAgg k11 = new UserKpAgg();
        k11.setUserId(11L);
        k11.setKpId("KP0201");
        k11.setEvents(1);
        k11.setErrorCount(1);
        k11.setSuccessWeight(0.0);
        k11.setSeveritySum(1.0);
        when(practiceRecordMapper.aggregateByUserKp(anyList(), any(), anyDouble(), any(), any()))
                .thenReturn(List.of(k10, k11));
        ErrorTypeAgg confusion = new ErrorTypeAgg();
        confusion.setErrorType("CONCEPT_CONFUSION");
        confusion.setCnt(2);
        when(practiceRecordMapper.aggregateErrorType(anyList(), any(), any()))
                .thenReturn(List.of(confusion));

        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            ClassPortraitView view = service.classPortrait(1L);
            // 分母是单学生样本量：学生 10（10 次）计入，Beta(2+9, 2+1)≈0.786；
            // 学生 11（1 次 < 3）不计入宏平均与错题数
            assertEquals(0.786, view.weakestKpTop().get(0).avgMastery());
            assertEquals(1, view.weakestKpTop().get(0).errorCount());
            assertEquals(2, view.studentCount());
            assertEquals(1.0, view.activeRate());
            // errorDist 走 SQL 聚合：只统计标注知识点且作答错误的记录
            assertEquals(2, view.errorDist().get("CONCEPT_CONFUSION").intValue());
        }
    }

    /**
     * 边界（docs/05 §2.4）：班级薄弱点分母是单学生 N_k >= min-samples，不是全班总事件数。
     * 3 个学生各答 1 次（全班共 3 次）不满足任一学生 N_k>=3 → 不应进入薄弱点。
     * 旧实现按全班总事件 3 次放行，会把“没练过”的抽样噪声当成班级漏洞。
     */
    @Test
    void classWeakPointRequiresPerStudentSampleNotClassTotal() {
        Course course = new Course();
        course.setCourseId(1L);
        course.setTeacherId(2L);
        when(courseService.getActiveById(1L)).thenReturn(course);
        when(questionBankService.listQidsByCourse(1L)).thenReturn(List.of("Q_0201"));

        List<UserPracticeAgg> totals = new java.util.ArrayList<>();
        List<UserKpAgg> kpAggs = new java.util.ArrayList<>();
        for (long userId : List.of(10L, 11L, 12L)) {
            UserPracticeAgg total = new UserPracticeAgg();
            total.setUserId(userId);
            total.setPracticeCount(1);
            total.setLastActiveAt(LocalDateTime.now(ZONE));
            totals.add(total);

            UserKpAgg agg = new UserKpAgg();
            agg.setUserId(userId);
            agg.setKpId("KP0201");
            agg.setEvents(1);
            agg.setErrorCount(1);
            agg.setSuccessWeight(0.0);
            agg.setSeveritySum(1.0);
            kpAggs.add(agg);
        }
        when(practiceRecordMapper.aggregateByUser(anyList(), any(), any())).thenReturn(totals);
        when(practiceRecordMapper.aggregateByUserKp(anyList(), any(), anyDouble(), any(), any()))
                .thenReturn(kpAggs);
        when(practiceRecordMapper.aggregateErrorType(anyList(), any(), any()))
                .thenReturn(List.of(errorAgg("CONCEPT_CONFUSION", 3)));
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            ClassPortraitView view = service.classPortrait(1L);
            assertEquals(3, view.studentCount());
            assertEquals(0, view.weakestKpTop().size());
        }
    }

    @Test
    void classPortraitRejectsOtherTeacher() {
        Course course = new Course();
        course.setCourseId(1L);
        course.setTeacherId(99L);
        when(courseService.getActiveById(1L)).thenReturn(course);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.classPortrait(1L));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
    }
}
