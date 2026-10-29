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
import com.zhixue.diagnosis.dto.PortraitView;
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
        return new PortraitService(snapshotMapper, practiceRecordMapper, errorRecordMapper,
                knowledgePointService, courseService, questionBankService,
                new ObjectMapper(), 30, minSamples);
    }

    private PracticeRecord practice(String kpId, String qId, int correct, String errorType,
                                    LocalDateTime at) {
        PracticeRecord record = new PracticeRecord();
        record.setKpId(kpId);
        record.setQId(qId);
        record.setUserId(2L);
        record.setIsCorrect(correct);
        record.setErrorType(errorType);
        record.setCreatedAt(at);
        return record;
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
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of());
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertNull(service.me());
        }
    }

    @Test
    void snapshotIsMappedToContractView() {
        LearningPortraitSnapshot snapshot = new LearningPortraitSnapshot();
        snapshot.setStatDate(LocalDate.of(2026, 10, 28));
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
            assertEquals(LocalDate.of(2026, 10, 28), view.statDate());
            assertEquals(0.72, view.dimensionScores().get("concept"));
            assertEquals("关系模型", view.weakestKps().get(0).name());
            assertEquals(2, view.errorDist().get("CONCEPT_CONFUSION"));
        }
    }

    @Test
    void transientPortraitComputesDimensionAndWeakKp() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(
                practice("KP0201", "Q_0201", 1, null, null),
                practice("KP0201", "Q_0202", 0, "CONCEPT_CONFUSION", null),
                practice("KP0201", "Q_0203", 0, "CONCEPT_CONFUSION", null)));

        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of(new CourseView(1L, "数据库原理",
                null, null, List.of(new CourseView.DimensionDef("concept", "概念理解")))));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = service.me();
            assertEquals(3, view.totalPractice());
            assertEquals(2, view.totalError());
            // 1 - 2×1.0/3 = 0.333（时间缺失视为不衰减）
            assertEquals(0.333, view.dimensionScores().get("concept"));
            assertEquals(1, view.weakestKps().size());
            assertEquals(0.333, view.weakestKps().get(0).mastery());
            assertEquals(2, view.errorDist().get("CONCEPT_CONFUSION"));
        }
    }

    @Test
    void confirmedUploadWithoutPracticeIsMergedAsOneErrorEvent() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(
                practice("KP0201", "Q_0201", 1, null, null)));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of(
                confirmed("KP0201", "Q_0999", "CONCEPT_CONFUSION", null)));
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = newService(1).me();
            // 事件 2 条：1 对 + 1 错（严重度 1.0），掌握度 1 - 1/2 = 0.5
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
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(
                practice("KP0201", "Q_0201", 0, "CONCEPT_CONFUSION", null)));
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
            assertEquals(0.0, view.weakestKps().get(0).mastery());
        }
    }

    @Test
    void oldErrorDecaysByHalfLife() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        LocalDateTime now = LocalDateTime.now(ZONE);
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(
                practice("KP0201", "Q_0201", 1, null, now),
                practice("KP0201", "Q_0202", 0, "CONCEPT_CONFUSION", now.minusDays(30).minusHours(1))));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = newService(1).me();
            // 1 - 0.5×1.0/2 = 0.75（满 30 个整天 → 半衰）
            assertEquals(0.75, view.weakestKps().get(0).mastery());
        }
    }

    @Test
    void severityDiffersByErrorType() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(
                practice("KP0201", "Q_0201", 0, "CALCULATION_ERROR", null),
                practice("KP0201", "Q_0202", 1, null, null)));
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));
        when(courseService.listActive()).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = newService(1).me();
            // 计算失误权重 0.6：1 - 0.6/2 = 0.7
            assertEquals(0.7, view.weakestKps().get(0).mastery());
        }
    }

    @Test
    void classPortraitUsesPerStudentMacroAverage() {
        Course course = new Course();
        course.setCourseId(1L);
        course.setTeacherId(2L);
        when(courseService.getActiveById(1L)).thenReturn(course);
        when(questionBankService.listQidsByCourse(1L)).thenReturn(List.of("Q_0201", "Q_0202"));

        PracticeRecord heavy = practice("KP0201", "Q_0201", 0, "CONCEPT_CONFUSION", null);
        heavy.setUserId(10L);
        List<PracticeRecord> records = new java.util.ArrayList<>();
        records.add(heavy);
        for (int i = 0; i < 9; i++) {
            PracticeRecord correct = practice("KP0201", "Q_0202", 1, null, null);
            correct.setUserId(10L);
            records.add(correct);
        }
        PracticeRecord light = practice("KP0201", "Q_0203", 0, "CONCEPT_CONFUSION", null);
        light.setUserId(11L);
        records.add(light);
        when(practiceRecordMapper.selectList(any())).thenReturn(records);
        when(knowledgePointService.findByIds(any()))
                .thenReturn(Map.of("KP0201", kp("KP0201", "关系模型", "concept")));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            ClassPortraitView view = service.classPortrait(1L);
            // 学生 10：1 - 1/10 = 0.9；学生 11：1 - 1/1 = 0；宏平均 0.45（池化会得到 0.818）
            assertEquals(0.45, view.weakestKpTop().get(0).avgMastery());
            assertEquals(2, view.weakestKpTop().get(0).errorCount());
            assertEquals(2, view.studentCount());
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
