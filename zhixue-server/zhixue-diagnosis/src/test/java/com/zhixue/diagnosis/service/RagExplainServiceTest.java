package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.controller.DiagnosisController;
import com.zhixue.diagnosis.dto.ExplainRequest;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.sun.net.httpserver.HttpServer;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
    private PracticeRecordMapper practiceRecordMapper;

    @Mock
    private PortraitService portraitService;

    @Mock
    private ExplainRateLimiter explainRateLimiter;

    private RagExplainService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiClientProperties properties = new AiClientProperties("http://127.0.0.1:1", 1);
        service = new RagExplainService(knowledgePointService, errorRecordService, questionBankService,
                properties, new ObjectMapper(), aiGenerationLogMapper, explainCacheService,
                practiceRecordMapper, portraitService, 30.0);
        DiagnosisController controller = new DiagnosisController(
                mock(KpMappingService.class), service, mock(RecommendationService.class),
                mock(OcrService.class), explainRateLimiter, mock(ExplainQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        lenient().when(explainRateLimiter.acquire(anyLong()))
                .thenReturn(new ExplainRateLimiter.Decision(true, 0L));
        lenient().when(portraitService.userKpAggregates(anyLong())).thenReturn(Map.of());
        lenient().when(knowledgePointService.prerequisites(anyString())).thenReturn(List.of());
        lenient().when(practiceRecordMapper.selectCount(any())).thenReturn(0L);
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
        when(explainCacheService.key(org.mockito.ArgumentMatchers.eq("v2"), anyString())).thenReturn("k1");
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
        when(explainCacheService.key(org.mockito.ArgumentMatchers.eq("v2"), anyString())).thenReturn("k1");
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
        when(explainCacheService.key(org.mockito.ArgumentMatchers.eq("v2"), anyString())).thenReturn("k1");

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
                request, kp, "CONCEPT_CONFUSION", "t-test", "求闭包", "B",
                new RagExplainService.ExplanationContext(null, 0, 0, null));

        assertThat(payload).containsEntry("answerText", "B");
    }

    @Test
    void contextUsesLatestAnswerAndQuestionCounts() {
        stubRecordAndKp("关系模型的核心概念");
        StudentErrorRecord record = errorRecordService.getConfirmed("EL_1");
        KnowledgePoint kp = knowledgePointService.getById("KP0201");
        PracticeRecord latest = new PracticeRecord();
        latest.setMyAnswer("学生最近作答");
        when(practiceRecordMapper.selectOne(any())).thenReturn(latest);
        when(practiceRecordMapper.countByUserAndQid(2L, "Q_0201")).thenReturn(4);
        when(practiceRecordMapper.selectCount(any())).thenReturn(3L);

        RagExplainService.ExplanationContext context = service.buildExplanationContext(2L, record, kp);

        assertThat(context.myAnswer()).isEqualTo("学生最近作答");
        assertThat(context.attemptCount()).isEqualTo(4);
        assertThat(context.wrongCount()).isEqualTo(3);
        assertThat(context.prereqWeakness()).isNull();
    }

    @Test
    void contextSelectsOnlyLowestPrereqWeaknessWithThreeSamples() {
        stubRecordAndKp("关系模型的核心概念");
        StudentErrorRecord record = errorRecordService.getConfirmed("EL_1");
        KnowledgePoint kp = knowledgePointService.getById("KP0201");
        when(knowledgePointService.prerequisites("KP0201")).thenReturn(List.of("KP0101", "KP0102", "KP0103"));
        when(portraitService.userKpAggregates(2L)).thenReturn(Map.of(
                "KP0101", agg(2, 0.0, 4.0),
                "KP0102", agg(3, 0.0, 4.0),
                "KP0103", agg(4, 4.0, 0.0)));
        KnowledgePoint prereq = new KnowledgePoint();
        prereq.setKpId("KP0102");
        prereq.setName("候选码");
        prereq.setConceptText("最小性");
        when(knowledgePointService.getById("KP0102")).thenReturn(prereq);

        RagExplainService.ExplanationContext context = service.buildExplanationContext(2L, record, kp);
        RagExplainService.PrereqWeakness weakness = context.prereqWeakness();

        assertThat(weakness.kpId()).isEqualTo("KP0102");
        assertThat(weakness.kpName()).isEqualTo("候选码");
        assertThat(weakness.sampleCount()).isEqualTo(3);
        assertThat(weakness.mastery()).isEqualTo(0.25);
        assertThat(weakness.conceptText()).isEqualTo("最小性");
    }

    @Test
    void structuredOutputParsesThreeSectionsExactly() {
        stubRecordAndKp("关系模型的核心概念");
        KnowledgePoint kp = knowledgePointService.getById("KP0201");
        String content = "【错因定位】\n你混淆了候选码与主码。\n"
                + "【完整讲解】\n先检查最小性。\n【自查】\n去掉属性后还唯一吗？";

        assertThat(service.structuredOutput(content, kp)).isEqualTo(Map.of(
                "sections", Map.of(
                        "errorLocation", "你混淆了候选码与主码。",
                        "explanation", "先检查最小性。",
                        "selfCheck", "去掉属性后还唯一吗？")));
    }

    @Test
    void localFallbackAndStructuredOutputUseLearningCard() {
        stubRecordAndKp("关系模型的核心概念");
        KnowledgePoint kp = knowledgePointService.getById("KP0201");
        kp.setMisconception("误区一\\n误区二\n误区三\n误区四");
        ExplainRequest request = new ExplainRequest();
        String content = String.join("", service.localTemplate(kp, request));

        assertThat(content).isEqualTo("【学习卡】\n【知识点】关系模型；教材P34\n【常见误区】\n"
                + "1. 误区一\n2. 误区二\n3. 误区三\n"
                + "【练习提示】请完成下方同类练习，检验自己是否真正掌握。");
        assertThat(service.structuredOutput(content, kp)).isEqualTo(Map.of(
                "learningCard", Map.of(
                        "kpName", "关系模型",
                        "textbookPage", "P34",
                        "misconceptions", List.of("误区一", "误区二", "误区三"),
                        "practiceHint", "请完成下方同类练习，检验自己是否真正掌握。")));
    }

    private UserKpAgg agg(int events, double successWeight, double severitySum) {
        UserKpAgg agg = new UserKpAgg();
        agg.setEvents(events);
        agg.setSuccessWeight(successWeight);
        agg.setSeveritySum(severitySum);
        agg.setErrorCount(1);
        return agg;
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

    @Test
    void canonicalContextCarriesKpNameSoRenameChangesCacheInput() {
        stubRecordAndKp("关系模型的核心概念");
        StudentErrorRecord record = errorRecordService.getConfirmed("EL_1");
        KnowledgePoint kp = knowledgePointService.getById("KP0201");
        RagExplainService.ExplanationContext context =
                new RagExplainService.ExplanationContext(null, 0, 0, null);

        String beforeRename = service.canonicalContext(
                2L, record, kp, "CONCEPT_CONFUSION", "求闭包", "B", context);
        assertThat(beforeRename).contains("\"kpName\":\"关系模型\"");

        KnowledgePoint renamed = new KnowledgePoint();
        renamed.setKpId(kp.getKpId());
        renamed.setName("关系模型（修订）");
        renamed.setConceptText(kp.getConceptText());
        renamed.setMisconception(kp.getMisconception());
        renamed.setTextbookPage(kp.getTextbookPage());
        String afterRename = service.canonicalContext(
                2L, record, renamed, "CONCEPT_CONFUSION", "求闭包", "B", context);

        // kpName 进入 canonicalContext → 进入 sha256 键；知识点改名后旧缓存不再命中
        assertThat(afterRename).isNotEqualTo(beforeRename);
    }

    @Test
    void classifyDegradeKeepsL1L2L3BoundariesInOnePlace() {
        // L1：主模型完整产出，不写降级级别，model 为真实模型
        assertThat(RagExplainService.classifyDegrade(true, false, null, "deepseek-chat"))
                .isEqualTo(new RagExplainService.DegradeOutcome(null, "deepseek-chat", false));

        // L2：中途断流/超时截断，有正文，保留真实模型名，绝不能是 L3/local-template
        assertThat(RagExplainService.classifyDegrade(true, true, null, "deepseek-chat"))
                .isEqualTo(new RagExplainService.DegradeOutcome("L2", "deepseek-chat", true));
        assertThat(RagExplainService.classifyDegrade(true, true, "L2", "glm-4-flash"))
                .isEqualTo(new RagExplainService.DegradeOutcome("L2", "glm-4-flash", true));

        // L2 现有语义：备用模型完整产出
        assertThat(RagExplainService.classifyDegrade(true, false, "L2", "glm-4-flash"))
                .isEqualTo(new RagExplainService.DegradeOutcome("L2", "glm-4-flash", true));

        // L3：远程零产出、本地模板真正产出正文
        assertThat(RagExplainService.classifyDegrade(false, true, null, "local-template"))
                .isEqualTo(new RagExplainService.DegradeOutcome("L3", "local-template", true));
        assertThat(RagExplainService.classifyDegrade(true, false, "L3", "local-template"))
                .isEqualTo(new RagExplainService.DegradeOutcome("L3", "local-template", true));
    }

    @Test
    void fullRemoteSuccessIsLoggedAsL1WithRealModel() throws Exception {
        stubRecordAndKp("关系模型的核心概念");
        String frames = """
                event: meta
                data: {"sources":[],"model":"deepseek-chat"}

                event: chunk
                data: {"content":"完整讲解"}

                event: done
                data: {"degraded":false,"blocked":false,"modelUsed":"deepseek-chat","promptVersion":"v2","tokens":5,"promptTokens":3,"elapsedMs":10}


                """;
        FakeSseServer server = new FakeSseServer(frames, 0L);
        try {
            String body = runExplain(mvcFor("http://127.0.0.1:" + server.port()));

            assertThat(body).contains("event:done", "完整讲解");
            AiGenerationLog log = captureLog();
            assertThat(log.getDegradeLevel()).isNull();
            assertThat(log.getIsDegraded()).isZero();
            assertThat(log.getModel()).isEqualTo("deepseek-chat");
        } finally {
            server.close();
        }
    }

    @Test
    void midStreamBreakIsLoggedAsL2WithRealModelNotTemplate() throws Exception {
        stubRecordAndKp("关系模型的核心概念");
        // meta + 一个 chunk 后干净关闭（无 done），模拟中途断流
        String frames = """
                event: meta
                data: {"sources":[],"model":"deepseek-chat"}

                event: chunk
                data: {"content":"半截讲解"}

                """;
        FakeSseServer server = new FakeSseServer(frames, 0L);
        try {
            String body = runExplain(mvcFor("http://127.0.0.1:" + server.port()));

            assertThat(body).contains("event:degraded", "\"level\":\"L2\"", "半截讲解");
            AiGenerationLog log = captureLog();
            assertThat(log.getDegradeLevel()).isEqualTo("L2");
            assertThat(log.getIsDegraded()).isEqualTo(1);
            assertThat(log.getModel()).isEqualTo("deepseek-chat");
            assertThat(log.getModel()).isNotEqualTo("local-template");
        } finally {
            server.close();
        }
    }

    @Test
    void upstreamTimeoutAfterPartialOutputIsLoggedAsL2() throws Exception {
        stubRecordAndKp("关系模型的核心概念");
        // meta + chunk 后停顿超过 1s 客户端超时，模拟超时截断
        String frames = """
                event: meta
                data: {"sources":[],"model":"deepseek-chat"}

                event: chunk
                data: {"content":"超时前半截"}

                """;
        FakeSseServer server = new FakeSseServer(frames, 15_000L);
        try {
            runExplain(mvcFor("http://127.0.0.1:" + server.port(), 8));

            AiGenerationLog log = captureLog();
            assertThat(log.getDegradeLevel()).isEqualTo("L2");
            assertThat(log.getIsDegraded()).isEqualTo(1);
            assertThat(log.getModel()).isEqualTo("deepseek-chat");
        } finally {
            server.close();
        }
    }

    @Test
    void remoteZeroOutputFallsBackToLocalTemplateAsL3() throws Exception {
        stubRecordAndKp("关系模型的核心概念");

        String body = runExplain(mockMvc);

        assertThat(body).contains("event:degraded", "\"level\":\"L3\"", "event:done");
        AiGenerationLog log = captureLog();
        assertThat(log.getDegradeLevel()).isEqualTo("L3");
        assertThat(log.getIsDegraded()).isEqualTo(1);
        assertThat(log.getModel()).isEqualTo("local-template");
    }

    private AiGenerationLog captureLog() {
        ArgumentCaptor<AiGenerationLog> log = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogMapper, timeout(3_000)).insert(log.capture());
        return log.getValue();
    }

    /**
     * 极简 SSE 假上游：手写 HTTP/1.1 chunked 响应，保证帧立即 flush 到 socket，
     * 可模拟“中途断流（无 done 直接结束）”与“超时截断（写完后长时间无新帧）”。
     */
    private static final class FakeSseServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        FakeSseServer(String frames, long stallMillis) throws Exception {
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            byte[] body = frames.getBytes(StandardCharsets.UTF_8);
            Thread acceptor = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        // 只消费请求头；请求体未读不影响响应
                    }
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/event-stream; charset=utf-8\r\n"
                            + "Transfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.write((Integer.toHexString(body.length) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.write(body);
                    out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                    if (stallMillis > 0) {
                        Thread.sleep(stallMillis);
                    }
                    out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } catch (Exception ignored) {
                    // 客户端先断开（超时后 dispose）属预期
                }
            }, "fake-sse-server");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
        }
    }

    private MockMvc mvcFor(String baseUrl) {
        return mvcFor(baseUrl, 10);
    }

    private MockMvc mvcFor(String baseUrl, int timeoutSeconds) {
        AiClientProperties properties = new AiClientProperties(baseUrl, timeoutSeconds);
        RagExplainService upstreamService = new RagExplainService(knowledgePointService, errorRecordService,
                questionBankService, properties, new ObjectMapper(), aiGenerationLogMapper,
                explainCacheService, practiceRecordMapper, portraitService, 30.0);
        DiagnosisController controller = new DiagnosisController(
                mock(KpMappingService.class), upstreamService, mock(RecommendationService.class),
                mock(OcrService.class), explainRateLimiter, mock(ExplainQueryService.class));
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String runExplain(MockMvc mvc) throws Exception {
        MvcResult result;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            result = mvc.perform(post("/api/diagnosis/explain")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            result = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 缓存 createdAt 按上海时区生成：把 JVM 默认时区拨到 UTC 后，写入缓存的 ISO 时间
     * 仍应与上海当前时间同窗。把 {@code now(ZONE)} 回退成裸 {@code now()} 即红（差 8 小时）。
     */
    @Test
    @SuppressWarnings("unchecked")
    void cacheCreatedAtUsesShanghaiZoneRegardlessOfJvmDefault() throws Exception {
        Method saveCache = RagExplainService.class.getDeclaredMethod("saveCache",
                String.class, KnowledgePoint.class, StringBuffer.class,
                AtomicReference.class, AtomicReference.class, AtomicBoolean.class, AtomicBoolean.class);
        saveCache.setAccessible(true);
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setTextbookPage("P34");
        kp.setName("关系模型");

        LocalDateTime shanghaiNow;
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            shanghaiNow = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            saveCache.invoke(service, "k1", kp, new StringBuffer("正文"),
                    new AtomicReference<>("deepseek-chat"), new AtomicReference<>("v2"),
                    new AtomicBoolean(false), new AtomicBoolean(false));
        } finally {
            TimeZone.setDefault(original);
        }

        ArgumentCaptor<ExplainCacheService.Cached> captor =
                ArgumentCaptor.forClass(ExplainCacheService.Cached.class);
        verify(explainCacheService).put(eq("k1"), captor.capture());
        LocalDateTime written = LocalDateTime.parse(captor.getValue().createdAt());
        assertThat(Duration.between(shanghaiNow, written).abs().getSeconds()).isLessThan(5);
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
