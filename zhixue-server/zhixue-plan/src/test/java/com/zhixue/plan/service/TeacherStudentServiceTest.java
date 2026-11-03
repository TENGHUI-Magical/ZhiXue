package com.zhixue.plan.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.dto.UserPracticeAgg;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import com.zhixue.plan.dto.TeacherStudentViews;
import com.zhixue.plan.dto.UserPlanAgg;
import com.zhixue.plan.mapper.StudyPlanMapper;
import com.zhixue.user.service.UserQueryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherStudentServiceTest {

    @Mock
    private CourseService courseService;
    @Mock
    private QuestionBankService questionBankService;
    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private StudentErrorRecordMapper errorRecordMapper;
    @Mock
    private StudyPlanMapper studyPlanMapper;
    @Mock
    private UserQueryService userQueryService;

    private TeacherStudentService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                StudentErrorRecord.class);
    }

    @BeforeEach
    void setUp() {
        service = new TeacherStudentService(courseService, questionBankService,
                knowledgePointService, practiceRecordMapper, errorRecordMapper,
                studyPlanMapper, userQueryService, 30, 3, 180);
    }

    @Test
    void maskHandlesEdgeCases() {
        assertEquals("学生", TeacherStudentService.mask(null));
        assertEquals("学生", TeacherStudentService.mask(""));
        assertEquals("学生", TeacherStudentService.mask("  "));
        assertEquals("张", TeacherStudentService.mask("张"));
        assertEquals("张*", TeacherStudentService.mask("张三"));
        assertEquals("欧*锋", TeacherStudentService.mask("欧阳锋"));
        assertEquals("张*明", TeacherStudentService.mask("张小明"));
        assertEquals("张***人", TeacherStudentService.mask("张三丰真人"));
    }

    @Test
    void pageRejectsOtherTeacher() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(99L));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.page(1L, 1, 20, "lastActiveDesc"));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
    }

    /** 导出与列表同口径：全量（不分页）、同排序、姓名脱敏、BOM 与中文表头；越权在进入聚合前被挡。 */
    @Test
    void exportCsvExportsFullResultWithMaskingAndBom() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        List<String> qIds = List.of("Q_1", "Q_2");
        when(questionBankService.listQidsByCourse(1L)).thenReturn(qIds);
        when(practiceRecordMapper.aggregateByUser(eq(qIds), any(), any())).thenReturn(List.of(
                agg(2L, 18, 6, 5, 52.4, LocalDateTime.of(2026, 10, 30, 21, 10)),
                agg(3L, 6, 2, 2, 35.0, LocalDateTime.of(2026, 10, 28, 12, 0))));
        when(practiceRecordMapper.aggregateByUserKp(eq(qIds), any(), eq(30.0), any(), any()))
                .thenReturn(List.of(kp(2L, "KP01", 10, 4.0, 4)));
        when(studyPlanMapper.aggregateProgress(any(), any(), any())).thenReturn(List.of());
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三", 3L, "欧阳锋"));

        byte[] csv;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            csv = service.exportCsv(1L, "lastActiveDesc");
        }

        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals((byte) 0xEF, csv[0]);
        assertTrue(text.startsWith("\uFEFF学生ID,姓名（脱敏）,练习次数,正确率,"));
        // 两行全量导出（列表 size=1 分页时只有一行，导出必须完整）
        assertTrue(text.contains("2,张*,18,0.667,52,"));
        assertTrue(text.contains("3,欧*锋,6,0.667,35,"));
    }

    @Test
    void exportCsvRejectsOtherTeacherBeforeAnyQuery() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(99L));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.exportCsv(1L, "lastActiveDesc"));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
        verify(questionBankService, never()).listQidsByCourse(any());
    }

    @Test
    void pageReturnsEmptyWhenCourseHasNoQuestions() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        when(questionBankService.listQidsByCourse(1L)).thenReturn(List.of());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            TeacherStudentViews.Page result = service.page(1L, 1, 20, "lastActiveDesc");
            assertEquals(0, result.total());
            assertTrue(result.records().isEmpty());
        }
        verify(practiceRecordMapper, never()).aggregateByUser(anyList(), any(), any());
    }

    @Test
    void pageReturnsEmptyWhenNoStudents() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        List<String> qIds = List.of("Q_1");
        when(questionBankService.listQidsByCourse(1L)).thenReturn(qIds);
        when(practiceRecordMapper.aggregateByUser(eq(qIds), any(), any())).thenReturn(List.of());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            TeacherStudentViews.Page result = service.page(1L, 1, 20, "lastActiveDesc");
            assertEquals(0, result.total());
        }
    }

    @Test
    void pageAggregatesAndMasksNames() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        List<String> qIds = List.of("Q_1", "Q_2");
        when(questionBankService.listQidsByCourse(1L)).thenReturn(qIds);
        when(practiceRecordMapper.aggregateByUser(eq(qIds), any(), any())).thenReturn(List.of(
                agg(2L, 18, 6, 5, 52.4, LocalDateTime.of(2026, 10, 30, 21, 10)),
                agg(3L, 6, 2, 2, 35.0, LocalDateTime.of(2026, 10, 28, 12, 0))));
        when(practiceRecordMapper.aggregateByUserKp(eq(qIds), any(), eq(30.0), any(), any())).thenReturn(List.of(
                kp(2L, "KP01", 10, 4.0, 4),
                kp(2L, "KP02", 8, 1.0, 1),
                kp(3L, "KP01", 6, 2.0, 2)));
        when(studyPlanMapper.aggregateProgress(any(), any(), any())).thenReturn(List.of());
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三", 3L, "欧阳锋"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            TeacherStudentViews.Page result = service.page(1L, 1, 20, "lastActiveDesc");
            assertEquals(2, result.total());
            assertEquals("张*", result.records().get(0).displayName());
            assertEquals("欧*锋", result.records().get(1).displayName());
            assertEquals(0.667, result.records().get(0).correctRate(), 0.001);
            assertEquals(2, result.records().get(1).errorCount());
        }
    }

    @Test
    void pageMasteryAscSortsGloballyNotJustCurrentPage() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        List<String> qIds = List.of("Q_1");
        when(questionBankService.listQidsByCourse(1L)).thenReturn(qIds);
        when(practiceRecordMapper.aggregateByUser(eq(qIds), any(), any())).thenReturn(List.of(
                agg(3L, 10, 1, 1, 30.0, LocalDateTime.of(2026, 10, 30, 21, 0)),
                agg(2L, 10, 5, 5, 30.0, LocalDateTime.of(2026, 10, 30, 20, 0))));
        // user2 mastery=0.2；user3 mastery=0.9 → masteryAsc 应把 user2 排在前
        when(practiceRecordMapper.aggregateByUserKp(eq(qIds), any(), eq(30.0), any(), any())).thenReturn(List.of(
                kp(2L, "KP01", 10, 8.0, 5),
                kp(3L, "KP01", 10, 1.0, 1)));
        when(studyPlanMapper.aggregateProgress(any(), any(), any())).thenReturn(List.of());
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三", 3L, "欧阳锋"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            TeacherStudentViews.Page result = service.page(1L, 1, 1, "masteryAsc");
            assertEquals(2, result.total());
            assertEquals(1, result.records().size());
            assertEquals(2L, result.records().get(0).userId());
        }
    }

    @Test
    void masteryUsesPerKpWeightsNotUserLastActive() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        List<String> qIds = List.of("Q_1");
        when(questionBankService.listQidsByCourse(1L)).thenReturn(qIds);
        // 用户今天在 kpA 活跃；kpB 的证据都在 90 天前，不应被 kpA 的 lastActive 续命
        when(practiceRecordMapper.aggregateByUser(eq(qIds), any(), any())).thenReturn(List.of(
                agg(2L, 4, 1, 1, 30.0, LocalDateTime.of(2026, 10, 30, 21, 0))));
        UserKpAgg kpA = kp(2L, "KP01", 1, 0.0, 0);
        kpA.setSuccessWeight(1.0);
        UserKpAgg kpB = kp(2L, "KP02", 3, 2.125, 1);
        kpB.setSuccessWeight(0.25);
        when(practiceRecordMapper.aggregateByUserKp(eq(qIds), any(), eq(30.0), any(), any()))
                .thenReturn(List.of(kpA, kpB));
        when(studyPlanMapper.aggregateProgress(any(), any(), any())).thenReturn(List.of());
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            TeacherStudentViews.Page result = service.page(1L, 1, 20, "lastActiveDesc");
            // 手算：kpA Beta(3,2)=0.6；kpB Beta(2.25,4.125)=0.352941；宏平均=0.476471
            assertEquals(0.476, result.records().get(0).avgMastery(), 0.001);
        }
    }

    @Test
    void pageMapsPlanDoneAndTotalInContractOrder() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        List<String> qIds = List.of("Q_1");
        when(questionBankService.listQidsByCourse(1L)).thenReturn(qIds);
        when(practiceRecordMapper.aggregateByUser(eq(qIds), any(), any())).thenReturn(List.of(
                agg(2L, 4, 2, 2, 45.0, LocalDateTime.of(2026, 10, 30, 20, 0))));
        when(practiceRecordMapper.aggregateByUserKp(eq(qIds), any(), eq(30.0), any(), any())).thenReturn(List.of(
                kp(2L, "KP01", 4, 2.0, 2)));
        UserPlanAgg plan = new UserPlanAgg();
        plan.setUserId(2L);
        plan.setPlanTotal(7);
        plan.setPlanDone(2);
        when(studyPlanMapper.aggregateProgress(any(), any(), any())).thenReturn(List.of(plan));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            TeacherStudentViews.Page result = service.page(1L, 1, 20, "lastActiveDesc");
            // 契约字段顺序：planDone=2、planTotal=7；曾因 int[] 顺序写反而返回 7/2
            assertEquals(2, result.records().get(0).planDone());
            assertEquals(7, result.records().get(0).planTotal());
        }
    }

    @Test
    void learningRecordsNotFoundWhenStudentHasNoPractice() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        when(questionBankService.listQidsByCourse(1L)).thenReturn(List.of("Q_1"));
        when(practiceRecordMapper.aggregateByUser(anyList(), any(), any())).thenReturn(List.of());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.learningRecords(1L, 9L, null, null, 1, 20));
            assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        }
    }

    @Test
    void learningRecordsBuildsSummaryAndPage() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(2L));
        List<String> qIds = List.of("Q_1");
        when(questionBankService.listQidsByCourse(1L)).thenReturn(qIds);
        when(practiceRecordMapper.aggregateByUser(eq(qIds), any(), any())).thenReturn(List.of(
                agg(2L, 10, 3, 2, 40.0, LocalDateTime.of(2026, 10, 30, 20, 0))));
        when(practiceRecordMapper.aggregateByUserKp(eq(qIds), any(), eq(30.0), any(), any())).thenReturn(List.of(
                kp(2L, "KP01", 10, 3.0, 3)));
        when(practiceRecordMapper.countByUserPaged(eq(2L), eq(qIds), any(), any())).thenReturn(1L);
        PracticeRecord record = new PracticeRecord();
        record.setPracticeId(11L);
        record.setQId("Q_1");
        record.setKpId("KP01");
        record.setIsCorrect(1);
        record.setMyAnswer("A");
        record.setCostSeconds(45);
        record.setSource("PLAN");
        record.setCreatedAt(LocalDateTime.of(2026, 10, 30, 20, 0));
        when(practiceRecordMapper.selectByUserPaged(eq(2L), eq(qIds), any(), any(), eq(20), eq(0)))
                .thenReturn(List.of(record));

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP01");
        kp.setName("数据模型");
        when(knowledgePointService.findByIds(any())).thenReturn(Map.of("KP01", kp));
        QuestionBank question = new QuestionBank();
        question.setQId("Q_1");
        question.setQuestionText("题干");
        question.setAnswerText("A");
        when(questionBankService.findByIds(any())).thenReturn(Map.of("Q_1", question));
        when(courseService.listActiveKnowledgePoints(1L)).thenReturn(List.of(kp));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三"));
        when(studyPlanMapper.aggregateProgress(any(), any(), any())).thenReturn(List.of());
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            TeacherStudentViews.LearningRecord detail =
                    service.learningRecords(1L, 2L, null, null, 1, 20);
            assertEquals("张*", detail.summary().displayName());
            assertEquals(1, detail.practices().total());
            assertEquals("题干", detail.practices().records().get(0).questionText());
            assertEquals(1, detail.kpMastery().size());
            assertTrue(detail.errors().isEmpty());
            assertNull(detail.practices().records().get(0).errorType());
        }
    }

    private Course courseOwnedBy(long teacherId) {
        Course course = new Course();
        course.setCourseId(1L);
        course.setTeacherId(teacherId);
        return course;
    }

    private UserPracticeAgg agg(Long userId, int practice, int wrong, int errorQids,
                                double avgCost, LocalDateTime lastActive) {
        UserPracticeAgg agg = new UserPracticeAgg();
        agg.setUserId(userId);
        agg.setPracticeCount(practice);
        agg.setWrongCount(wrong);
        agg.setErrorQidCount(errorQids);
        agg.setAvgCostSeconds(avgCost);
        agg.setLastActiveAt(lastActive);
        return agg;
    }

    private UserKpAgg kp(Long userId, String kpId, int events, double severity, int errors) {
        UserKpAgg agg = new UserKpAgg();
        agg.setUserId(userId);
        agg.setKpId(kpId);
        agg.setEvents(events);
        // 单测默认按“今日证据”构造，successWeight = 正确事件数；需要旧证据时手动覆盖
        agg.setSuccessWeight((double) Math.max(0, events - errors));
        agg.setSeveritySum(severity);
        agg.setErrorCount(errors);
        return agg;
    }
}
