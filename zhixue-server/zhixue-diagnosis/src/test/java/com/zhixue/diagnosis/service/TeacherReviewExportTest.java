package com.zhixue.diagnosis.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.csv.CsvExportFile;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import com.zhixue.user.service.UserQueryService;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherReviewExportTest {

    @Mock private AiGenerationLogMapper logMapper;
    @Mock private StudentErrorRecordMapper errorRecordMapper;
    @Mock private CourseService courseService;
    @Mock private KnowledgePointService knowledgePointService;
    @Mock private QuestionBankService questionBankService;
    @Mock private UserQueryService userQueryService;

    private TeacherReviewService service;

    @BeforeEach
    void setUp() {
        service = new TeacherReviewService(logMapper, errorRecordMapper, courseService,
                knowledgePointService, questionBankService, userQueryService, new ObjectMapper());
    }

    @Test
    void exportsEveryFilteredReviewWithEvidenceFieldsAndAudit() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 11, 3);
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(7L));
        when(logMapper.selectForReviewExport(eq(1L), isNull(), eq("KP01"),
                eq(from.atStartOfDay()), eq(to.atTime(23, 59, 59))))
                .thenReturn(List.of(reviewLog(10L, 2L), reviewLog(11L, 3L)));
        when(errorRecordMapper.selectBatchIds(any())).thenReturn(List.of(
                error("EL_10", "Q_10"), error("EL_11", "Q_11")));
        when(questionBankService.findByIds(any())).thenReturn(Map.of(
                "Q_10", question("Q_10", 1L), "Q_11", question("Q_11", 1L)));
        when(userQueryService.findUsernames(any())).thenReturn(Map.of(2L, "S002", 3L, "S003"));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三", 3L, "欧阳锋"));

        Logger logger = (Logger) LoggerFactory.getLogger(TeacherReviewService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        CsvExportFile file;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            file = service.exportCsv(1L, "all", "KP01", from, to);
        } finally {
            logger.detachAppender(appender);
        }

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("courseId=1;status=ALL;kpId=KP01;from=2026-10-01;to=2026-11-03"));
        assertTrue(csv.contains("logPk,explainId,studentKey,maskedName,qId,kpId,errorType,reviewResult,teacherScore,teacherComment,reviewedAt,model,degraded,createdAt\r\n"));
        assertTrue(csv.contains("10,EX_10,S002,张*,Q_10,KP01,CONCEPT_CONFUSION,PASS,5,\"讲解清晰\n第二行\",2026-11-02 10:00:00,deepseek-chat,false,2026-11-01 10:00:00"));
        assertTrue(csv.contains("11,EX_11,S003,欧*锋,Q_11,KP01,CONCEPT_CONFUSION,PASS,5,\"讲解清晰\n第二行\",2026-11-02 10:00:00,deepseek-chat,false,2026-11-01 10:00:00"));
        assertEquals(2, file.rowCount());
        verify(logMapper).selectForReviewExport(eq(1L), isNull(), eq("KP01"),
                eq(from.atStartOfDay()), eq(to.atTime(23, 59, 59)));
        verify(logMapper, never()).selectForReview(any(), any(), any(), any(), any(Integer.class), any(Integer.class));
        verify(logMapper, never()).countForReview(any(), any(), any(), any());
        assertEquals(1, appender.list.size());
        String audit = appender.list.get(0).getFormattedMessage();
        assertTrue(audit.contains("TEACHER_EXPORT_AUDIT"));
        assertTrue(audit.contains("teacherUserId=7"));
        assertTrue(audit.contains("endpoint=/api/teacher/reviews/export"));
        assertTrue(audit.contains("courseId=1"));
        assertTrue(audit.contains("rowCount=2"));
    }

    @Test
    void excludesOtherCourseAndUnreliableEvidenceFromCsvAndSql() throws Exception {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(7L));
        when(logMapper.selectForReviewExport(eq(1L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(reviewLog(10L, 2L), reviewLog(99L, 3L), reviewLog(98L, 4L)));
        when(errorRecordMapper.selectBatchIds(any())).thenReturn(List.of(
                error("EL_10", "Q_10"), error("EL_99", "Q_99"), error("EL_98", "Q_MISSING")));
        when(questionBankService.findByIds(any())).thenReturn(Map.of(
                "Q_10", question("Q_10", 1L), "Q_99", question("Q_99", 2L)));
        when(userQueryService.findUsernames(any())).thenReturn(Map.of(2L, "S002"));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "张三"));

        CsvExportFile file;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            file = service.exportCsv(1L, "ALL", null, null, null);
        }
        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("EX_10"));
        assertFalse(csv.contains("EX_99"), "他课程记录不得进入本人课程 CSV");
        assertFalse(csv.contains("EX_98"), "缺少可靠课程证据的旧日志必须保守排除");
        assertEquals(1, file.rowCount());
        verify(logMapper).selectForReviewExport(eq(1L), isNull(), isNull(), isNull(), isNull());

        Select select = AiGenerationLogMapper.class.getMethod("selectForReviewExport",
                long.class, Boolean.class, String.class, LocalDateTime.class, LocalDateTime.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value());
        assertTrue(sql.contains("JOIN question_bank qb ON qb.q_id = e.q_id"));
        assertTrue(sql.contains("qb.course_id = #{courseId}"));
        assertTrue(sql.contains("AND e.kp_id = #{kpId}"));
    }

    @Test
    void otherTeacherCourseIsForbiddenBeforeQueryOrAudit() {
        when(courseService.getActiveById(1L)).thenReturn(courseOwnedBy(99L));
        Logger logger = (Logger) LoggerFactory.getLogger(TeacherReviewService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.exportCsv(1L, "PENDING", null, null, null));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        } finally {
            logger.detachAppender(appender);
        }
        verify(logMapper, never()).selectForReviewExport(anyLong(), any(), any(), any(), any());
        assertTrue(appender.list.isEmpty());
    }

    @Test
    void nonTeacherForbiddenDoesNotQueryOrProduceAudit() {
        Logger logger = (Logger) LoggerFactory.getLogger(TeacherReviewService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.checkRole("TEACHER"))
                    .thenThrow(new BizException(ErrorCode.FORBIDDEN, "非教师"));
            BizException ex = assertThrows(BizException.class,
                    () -> service.exportCsv(1L, "PENDING", null, null, null));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        } finally {
            logger.detachAppender(appender);
        }
        verify(logMapper, never()).selectForReviewExport(anyLong(), any(), any(), any(), any());
        assertTrue(appender.list.isEmpty());
    }

    private Course courseOwnedBy(long teacherId) {
        Course course = new Course();
        course.setCourseId(1L);
        course.setTeacherId(teacherId);
        return course;
    }

    private QuestionBank question(String qId, long courseId) {
        QuestionBank question = new QuestionBank();
        question.setQId(qId);
        question.setCourseId(courseId);
        return question;
    }

    private AiGenerationLog reviewLog(long logPk, long userId) {
        AiGenerationLog row = new AiGenerationLog();
        row.setLogPk(logPk);
        row.setExplainId("EX_" + logPk);
        row.setUserId(userId);
        row.setLogId("EL_" + logPk);
        row.setModel("deepseek-chat");
        row.setIsDegraded(0);
        row.setReviewResult("PASS");
        row.setTeacherScore(5);
        row.setTeacherComment("讲解清晰\n第二行");
        row.setReviewedAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        row.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return row;
    }

    private StudentErrorRecord error(String logId, String qId) {
        StudentErrorRecord row = new StudentErrorRecord();
        row.setLogId(logId);
        row.setQId(qId);
        row.setKpId("KP01");
        row.setErrorType("CONCEPT_CONFUSION");
        return row;
    }
}
