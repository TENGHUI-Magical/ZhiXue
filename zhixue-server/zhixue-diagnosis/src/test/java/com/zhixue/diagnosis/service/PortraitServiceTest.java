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
import com.zhixue.diagnosis.dto.PortraitView;
import com.zhixue.diagnosis.entity.LearningPortraitSnapshot;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.mapper.LearningPortraitSnapshotMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortraitServiceTest {

    @Mock
    private LearningPortraitSnapshotMapper snapshotMapper;
    @Mock
    private PracticeRecordMapper practiceRecordMapper;
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
        for (Class<?> entity : List.of(LearningPortraitSnapshot.class, PracticeRecord.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = new PortraitService(snapshotMapper, practiceRecordMapper,
                knowledgePointService, courseService, questionBankService, new ObjectMapper());
    }

    @Test
    void noSnapshotAndNoPracticeReturnsNull() {
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of());
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

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        when(knowledgePointService.findByIds(any())).thenReturn(Map.of("KP0201", kp));

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
        PracticeRecord correct = new PracticeRecord();
        correct.setKpId("KP0201");
        correct.setQId("Q_0201");
        correct.setIsCorrect(1);
        PracticeRecord wrong1 = new PracticeRecord();
        wrong1.setKpId("KP0201");
        wrong1.setQId("Q_0202");
        wrong1.setIsCorrect(0);
        wrong1.setErrorType("CONCEPT_CONFUSION");
        PracticeRecord wrong2 = new PracticeRecord();
        wrong2.setKpId("KP0201");
        wrong2.setQId("Q_0203");
        wrong2.setIsCorrect(0);
        wrong2.setErrorType("CONCEPT_CONFUSION");
        when(practiceRecordMapper.selectList(any())).thenReturn(List.of(correct, wrong1, wrong2));

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        kp.setDimensionKey("concept");
        when(knowledgePointService.findByIds(any())).thenReturn(Map.of("KP0201", kp));
        when(courseService.listActive()).thenReturn(List.of(new CourseView(1L, "数据库原理",
                null, null, List.of(new CourseView.DimensionDef("concept", "概念理解")))));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PortraitView view = service.me();
            assertEquals(3, view.totalPractice());
            assertEquals(2, view.totalError());
            assertEquals(0.333, view.dimensionScores().get("concept"));
            assertEquals(1, view.weakestKps().size());
            assertEquals(2, view.errorDist().get("CONCEPT_CONFUSION"));
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
            BizException ex = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                    () -> service.classPortrait(1L));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
    }
}
