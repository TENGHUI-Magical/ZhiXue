package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    private TeacherReviewService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiGenerationLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new TeacherReviewService(logMapper, errorRecordMapper, knowledgePointService,
                questionBankService, userQueryService, new ObjectMapper());
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
        when(logMapper.selectCount(any())).thenReturn(1L);
        when(logMapper.selectList(any())).thenReturn(List.of(log));

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
