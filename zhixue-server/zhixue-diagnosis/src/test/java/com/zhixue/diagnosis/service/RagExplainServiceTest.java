package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.controller.DiagnosisController;
import com.zhixue.diagnosis.dto.ExplainRequest;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 讲解 SSE 网关单测：L4 concept_text 缺失 → 60001；缓存命中回放；forceRefresh 跳过缓存。
 */
@ExtendWith(MockitoExtension.class)
class RagExplainServiceTest {

    private static final String BODY = """
            {"logId":"EL_1","kpId":"KP0201","errorType":"CONCEPT_CONFUSION","questionText":"求闭包"}
            """;

    @Mock
    private KnowledgePointService knowledgePointService;

    @Mock
    private StudentErrorRecordService errorRecordService;

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private AiGenerationLogMapper aiGenerationLogMapper;

    @Mock
    private ExplainCacheService explainCacheService;

    @Mock
    private ExplainRateLimiter explainRateLimiter;

    private RagExplainService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiClientProperties properties = new AiClientProperties("http://127.0.0.1:1", 1);
        service = new RagExplainService(knowledgePointService, errorRecordService, questionBankService,
                properties, new ObjectMapper(), aiGenerationLogMapper, explainCacheService);
        DiagnosisController controller = new DiagnosisController(
                mock(KpMappingService.class), service, mock(RecommendationService.class),
                mock(OcrService.class), explainRateLimiter, mock(ExplainQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        lenient().when(explainRateLimiter.acquire(anyLong()))
                .thenReturn(new ExplainRateLimiter.Decision(true, 0L));
    }

    @Test
    void blankConceptReturns60001AndSkipsCacheAndTemplate() throws Exception {
        stubRecordAndKp(null);

        MvcResult result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = mockMvc.perform(post("/api/diagnosis/explain")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            result = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }

        // SSE 响应未声明 charset，按 UTF-8 解码原始字节，避免 MockMvc 默认 ISO-8859-1
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("\"code\":60001");
        assertThat(body).doesNotContain("event:delta");
        verify(explainCacheService, never()).get(anyString());

        ArgumentCaptor<AiGenerationLog> log = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogMapper).insert(log.capture());
        assertThat(log.getValue().getDegradeLevel()).isEqualTo("L4");
        assertThat(log.getValue().getIsDegraded()).isEqualTo(1);
    }

    @Test
    void cacheHitReplaysFullContentWithoutCallingUpstream() throws Exception {
        stubRecordAndKp("关系模型的核心概念");
        when(explainCacheService.key("KP0201", "关系模型", "关系模型的核心概念", null, "P34",
                "CONCEPT_CONFUSION", "求闭包（校正）", "B")).thenReturn("k1");
        when(explainCacheService.get("k1")).thenReturn(new ExplainCacheService.Cached(
                "缓存中的完整讲解", "deepseek-chat", "v1",
                List.of(new ExplainCacheService.Source("KP0201", "P34", "关系模型")),
                "2026-10-26T12:00:00"));

        MvcResult result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = mockMvc.perform(post("/api/diagnosis/explain")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            result = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("event:meta", "\"model\":\"cache\"");
        assertThat(body).contains("event:delta", "缓存中的完整讲解");
        assertThat(body).contains("event:done", "\"cached\":true");

        ArgumentCaptor<AiGenerationLog> log = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogMapper).insert(log.capture());
        assertThat(log.getValue().getModel()).isEqualTo("cache");
        verify(explainCacheService, never()).put(anyString(), any());
    }

    @Test
    void cacheHitSanitizesPagesOutsideKnowledgePointWhitelist() throws Exception {
        stubRecordAndKp("关系模型的核心概念");
        when(explainCacheService.key("KP0201", "关系模型", "关系模型的核心概念", null, "P34",
                "CONCEPT_CONFUSION", "求闭包（校正）", "B")).thenReturn("k1");
        when(explainCacheService.get("k1")).thenReturn(new ExplainCacheService.Cached(
                "参见教材P999，合法 P34。", "deepseek-chat", "v1",
                List.of(new ExplainCacheService.Source("KP0201", "P34", "关系模型")),
                "2026-10-26T12:00:00"));

        MvcResult result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = mockMvc.perform(post("/api/diagnosis/explain")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            result = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("P999", "教材教材相关章节");
        assertThat(body).contains("参见教材相关章节", "P34");
    }

    @Test
    void forceRefreshSkipsCacheRead() throws Exception {
        stubRecordAndKp("关系模型的核心概念");
        when(explainCacheService.key("KP0201", "关系模型", "关系模型的核心概念", null, "P34",
                "CONCEPT_CONFUSION", "求闭包（校正）", "B")).thenReturn("k1");

        String body = BODY.replace("\"questionText\"", "\"forceRefresh\":true,\"questionText\"");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            mockMvc.perform(post("/api/diagnosis/explain")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(request().asyncStarted());
        }

        verify(explainCacheService, never()).get(anyString());
    }

    @Test
    void upstreamPayloadCarriesAnswerTextForOutputGuard() {
        stubRecordAndKp("关系模型的核心概念");
        KnowledgePoint kp = knowledgePointService.getById("KP0201");
        ExplainRequest request = new ExplainRequest();
        request.setLogId("EL_1");

        Map<String, Object> payload = service.buildUpstreamPayload(
                request, kp, "CONCEPT_CONFUSION", "t-test", "求闭包", "B");

        assertThat(payload).containsEntry("answerText", "B");
    }

    @Test
    void cacheTextPrefersConfirmedRecordText() {
        StudentErrorRecord record = new StudentErrorRecord();
        record.setCorrectedText("记录中的校正题干");
        ExplainRequest request = new ExplainRequest();
        request.setQuestionText("请求里的题干");

        assertThat(service.resolveCacheText(record, request)).isEqualTo("记录中的校正题干");

        record.setCorrectedText("   ");
        assertThat(service.resolveCacheText(record, request)).isEqualTo("请求里的题干");
    }

    private void stubRecordAndKp(String conceptText) {
        StudentErrorRecord record = new StudentErrorRecord();
        record.setLogId("EL_1");
        record.setQId("Q_0201");
        record.setKpId("KP0201");
        record.setErrorType("CONCEPT_CONFUSION");
        record.setCorrectedText("求闭包（校正）");
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        kp.setConceptText(conceptText);
        kp.setTextbookPage("P34");

        lenient().when(errorRecordService.getConfirmed("EL_1")).thenReturn(record);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp);
        QuestionBank question = new QuestionBank();
        question.setAnswerText("B");
        lenient().when(questionBankService.getById("Q_0201")).thenReturn(question);
    }
}
