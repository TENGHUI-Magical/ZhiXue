package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.ReviewPageView;
import com.zhixue.diagnosis.dto.ReviewSubmitRequest;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import com.zhixue.user.service.UserQueryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherReviewServiceTest {

    @Mock
    private AiGenerationLogMapper logMapper;
    @Mock
    private StudentErrorRecordMapper errorRecordMapper;
    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private QuestionBankService questionBankService;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private CourseService courseService;

    private TeacherReviewService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiGenerationLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new TeacherReviewService(logMapper, errorRecordMapper, knowledgePointService,
                questionBankService, userQueryService, new ObjectMapper(), courseService);
    }

    @Test
    void pageMapsReviewItemAndBatchLookups() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setExplainId("EX_1");
        log.setUserId(2L);
        log.setLogId("EL_1");
        log.setScene("RAG");
        log.setModel("deepseek-chat");
        log.setIsDegraded(0);
        log.setSourceIds("[\"KP0201\"]");
        log.setContentText("讲解正文");
        log.setCreatedAt(LocalDateTime.of(2026, 10, 28, 20, 0, 0));
        when(courseService.listActiveCourseIdsByTeacher(anyLong())).thenReturn(List.of(1L));
        when(logMapper.countForReview(any(), any(), any(), any(), any())).thenReturn(1L);
        when(logMapper.selectForReview(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(log));

        StudentErrorRecord error = new StudentErrorRecord();
        error.setLogId("EL_1");
        error.setKpId("KP0201");
        error.setErrorType("CONCEPT_CONFUSION");
        when(errorRecordMapper.selectBatchIds(any())).thenReturn(List.of(error));

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        kp.setTextbookPage("P34");
        when(knowledgePointService.findByIds(any())).thenReturn(Map.of("KP0201", kp));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "学生A"));

        ReviewPageView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            view = service.page(1, 10);
        }

        assertEquals(1, view.total());
        assertEquals(1, view.records().size());
        assertEquals("学生A", view.records().get(0).userName());
        assertEquals("KP0201", view.records().get(0).kpId());
        assertEquals("CONCEPT_CONFUSION", view.records().get(0).errorType());
        assertEquals(1, view.records().get(0).sources().size());
        assertEquals("P34", view.records().get(0).sources().get(0).textbookPage());
        assertEquals("2026-10-28 20:00:00", view.records().get(0).createdAt());
    }

    @Test
    void invalidPageOrSizeReturns40000() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BizException page = assertThrows(BizException.class, () -> service.page(0, 10));
            assertEquals(ErrorCode.PARAM_ERROR, page.getErrorCode());
            BizException size = assertThrows(BizException.class, () -> service.page(1, 51));
            assertEquals(ErrorCode.PARAM_ERROR, size.getErrorCode());
        }
    }

    /** 导出：全量查询、姓名脱敏、错因中文映射、评阅字段、BOM 与中文表头。 */
    @Test
    void exportCsvMasksNamesMapsErrorTypesAndCarriesReviewFields() {
        AiGenerationLog reviewed = new AiGenerationLog();
        reviewed.setLogPk(11L);
        reviewed.setExplainId("EX_2");
        reviewed.setUserId(3L);
        reviewed.setLogId("EL_2");
        reviewed.setScene("RAG");
        reviewed.setModel("deepseek-chat");
        reviewed.setIsDegraded(0);
        reviewed.setSourceIds("[\"KP0201\"]");
        reviewed.setContentText("正文不进导出");
        reviewed.setCreatedAt(LocalDateTime.of(2026, 10, 28, 20, 0, 0));
        reviewed.setReviewResult("PASS");
        reviewed.setTeacherScore(5);
        reviewed.setTeacherComment("讲解清晰");
        reviewed.setReviewedAt(LocalDateTime.of(2026, 10, 29, 9, 0, 0));
        when(courseService.listActiveCourseIdsByTeacher(anyLong())).thenReturn(List.of(1L));
        when(logMapper.selectForReviewAll(any(), any(), any(), any(), any()))
                .thenReturn(List.of(reviewed));

        StudentErrorRecord error = new StudentErrorRecord();
        error.setLogId("EL_2");
        error.setKpId("KP0201");
        error.setErrorType("CONCEPT_CONFUSION");
        error.setQId("Q_0201");
        when(errorRecordMapper.selectBatchIds(any())).thenReturn(List.of(error));

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        kp.setTextbookPage("P34");
        when(knowledgePointService.findByIds(any())).thenReturn(Map.of("KP0201", kp));
        com.zhixue.course.entity.QuestionBank question = new com.zhixue.course.entity.QuestionBank();
        question.setQId("Q_0201");
        question.setQuestionText("求闭包");
        when(questionBankService.findByIds(any())).thenReturn(Map.of("Q_0201", question));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(3L, "欧阳锋"));

        byte[] csv;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            csv = service.exportCsv("REVIEWED", null, null, null);
        }

        String text = new String(csv, StandardCharsets.UTF_8);
        // BOM + 中文表头
        assertEquals((byte) 0xEF, csv[0]);
        assertTrue(text.startsWith("\uFEFF留痕ID,讲解ID,学生（脱敏）,"));
        // 姓名“欧阳锋”→“欧*锋”（NameMasker 同口径）；错因映射中文；评阅字段完整
        assertTrue(text.contains("11,EX_2,欧*锋,KP0201,概念混淆"));
        assertTrue(text.contains(",通过,5,讲解清晰,2026-10-29 09:00:00"));
        // 正文是系统内可查的字段，不进学期材料导出
        assertFalse(text.contains("正文不进导出"));
        // 全量查询（无分页参数），且带登录教师的课程范围
        verify(logMapper).selectForReviewAll(any(), any(), any(), any(), eq(List.of(1L)));
    }

    /** 课程范围（队列与导出同口径）：无课程教师传哨兵 -1（看不到任何有归属的记录）；有课程教师传自己的课程集。 */
    @Test
    void reviewQueriesAreScopedToTeachersManagedCourses() {
        when(courseService.listActiveCourseIdsByTeacher(2L)).thenReturn(List.of(1L, 3L));
        when(courseService.listActiveCourseIdsByTeacher(9L)).thenReturn(List.of());
        when(logMapper.countForReview(any(), any(), any(), any(), any())).thenReturn(0L);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            service.page(1, 10, "PENDING", null, null, null);
            verify(logMapper).countForReview(any(), any(), any(), any(), eq(List.of(1L, 3L)));

            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
            service.page(1, 10, "PENDING", null, null, null);
            // 无课程教师：哨兵 -1 → NOT IN (-1) 恒真 → 有归属的记录全排除，只剩归属不明
            verify(logMapper).countForReview(any(), any(), any(), any(), eq(List.of(-1L)));
        }
    }

    /**
     * 超限行为（先 COUNT 后取数）：行数超过 MAX_EXPORT_ROWS 时拒绝并提示缩小筛选，
     * 且不进入全量取数——拒绝优先于截断，避免老师拿到静默少行的文件以为数据丢了。
     */
    @Test
    void exportCsvRejectsWhenRowCountExceedsCap() {
        when(courseService.listActiveCourseIdsByTeacher(anyLong())).thenReturn(List.of(1L));
        when(logMapper.countForReview(any(), any(), any(), any(), any()))
                .thenReturn((long) TeacherReviewService.MAX_EXPORT_ROWS + 1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.exportCsv("ALL", null, null, null));
            assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
            assertTrue(ex.getMessage().contains(String.valueOf(TeacherReviewService.MAX_EXPORT_ROWS)));
            assertTrue(ex.getMessage().contains("缩小筛选"));
        }
        verify(logMapper, never()).selectForReviewAll(any(), any(), any(), any(), any());
    }

    @Test
    void submitWritesOnlyWhenReviewedAtIsNull() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setScene("RAG");
        when(logMapper.selectById(10L)).thenReturn(log);
        when(logMapper.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            service.submit(10L, new ReviewSubmitRequest(5, "讲解清晰"));
            verify(logMapper).update(isNull(), any());
        }
    }

    @Test
    void duplicateReviewReturns40900() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setScene("RAG");
        log.setReviewedAt(LocalDateTime.now());
        when(logMapper.selectById(10L)).thenReturn(log);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(10L, new ReviewSubmitRequest(5, "讲解清晰")));
            assertEquals(ErrorCode.DUPLICATE_SUBMIT, ex.getErrorCode());
            verify(logMapper, never()).update(any(), any());
        }
    }

    @Test
    void returnWithoutCommentReturns40000() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setScene("RAG");
        when(logMapper.selectById(10L)).thenReturn(log);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(10L, new ReviewSubmitRequest(3, "  ", "RETURN")));
            assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
            verify(logMapper, never()).update(any(), any());
        }
    }
}
