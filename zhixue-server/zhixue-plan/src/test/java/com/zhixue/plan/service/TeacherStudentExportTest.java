package com.zhixue.plan.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.csv.CsvExportFile;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.dto.UserPracticeAgg;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import com.zhixue.plan.mapper.StudyPlanMapper;
import com.zhixue.user.service.UserQueryService;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherStudentExportTest {

    @Mock private CourseService courseService;
    @Mock private QuestionBankService questionBankService;
    @Mock private KnowledgePointService knowledgePointService;
    @Mock private PracticeRecordMapper practiceRecordMapper;
    @Mock private StudentErrorRecordMapper errorRecordMapper;
    @Mock private StudyPlanMapper studyPlanMapper;
    @Mock private UserQueryService userQueryService;

    private TeacherStudentService service;

    @BeforeEach
    void setUp() {
        service = new TeacherStudentService(courseService, questionBankService,
                knowledgePointService, practiceRecordMapper, errorRecordMapper,
                studyPlanMapper, userQueryService, 30, 3, 180);
    }

    @Test
    void exportsAllAnswersWithStudentKeysMasteryAndAudit() throws Exception {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(7L));
        when(questionBankService.listQidsByCourse(1L)).thenReturn(List.of("Q_2", "Q_3"));
        when(practiceRecordMapper.selectForCourseExport(eq(1L), any(LocalDateTime.class))).thenReturn(List.of(
                practice(21L, 2L, "Q_2", 0, "CONCEPT_CONFUSION"),
                practice(31L, 3L, "Q_3", 1, null),
                practice(32L, 3L, "Q_3", 0, "STEP_OMISSION")));
        when(practiceRecordMapper.aggregateByUser(any(), any(), any())).thenReturn(List.of(
                aggregate(2L, 1), aggregate(3L, 2)));
        when(practiceRecordMapper.aggregateByUserKp(any(), any(), eq(30.0), any(), any()))
                .thenReturn(List.of(kp(2L, 1.0, 1.0), kp(3L, 2.0, 0.0)));
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP01");
        kp.setName("关系模型");
        when(knowledgePointService.findByIds(any())).thenReturn(Map.of("KP01", kp));
        when(userQueryService.findUsernames(any())).thenReturn(Map.of(2L, "20230002", 3L, "20230003"));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三", 3L, "李四"));

        Logger logger = (Logger) LoggerFactory.getLogger(TeacherStudentService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        CsvExportFile file;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            file = service.exportCsv(1L, "practiceDesc");
        } finally {
            logger.detachAppender(appender);
        }

        String[] lines = new String(file.content(), StandardCharsets.UTF_8).split("\\r\\n");
        assertEquals(5, lines.length, "两名学生三条作答必须全部导出，而非只导当前页");
        assertEquals("studentKey,maskedName,courseId,practiceId,qId,kpId,kpName,mastery,errorType,isCorrect,createdAt",
                lines[1]);
        assertTrue(lines[2].startsWith("20230003,李*,1,31,Q_3,KP01,关系模型,0.667,,true,"));
        assertTrue(lines[3].startsWith("20230003,李*,1,32,Q_3,KP01,关系模型,0.667,STEP_OMISSION,false,"));
        assertTrue(lines[4].startsWith("20230002,张*,1,21,Q_2,KP01,关系模型,0.5,CONCEPT_CONFUSION,false,"));
        assertEquals(3, file.rowCount());
        assertTrue(file.filterSummary().contains("courseId=1"));
        assertTrue(file.filterSummary().contains("sort=practiceDesc"));
        assertTrue(file.filterSummary().contains("windowDays=180"));
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(practiceRecordMapper).selectForCourseExport(eq(1L), since.capture());
        verify(practiceRecordMapper).aggregateByUser(any(), any(), since.capture());
        verify(practiceRecordMapper).aggregateByUserKp(any(), any(), eq(30.0), any(), since.capture());
        assertEquals(3, since.getAllValues().size());
        assertEquals(since.getAllValues().get(0), since.getAllValues().get(1));
        assertEquals(since.getAllValues().get(0), since.getAllValues().get(2));
        Select select = PracticeRecordMapper.class.getMethod("selectForCourseExport",
                long.class, LocalDateTime.class).getAnnotation(Select.class);
        assertTrue(String.join(" ", select.value()).contains("pr.created_at >= #{since}"));
        verify(practiceRecordMapper, never()).selectByUserPaged(
                anyLong(), any(), any(), any(), anyInt(), anyInt());
        assertEquals(1, appender.list.size());
        String audit = appender.list.get(0).getFormattedMessage();
        assertTrue(audit.contains("TEACHER_EXPORT_AUDIT"));
        assertTrue(audit.contains("teacherUserId=7"));
        assertTrue(audit.contains("endpoint=/api/teacher/students/export"));
        assertTrue(audit.contains("rowCount=3"));
    }

    @Test
    void courseForbiddenDoesNotProduceCsvOrAudit() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(99L));
        Logger logger = (Logger) LoggerFactory.getLogger(TeacherStudentService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.exportCsv(1L, "lastActiveDesc"));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        } finally {
            logger.detachAppender(appender);
        }
        verify(practiceRecordMapper, never()).selectForCourseExport(anyLong(), any());
        assertTrue(appender.list.isEmpty());
    }

    private Course courseOwnedBy(long teacherId) {
        Course course = new Course();
        course.setCourseId(1L);
        course.setTeacherId(teacherId);
        return course;
    }

    private PracticeRecord practice(long id, long userId, String qId, int correct, String errorType) {
        PracticeRecord row = new PracticeRecord();
        row.setPracticeId(id);
        row.setUserId(userId);
        row.setQId(qId);
        row.setKpId("KP01");
        row.setIsCorrect(correct);
        row.setErrorType(errorType);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0).minusMinutes(id));
        return row;
    }

    private UserPracticeAgg aggregate(long userId, int count) {
        UserPracticeAgg row = new UserPracticeAgg();
        row.setUserId(userId);
        row.setPracticeCount(count);
        row.setLastActiveAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return row;
    }

    private UserKpAgg kp(long userId, double success, double severity) {
        UserKpAgg row = new UserKpAgg();
        row.setUserId(userId);
        row.setKpId("KP01");
        row.setSuccessWeight(success);
        row.setSeveritySum(severity);
        return row;
    }
}
