package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        when(logMapper.selectForReviewExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(reviewed));

        StudentErrorRecord error = new StudentErrorRecord();
        error.setLogId("EL_2");
        error.setKpId("KP0201");
        error.setErrorType("CONCEPT_CONFUSION");
        error.setQId("Q_0201");
        when(errorRecordMapper.selectBatchIds(any())).thenReturn(List.of(error));

        // 导出只取 CSV 列，不再构造含正文的视图，故不查询知识点详情/教材页
        com.zhixue.course.entity.QuestionBank question = new com.zhixue.course.entity.QuestionBank();
        question.setQId("Q_0201");
        question.setQuestionText("求闭包");
        when(questionBankService.findByIds(any())).thenReturn(Map.of("Q_0201", question));
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(3L, "欧阳锋"));

        byte[] csv;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            csv = renderCsv(service.exportCsv("REVIEWED", null, null, null));
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
        verify(logMapper).selectForReviewExport(any(), any(), any(), any(), eq(List.of(1L)));
    }

    /** 课程范围（队列与导出同口径）：无课程教师传哨兵 -1；有课程教师传自己的课程集。 */
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
            // 无课程教师：哨兵 -1 + “可归属”守卫 → 有归属与归属不明的记录都排除
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
        verify(logMapper, never()).selectForReviewExport(any(), any(), any(), any(), any());
    }

    /** 同快照不能只靠数据库默认隔离级别：显式声明 REPEATABLE_READ，删掉注解参数即红。 */
    @Test
    void exportCsvDeclaresRepeatableReadIsolation() throws NoSuchMethodException {
        Transactional tx = TeacherReviewService.class
                .getMethod("exportCsv", String.class, String.class, LocalDate.class, LocalDate.class)
                .getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertTrue(tx.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, tx.isolation());
    }

    /** 上限边界放行：恰好 50,000 行必须导出成功，拒绝只发生在 50,001 行。 */
    @Test
    void exportCsvAllowsExactlyAtRowCap() {
        when(courseService.listActiveCourseIdsByTeacher(anyLong())).thenReturn(List.of(1L));
        when(logMapper.countForReview(any(), any(), any(), any(), any()))
                .thenReturn((long) TeacherReviewService.MAX_EXPORT_ROWS);
        List<AiGenerationLog> rows = new ArrayList<>(TeacherReviewService.MAX_EXPORT_ROWS);
        for (int i = 1; i <= TeacherReviewService.MAX_EXPORT_ROWS; i++) {
            rows.add(exportRow(i));
        }
        when(logMapper.selectForReviewExport(any(), any(), any(), any(), any())).thenReturn(rows);
        when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(3L, "欧阳锋"));

        byte[] csv;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            csv = renderCsv(service.exportCsv("ALL", null, null, null));
        }

        String text = new String(csv, StandardCharsets.UTF_8);
        // 表头 1 行 + 恰好 50,000 行数据：上限本身放行，拒绝只在 50,001
        assertEquals(TeacherReviewService.MAX_EXPORT_ROWS + 1,
                text.split("\r\n", -1).length - 1);
        verify(logMapper).selectForReviewExport(any(), any(), any(), any(), any());
    }

    /**
     * 导出 OOM 红检（2026-11-03）：导出专用投影查询不得出现 {@code content_text}，
     * 也不得用 {@code SELECT l.*}。正文单行可达 20,000 字符，50,000 行全量入堆实测 187MB。
     * 把 {@code selectForReviewExport} 改回 {@code SELECT l.*}（即 {@code content_text}）即红。
     */
    @Test
    void exportProjectionExcludesContentText() throws NoSuchMethodException {
        String sql = AiGenerationLogMapper.class.getMethod("selectForReviewExport",
                        Boolean.class, String.class, LocalDateTime.class, LocalDateTime.class,
                        List.class)
                .getAnnotation(Select.class).value()[0];
        String normalized = sql.replaceAll("\\s+", " ");
        assertFalse(normalized.contains("content_text"), "导出投影不得包含正文列: " + normalized);
        assertFalse(normalized.contains("l.*"), "导出投影不得使用 SELECT l.*: " + normalized);
        // CSV 用到的列必须都在投影里，否则只是把 OOM 换成空列
        for (String column : List.of("log_pk", "log_id", "explain_id", "user_id", "source_ids",
                "model", "is_degraded", "created_at", "review_result", "teacher_score",
                "teacher_comment", "reviewed_at")) {
            assertTrue(normalized.contains(column), "投影缺列 " + column + ": " + normalized);
        }
    }

    /** 把流式响应体渲染成字节，供既有断言按整份 CSV 文本检查（真实写出路径同 Controller）。 */
    private static byte[] renderCsv(StreamingResponseBody body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            body.writeTo(out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /**
     * reviewed_at 按上海时区入库，不随 JVM 默认时区漂移：测试内把默认时区拨到 UTC，
     * 写出的评阅时间仍应与上海当前时间同窗（把 now(ZONE) 改回裸 now() 即红）。
     */
    @Test
    @SuppressWarnings("unchecked")
    void reviewedAtUsesShanghaiZoneRegardlessOfJvmDefault() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setLogId("EL_10");
        log.setScene("RAG");
        log.setUserId(10L);
        when(logMapper.selectById(10L)).thenReturn(log);
        when(logMapper.selectLinkedCourseIds("EL_10")).thenReturn(List.of());
        when(logMapper.update(isNull(), any())).thenReturn(1);

        LocalDateTime shanghaiNow;
        LocalDateTime written;
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            shanghaiNow = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                stp.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
                service.submit(10L, new ReviewSubmitRequest(5, "讲解清晰"));
            }
            ArgumentCaptor<LambdaUpdateWrapper<AiGenerationLog>> captor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(logMapper).update(isNull(), captor.capture());
            written = captor.getValue().getParamNameValuePairs().values().stream()
                    .filter(LocalDateTime.class::isInstance)
                    .map(LocalDateTime.class::cast)
                    .findFirst().orElseThrow();
        } finally {
            TimeZone.setDefault(original);
        }

        assertTrue(Duration.between(shanghaiNow, written).abs().getSeconds() < 5);
    }

    /**
     * 等待时长按上海时区计算：把 JVM 默认时区拨到 UTC 后，30 秒前创建的记录仍应显示约 30 秒等待。
     * 把 {@code now(ZONE)} 回退成裸 {@code now()} 即红——UTC 与上海差 8 小时，等待时长会被夹到 0。
     */
    @Test
    void waitingSecondsUsesShanghaiZoneRegardlessOfJvmDefault() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setExplainId("EX_1");
        log.setUserId(2L);
        log.setLogId("EL_1");
        log.setScene("RAG");
        log.setModel("deepseek-chat");
        log.setIsDegraded(0);
        log.setSourceIds("[]");
        log.setContentText("讲解正文");

        long waitingSeconds;
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            log.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(30));
            when(courseService.listActiveCourseIdsByTeacher(anyLong())).thenReturn(List.of(1L));
            when(logMapper.countForReview(any(), any(), any(), any(), any())).thenReturn(1L);
            when(logMapper.selectForReview(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(log));
            when(errorRecordMapper.selectBatchIds(any())).thenReturn(List.of());
            when(knowledgePointService.findByIds(any())).thenReturn(Map.of());
            when(userQueryService.findDisplayNames(any())).thenReturn(Map.of(2L, "学生A"));
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                waitingSeconds = service.page(1, 10).records().get(0).waitingSeconds();
            }
        } finally {
            TimeZone.setDefault(original);
        }

        assertTrue(waitingSeconds >= 25 && waitingSeconds <= 40, "waitingSeconds=" + waitingSeconds);
    }

    private AiGenerationLog exportRow(long logPk) {
        AiGenerationLog row = new AiGenerationLog();
        row.setLogPk(logPk);
        row.setExplainId("EX_" + logPk);
        row.setUserId(3L);
        row.setScene("RAG");
        row.setModel("deepseek-chat");
        row.setIsDegraded(0);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 28, 20, 0, 0));
        return row;
    }

    @Test
    void submitWritesOnlyWhenReviewedAtIsNull() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setScene("RAG");
        log.setUserId(10L);
        when(logMapper.selectById(10L)).thenReturn(log);
        when(logMapper.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            service.submit(10L, new ReviewSubmitRequest(5, "讲解清晰"));
            verify(logMapper).update(isNull(), any());
        }
    }

    @Test
    void duplicateReviewReturns40900() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setScene("RAG");
        log.setUserId(10L);
        log.setReviewedAt(LocalDateTime.now());
        when(logMapper.selectById(10L)).thenReturn(log);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
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
        log.setUserId(10L);
        when(logMapper.selectById(10L)).thenReturn(log);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(10L, new ReviewSubmitRequest(3, "  ", "RETURN")));
            assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
            verify(logMapper, never()).update(any(), any());
        }
    }

    /** 写侧课程范围（与读侧同一口径）：记录可定位到登录教师范围外课程 → 40300，且不发生写入。 */
    @Test
    void submitRejectsRecordScopedToOtherTeachersCourse() {
        AiGenerationLog log = new AiGenerationLog();
        log.setLogPk(10L);
        log.setLogId("EL_10");
        log.setScene("RAG");
        when(logMapper.selectById(10L)).thenReturn(log);
        when(logMapper.selectLinkedCourseIds("EL_10")).thenReturn(List.of(99L));
        when(courseService.listActiveCourseIdsByTeacher(anyLong())).thenReturn(List.of(1L));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(10L, new ReviewSubmitRequest(5, "讲解清晰")));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
            verify(logMapper, never()).update(any(), any());
        }
    }

    /** 写侧课程范围：自己课程的记录放行；归属不明只有创建者本人可评阅，非创建者教师 40300。 */
    @Test
    void submitAllowsOwnCourseAndRejectsUnknownAttribution() {
        AiGenerationLog own = new AiGenerationLog();
        own.setLogPk(11L);
        own.setLogId("EL_11");
        own.setScene("RAG");
        when(logMapper.selectById(11L)).thenReturn(own);
        when(logMapper.selectLinkedCourseIds("EL_11")).thenReturn(List.of(1L));
        AiGenerationLog unknown = new AiGenerationLog();
        unknown.setLogPk(12L);
        unknown.setLogId("EL_12");
        unknown.setScene("RAG");
        unknown.setUserId(7L);
        when(logMapper.selectById(12L)).thenReturn(unknown);
        when(logMapper.selectLinkedCourseIds("EL_12")).thenReturn(List.of());
        when(courseService.listActiveCourseIdsByTeacher(anyLong())).thenReturn(List.of(1L));
        when(logMapper.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(3L);
            service.submit(11L, new ReviewSubmitRequest(5, "讲解清晰"));
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(12L, new ReviewSubmitRequest(5, "讲解清晰")));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
            verify(logMapper, times(1)).update(isNull(), any());
        }
    }

    /** 写侧同一口径的反向：创建者本人（带 TEACHER 角色）可评阅自己归属不明的记录，不误伤。 */
    @Test
    void submitAllowsCreatorOwnUnattributedRecord() {
        AiGenerationLog own = new AiGenerationLog();
        own.setLogPk(13L);
        own.setLogId("EL_13");
        own.setScene("RAG");
        own.setUserId(9L);
        when(logMapper.selectById(13L)).thenReturn(own);
        when(logMapper.selectLinkedCourseIds("EL_13")).thenReturn(List.of());
        when(logMapper.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
            service.submit(13L, new ReviewSubmitRequest(5, "讲解清晰"));
        }

        verify(logMapper).update(isNull(), any());
    }

    /**
     * P1-6：评阅队列 / 导出 / COUNT 必须同口径排除归属不明的记录。
     * 三条 SQL 的 WHERE 片段必须逐字一致，且都含“可归属”守卫（{@code q.course_id IS NOT NULL}）。
     * 只改 COUNT 不改分页/导出，或删掉守卫，本用例即红（会出现“说有 46 条、导出 40 条”）。
     */
    @Test
    void reviewQueueAndExportShareAttributionGuard() throws NoSuchMethodException {
        String count = whereOf(sqlOf("countForReview", Boolean.class, String.class,
                LocalDateTime.class, LocalDateTime.class, List.class));
        String select = whereOf(sqlOf("selectForReview", Boolean.class, String.class,
                LocalDateTime.class, LocalDateTime.class, List.class, int.class, int.class));
        String export = whereOf(sqlOf("selectForReviewExport", Boolean.class, String.class,
                LocalDateTime.class, LocalDateTime.class, List.class));

        assertEquals(count, select, "COUNT 与分页 WHERE 必须同口径");
        assertEquals(count, export, "COUNT 与导出 WHERE 必须同口径");
        assertTrue(count.contains("q.course_id IS NOT NULL"), "缺少可归属守卫: " + count);
        assertTrue(count.contains("NOT EXISTS"), "他课排除条件丢失: " + count);
    }

    private static String sqlOf(String method, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        String sql = AiGenerationLogMapper.class.getMethod(method, parameterTypes)
                .getAnnotation(Select.class).value()[0];
        return sql.replaceAll("\\s+", " ").trim();
    }

    /** 取 WHERE 片段：去掉 SELECT 投影、ORDER BY 与 LIMIT，只比较筛选条件。 */
    private static String whereOf(String sql) {
        int from = sql.indexOf(" WHERE ");
        assertTrue(from >= 0, "缺少 WHERE: " + sql);
        int end = sql.length();
        int order = sql.indexOf(" ORDER BY ");
        if (order > from) {
            end = order;
        }
        int script = sql.indexOf("</script>");
        if (script > from && script < end) {
            end = script;
        }
        return sql.substring(from, end).trim();
    }
}
