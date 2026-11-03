package com.zhixue.diagnosis.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import jakarta.annotation.PreDestroy;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.dto.ExplainRequest;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 流式讲解网关（契约 V2：POST /api/diagnosis/explain，SSE）。
 * <p>
 * 事件序列：meta → source → delta* →（degraded）→ done / error。
 * 职责边界：Java 只做编排与转发，不直连大模型；模型调用全部在 zhixue-ai。
 * 降级：上游超时/不可用 → 本地模板（仍使用概念切片与教材页码，来源可追溯）。
 * 连接治理：15 秒 `: heartbeat` 注释帧；整体 60 秒；客户端断开时取消上游订阅。
 */
@Service
public class RagExplainService {

    private static final Logger log = LoggerFactory.getLogger(RagExplainService.class);

    /** 整体超时：契约 5.2 节 */
    private static final long TOTAL_TIMEOUT_MS = 60_000L;

    /** 心跳间隔：契约 5.2 节 */
    private static final long HEARTBEAT_SECONDS = 15L;

    /** meta.model 暂用 auto；接入模型配置同步后改为真实模型名 */
    private static final String META_MODEL = "auto";

    private static final Pattern PAGE_CITATION = Pattern.compile(
            "(?<![A-Za-z0-9])P\\s*(\\d+)(?:\\s*[—–-]\\s*(\\d+))?"
                    + "|第\\s*(\\d+)(?:\\s*[—–-]\\s*(\\d+))?\\s*页",
            Pattern.CASE_INSENSITIVE);

    /** SSE 帧显式 UTF-8：TEXT_PLAIN 默认 ISO-8859-1 会把中文写成问号 */
    private static final MediaType TEXT_PLAIN_UTF8 =
            MediaType.parseMediaType("text/plain;charset=UTF-8");

    /** 契约 ExplainRequest.errorType 的五个英文枚举 */
    private static final Set<String> ERROR_TYPES = Set.of(
            "CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
            "CALCULATION_ERROR", "BOUNDARY_ERROR");

    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final KnowledgePointService knowledgePointService;
    private final StudentErrorRecordService errorRecordService;
    private final QuestionBankService questionBankService;
    private final AiClientProperties aiClientProperties;
    private final ObjectMapper objectMapper;
    private final AiGenerationLogMapper aiGenerationLogMapper;
    private final ExplainCacheService explainCacheService;
    private final WebClient webClient;

    /** 心跳调度：固定 2 线程，取消的任务及时移除，避免高并发 SSE 下队列堆积 */
    private final ScheduledThreadPoolExecutor heartbeatPool =
            new ScheduledThreadPoolExecutor(2, daemonFactory("sse-heartbeat"));
    /** 降级/留痕线程池：有界队列，队列满时由调用线程执行，保证 SSE 一定收尾 */
    private final ThreadPoolExecutor fallbackPool = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            daemonFactory("sse-fallback"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public RagExplainService(KnowledgePointService knowledgePointService,
                             StudentErrorRecordService errorRecordService,
                             QuestionBankService questionBankService,
                             AiClientProperties aiClientProperties,
                             ObjectMapper objectMapper,
                             AiGenerationLogMapper aiGenerationLogMapper,
                             ExplainCacheService explainCacheService) {
        this.knowledgePointService = knowledgePointService;
        this.errorRecordService = errorRecordService;
        this.questionBankService = questionBankService;
        this.aiClientProperties = aiClientProperties;
        this.objectMapper = objectMapper;
        this.aiGenerationLogMapper = aiGenerationLogMapper;
        this.explainCacheService = explainCacheService;
        this.webClient = WebClient.builder().baseUrl(aiClientProperties.baseUrl()).build();
        this.heartbeatPool.setRemoveOnCancelPolicy(true);
    }

    /**
     * 限流拒绝：仍用 SSE error 事件返回，保持前端 fetch 流解析路径一致。
     *
     * @param code      错误码（42900）
     * @param message   提示
     * @param retryable 是否可重试
     * @return 已发送 error 并关闭的 SSE 发射器
     */
    public SseEmitter reject(int code, String message, boolean retryable) {
        SseEmitter emitter = new SseEmitter(0L);
        sendError(emitter, code, message, retryable);
        emitter.complete();
        return emitter;
    }

    /**
     * 建立 SSE 连接并开始转发讲解。
     *
     * @param request 契约 ExplainRequest；logId 必填且记录须为 CONFIRMED
     * @param traceIdFilterTraceId 由 TraceIdFilter 生成的链路 ID（契约 §1.2）；null 时回退本地生成
     * @return SSE 发射器；知识点不存在或 errorType 非法时先发 error 事件再关闭
     */
    public SseEmitter explain(ExplainRequest request, String traceIdFilterTraceId) {
        SseEmitter emitter = new SseEmitter(TOTAL_TIMEOUT_MS);
        long start = System.currentTimeMillis();
        String explainId = nextExplainId();
        // 契约 §1.2：traceId 全链路唯一（Filter 响应头 → meta → FastAPI → ai_generation_log）
        String traceId = traceIdFilterTraceId != null ? traceIdFilterTraceId : "t-" + explainId;

        KnowledgePoint kp;
        String errorType;
        StudentErrorRecord record;
        try {
            // 契约前置：必须是 CONFIRMED 错题记录，且记录里的 kpId 与请求一致
            record = errorRecordService.getConfirmed(request.getLogId());
            if (!record.getKpId().equals(request.getKpId())) {
                sendError(emitter, ErrorCode.PARAM_ERROR.getCode(),
                        "kpId 与错题记录不一致", false);
                emitter.complete();
                return emitter;
            }
            errorType = record.getErrorType() == null || record.getErrorType().isBlank()
                    ? request.getErrorType() : record.getErrorType();
            kp = knowledgePointService.getById(record.getKpId());
        } catch (BizException e) {
            sendError(emitter, e.getErrorCode().getCode(), e.getMessage(), false);
            emitter.complete();
            return emitter;
        }
        if (!ERROR_TYPES.contains(errorType)) {
            sendError(emitter, ErrorCode.PARAM_ERROR.getCode(),
                    "errorType 必须为契约五类错因之一", false);
            emitter.complete();
            return emitter;
        }
        if (kp == null) {
            sendError(emitter, ErrorCode.NOT_FOUND.getCode(), "知识点不存在", false);
            emitter.complete();
            return emitter;
        }

        long userId = StpUtil.getLoginIdAsLong();

        // 契约 §6 L4：L3 模板所需的 concept_text 为空时直接 60001，禁止用空概念伪造模板讲解
        if (kp.getConceptText() == null || kp.getConceptText().isBlank()) {
            log.warn("知识点原文缺失，L4 拒绝生成 explainId={} kpId={}", explainId, kp.getKpId());
            sendMeta(emitter, explainId, traceId, META_MODEL);
            sendSource(emitter, kp);
            sendError(emitter, ErrorCode.AI_TIMEOUT.getCode(),
                    "知识点原文缺失，暂无法生成讲解，请联系教师补全", false);
            writeLog(explainId, traceId, userId, request.getLogId(), kp.getKpId(),
                    "none", true, "L4", System.currentTimeMillis() - start,
                    "unknown", 0, 0, "", false, new AtomicBoolean(false));
            emitter.complete();
            return emitter;
        }

        String answerText = resolveAnswerText(record);

        // 契约 §6：缓存键 = contextVersion + sha256(全部 prompt 材料)，TTL 7 天；forceRefresh 跳过读缓存。
        // correctedText 取 CONFIRMED 记录（学生校正后的题干），缺省回退请求的 questionText；
        // 键覆盖 buildUpstreamPayload 的全部语义材料（含 misconception 与题库标准答案），
        // 材料任一变化即失效，保证“生成依据”与“缓存键依据”一致。
        String cacheText = resolveCacheText(record, request);
        String cacheKey = explainCacheService.key(kp.getKpId(), kp.getName(), kp.getConceptText(),
                kp.getMisconception(), kp.getTextbookPage(), errorType, cacheText, answerText);
        if (!Boolean.TRUE.equals(request.getForceRefresh())) {
            ExplainCacheService.Cached cached = explainCacheService.get(cacheKey);
            if (cached != null && cached.content() != null && !cached.content().isBlank()) {
                sendMeta(emitter, explainId, traceId, "cache");
                if (cached.sources() != null) {
                    for (ExplainCacheService.Source source : cached.sources()) {
                        sendSource(emitter, source);
                    }
                }
                String replay = sanitizePageCitations(cached.content(), kp.getTextbookPage());
                send(emitter, "delta", Map.of("text", replay));
                sendDone(emitter, explainId, start, 0, true);
                writeLog(explainId, traceId, userId, request.getLogId(), kp.getKpId(),
                        "cache", false, null, System.currentTimeMillis() - start,
                        cached.promptVersion(), 0, 0, replay, false,
                        new AtomicBoolean(false));
                emitter.complete();
                return emitter;
            }
        }
        // StringBuffer：Reactor 线程、降级线程、cleanup 线程三方并发读写正文快照（线程安全）
        StringBuffer content = new StringBuffer();
        AtomicReference<String> modelUsed = new AtomicReference<>("unknown");
        AtomicReference<String> degradeLevel = new AtomicReference<>();
        AtomicReference<String> promptVersion = new AtomicReference<>("unknown");
        AtomicBoolean degraded = new AtomicBoolean(false);
        AtomicBoolean blocked = new AtomicBoolean(false);
        AtomicInteger completionTokens = new AtomicInteger();
        AtomicInteger promptTokens = new AtomicInteger();
        AtomicBoolean logged = new AtomicBoolean(false);

        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean chunkReceived = new AtomicBoolean(false);
        AtomicInteger characters = new AtomicInteger();
        AtomicReference<Disposable> upstream = new AtomicReference<>();

        ScheduledFuture<?> heartbeat = heartbeatPool.scheduleAtFixedRate(
                () -> {
                    if (!closed.get()) {
                        sendHeartbeat(emitter);
                    }
                },
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        Runnable cleanup = () -> {
            if (closed.compareAndSet(false, true)) {
                heartbeat.cancel(false);
                dispose(upstream.get());
                // 客户端断开也要留痕：契约 5.3 要求已生成内容与耗时照常记录
                fallbackPool.execute(() -> writeLog(explainId, traceId, userId, request.getLogId(),
                        kp.getKpId(), modelUsed.get(), degraded.get(), degradeLevel.get(),
                        System.currentTimeMillis() - start, promptVersion.get(),
                        promptTokens.get(), completionTokens.get(),
                        content.toString(), blocked.get(), logged));
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onError(e -> cleanup.run());
        emitter.onTimeout(() -> {
            cleanup.run();
            sendError(emitter, ErrorCode.AI_TIMEOUT.getCode(), "讲解超时，请重试", true);
            emitter.complete();
        });

        sendMeta(emitter, explainId, traceId, META_MODEL);
        sendSource(emitter, kp);

        Map<String, Object> payload = buildUpstreamPayload(
                request, kp, errorType, traceId, cacheText, answerText);
        Flux<ServerSentEvent<String>> stream = webClient.post()
                .uri("/api/rag/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .timeout(Duration.ofSeconds(aiClientProperties.timeoutSeconds()));

        Disposable subscription = stream.subscribe(
                event -> {
                    String name = event.event() == null ? "message" : event.event();
                    switch (name) {
                        case "meta" -> {
                            // 上游 meta 携带检索增强来源；转成契约 source 事件，前端按 kpId 去重
                            Map<String, Object> meta = readMap(event.data());
                            Object sources = meta.get("sources");
                            if (sources instanceof List<?> list) {
                                for (Object item : list) {
                                    if (!(item instanceof Map<?, ?> source)) {
                                        continue;
                                    }
                                    Object kpIdValue = source.get("kpId");
                                    String sourceKpId = kpIdValue == null ? null : String.valueOf(kpIdValue);
                                    if (sourceKpId == null || sourceKpId.isBlank()) {
                                        continue;
                                    }
                                    Map<String, Object> out = new LinkedHashMap<>();
                                    out.put("kpId", sourceKpId);
                                    out.put("title", source.get("name"));
                                    out.put("textbookPage", pageFromRef(source.get("ref")));
                                    send(emitter, "source", out);
                                }
                            }
                        }
                        case "chunk" -> {
                            String text = readContent(event.data());
                            if (!text.isEmpty()) {
                                chunkReceived.set(true);
                                characters.addAndGet(text.length());
                                content.append(text);
                                send(emitter, "delta", Map.of("text", text));
                            }
                        }
                        case "degraded" -> {
                            degraded.set(true);
                            String level = readString(event.data(), "level");
                            if (level != null && !level.isBlank()) {
                                degradeLevel.set(level);
                            }
                            sendRaw(emitter, "degraded", event.data());
                        }
                        case "done" -> {
                            Map<String, Object> done = readMap(event.data());
                            completionTokens.set(intValue(done.get("tokens")));
                            promptTokens.set(intValue(done.get("promptTokens")));
                            Object model = done.get("modelUsed");
                            if (model != null && !String.valueOf(model).isBlank()) {
                                modelUsed.set(String.valueOf(model));
                            }
                            Object version = done.get("promptVersion");
                            if (version != null && !String.valueOf(version).isBlank()) {
                                promptVersion.set(String.valueOf(version));
                            }
                            if (Boolean.TRUE.equals(done.get("degraded"))) {
                                degraded.set(true);
                            }
                            if (Boolean.TRUE.equals(done.get("blocked"))) {
                                blocked.set(true);
                            }
                        }
                        case "error" -> {
                            // 上游错误事件转发给前端（60004 合规拦截等）；并驱动留痕与关闭
                            int code = intValue(readMap(event.data()).get("code"));
                            if (code == ErrorCode.CONTENT_BLOCKED.getCode()) {
                                blocked.set(true);
                            }
                            sendRaw(emitter, "error", event.data());
                            emitter.complete();
                            cleanup.run();
                        }
                        default -> {
                            // 未知事件忽略，保持前向兼容
                        }
                    }
                },
                error -> {
                    log.warn("AI 讲解上游失败，切本地模板：{}", error.toString());
                    fallback(emitter, kp, request, explainId, start, chunkReceived,
                            characters, cleanup, content, modelUsed, degraded, degradeLevel);
                },
                () -> {
                    saveCache(cacheKey, kp, content, modelUsed, promptVersion, degraded, blocked);
                    sendDone(emitter, explainId, start, completionTokens.get());
                    emitter.complete();
                    cleanup.run();
                });
        upstream.set(subscription);
        return emitter;
    }

    /** 上游不可用时，用概念切片本地拼讲解；已收到部分 delta 时只补降级标记与 done。 */
    private void fallback(SseEmitter emitter, KnowledgePoint kp, ExplainRequest request,
                          String explainId, long start, AtomicBoolean chunkReceived,
                          AtomicInteger characters, Runnable cleanup, StringBuffer content,
                          AtomicReference<String> modelUsed, AtomicBoolean degraded,
                          AtomicReference<String> degradeLevel) {
        fallbackPool.execute(() -> {
            try {
                degraded.set(true);
                degradeLevel.set("L3");
                modelUsed.set("local-template");
                if (!chunkReceived.get()) {
                    send(emitter, "degraded", Map.of("level", "L3", "reason", "AI 服务不可用"));
                    for (String part : localTemplate(kp, request)) {
                        characters.addAndGet(part.length());
                        content.append(part);
                        send(emitter, "delta", Map.of("text", part));
                        sleepQuietly(30);
                    }
                } else {
                    send(emitter, "degraded", Map.of("level", "L3", "reason", "上游中断"));
                }
                sendDone(emitter, explainId, start, 0);
                emitter.complete();
            } catch (Exception e) {
                log.warn("本地模板讲解发送失败", e);
                emitter.completeWithError(e);
            } finally {
                cleanup.run();
            }
        });
    }

    private void sendMeta(SseEmitter emitter, String explainId, String traceId, String model) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("explainId", explainId);
        meta.put("traceId", traceId);
        meta.put("model", model);
        meta.put("degraded", false);
        send(emitter, "meta", meta);
    }

    private void sendSource(SseEmitter emitter, KnowledgePoint kp) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kpId", kp.getKpId());
        source.put("textbookPage", kp.getTextbookPage());
        source.put("title", kp.getName());
        send(emitter, "source", source);
    }

    /** 缓存整文回放前按当前知识点页码白名单清洗，避免旧缓存绕过 AI 侧校验。 */
    private String sanitizePageCitations(String content, String textbookPage) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        Set<Integer> allowed = pageNumbers(textbookPage);
        Matcher matcher = PAGE_CITATION.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            if (!allowed.containsAll(pageNumbers(matcher.group()))) {
                String prefix = content.substring(0, matcher.start());
                String replacement = prefix.endsWith("教材") || prefix.endsWith("》")
                        ? "相关章节" : "教材相关章节";
                matcher.appendReplacement(out, replacement);
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private Set<Integer> pageNumbers(String text) {
        Set<Integer> pages = new HashSet<>();
        if (text == null) {
            return pages;
        }
        Matcher matcher = PAGE_CITATION.matcher(text);
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(3));
            String endValue = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            int end = endValue == null ? start : Integer.parseInt(endValue);
            for (int page = Math.min(start, end); page <= Math.max(start, end); page++) {
                pages.add(page);
            }
        }
        return pages;
    }

    /** 上游 ref 形如「《数据库系统概论（第6版）》P38」，只取页码部分回给前端。 */
    private String pageFromRef(Object ref) {
        if (ref == null) {
            return null;
        }
        String text = String.valueOf(ref);
        int index = text.lastIndexOf('》');
        return index >= 0 ? text.substring(index + 1).trim() : text;
    }

    private void sendSource(SseEmitter emitter, ExplainCacheService.Source cached) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kpId", cached.kpId());
        source.put("textbookPage", cached.textbookPage());
        source.put("title", cached.title());
        send(emitter, "source", source);
    }

    private void sendDone(SseEmitter emitter, String explainId, long start, int tokens) {
        sendDone(emitter, explainId, start, tokens, false);
    }

    private void sendDone(SseEmitter emitter, String explainId, long start, int tokens, boolean cached) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("explainId", explainId);
        done.put("latencyMs", System.currentTimeMillis() - start);
        done.put("tokens", tokens);
        if (cached) {
            done.put("cached", true);
        }
        send(emitter, "done", done);
    }

    /**
     * L1 正常完成且未降级/未拦截时写缓存；L2/L3 与空正文不缓存，避免把降级结果固化 7 天。
     */
    private void saveCache(String cacheKey, KnowledgePoint kp, StringBuffer content,
                           AtomicReference<String> modelUsed, AtomicReference<String> promptVersion,
                           AtomicBoolean degraded, AtomicBoolean blocked) {
        if (degraded.get() || blocked.get() || content.length() == 0) {
            return;
        }
        ExplainCacheService.Cached cached = new ExplainCacheService.Cached(
                content.toString(), modelUsed.get(), promptVersion.get(),
                List.of(new ExplainCacheService.Source(kp.getKpId(), kp.getTextbookPage(), kp.getName())),
                LocalDateTime.now().toString());
        explainCacheService.put(cacheKey, cached);
    }

    private void sendError(SseEmitter emitter, int code, String message, boolean retryable) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        send(emitter, "error", error);
    }

    /** 仅把答案用于 AI 输出泄露校验；无 qId 或题目失效时不阻断既有讲解。 */
    private String resolveAnswerText(StudentErrorRecord record) {
        if (record.getQId() == null || record.getQId().isBlank()) {
            return null;
        }
        try {
            QuestionBank question = questionBankService.getById(record.getQId());
            return question == null ? null : question.getAnswerText();
        } catch (BizException exc) {
            log.warn("题目答案不可用，跳过本次输出泄露校验 logId={} qId={}", record.getLogId(), record.getQId());
            return null;
        }
    }

    /**
     * 解析本轮讲解使用的题干文本：CONFIRMED 记录的 correctedText 优先，空则回退请求 questionText。
     * 该值同时用于缓存键和上游 prompt，保证“生成依据”与“缓存键”一致。
     */
    String resolveCacheText(StudentErrorRecord record, ExplainRequest request) {
        String correctedText = record.getCorrectedText();
        return correctedText == null || correctedText.isBlank()
                ? request.getQuestionText() : correctedText;
    }

    Map<String, Object> buildUpstreamPayload(ExplainRequest request, KnowledgePoint kp,
                                             String errorType, String traceId,
                                             String questionText, String answerText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("kpId", kp.getKpId());
        payload.put("kpName", kp.getName());
        payload.put("conceptText", kp.getConceptText());
        payload.put("misconception", kp.getMisconception());
        payload.put("textbookPage", kp.getTextbookPage());
        payload.put("questionText", questionText);
        payload.put("answerText", answerText);
        payload.put("errorType", errorType);
        payload.put("courseId", kp.getCourseId() == null ? 1L : kp.getCourseId());
        payload.put("logId", request.getLogId());
        payload.put("forceRefresh", Boolean.TRUE.equals(request.getForceRefresh()));
        return payload;
    }

    /** L3 本地模板：逐段使用已核对的概念切片与误区，不新增事实。 */
    private List<String> localTemplate(KnowledgePoint kp, ExplainRequest request) {
        String name = kp.getName() == null ? kp.getKpId() : kp.getName();
        String concept = first(kp.getConceptText(), 160);
        String mistake = first(kp.getMisconception(), 90);
        String question = first(request.getQuestionText(), 70);
        String page = kp.getTextbookPage() == null ? "页码待确认" : kp.getTextbookPage();

        List<String> parts = new ArrayList<>(4);
        parts.add("这道题对应知识点「" + name + "」。先抓定义：" + concept);
        parts.add(mistake.isEmpty() ? "这个知识点暂时没有登记常见误区，请对照概念逐条自查。" : "常见误区：" + mistake);
        parts.add(question.isEmpty() ? "先用自己的话复述概念，再找一个反例检验边界条件。" : "回到题目：" + question);
        parts.add("自查顺序：定义 → 适用条件 → 反例。来源：《数据库系统概论（第6版）》" + page + "。");
        return parts;
    }

    /** 上游 chunk 的 data 是 {"content":"..."}，转成契约 delta 的 {"text":"..."} */
    private String readContent(String data) {
        if (data == null || data.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> map = objectMapper.readValue(data,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object content = map.get("content");
            return content == null ? "" : String.valueOf(content);
        } catch (Exception e) {
            log.debug("上游 chunk 解析失败：{}", e.toString());
            return "";
        }
    }

    /** 解析上游 JSON；失败返回空 Map，不阻断流 */
    private Map<String, Object> readMap(String data) {
        if (data == null || data.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.debug("上游事件解析失败：{}", e.toString());
            return Map.of();
        }
    }

    private String readString(String data, String key) {
        Object value = readMap(data).get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 写 ai_generation_log：同一 explainId 只写一次，客户端断开时也保留已生成内容 */
    private void writeLog(String explainId, String traceId, long userId, String logId, String kpId,
                          String model, boolean degraded, String degradeLevel, long latencyMs,
                          String promptVersion, int promptTokens, int completionTokens,
                          String content, boolean blocked, AtomicBoolean logged) {
        if (!logged.compareAndSet(false, true)) {
            return;
        }
        try {
            AiGenerationLog row = new AiGenerationLog();
            row.setExplainId(explainId);
            row.setTraceId(traceId);
            row.setUserId(userId);
            row.setLogId(logId);
            row.setScene("RAG");
            row.setModel(model);
            row.setPromptVersion(promptVersion);
            row.setIsDegraded(degraded ? 1 : 0);
            row.setDegradeLevel(degradeLevel);
            row.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            row.setPromptTokens(promptTokens);
            row.setCompletionTokens(completionTokens);
            row.setSourceIds(objectMapper.writeValueAsString(List.of(kpId)));
            if (content.length() > 20000) {
                log.warn("讲解正文超 20000 字符，落库截断 explainId={} length={}",
                        explainId, content.length());
                content = content.substring(0, 20000);
            }
            row.setContentText(content);
            row.setIsBlocked(blocked ? 1 : 0);
            aiGenerationLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("AI 讲解留痕写入失败 explainId={}", explainId, e);
        }
    }

    private String first(String text, int limit) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = text.replace("\n", "；").trim();
        return clean.length() <= limit ? clean : clean.substring(0, limit);
    }

    /** 发送对象并按 JSON 序列化；任何 IO 异常都不能影响后续事件。 */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            sendRaw(emitter, event, json);
        } catch (Exception e) {
            log.debug("SSE 序列化失败：{}", e.toString());
        }
    }

    private void sendRaw(SseEmitter emitter, String event, String data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data, TEXT_PLAIN_UTF8));
            }
        } catch (Exception e) {
            log.debug("SSE 发送失败（客户端可能已断开）：{}", e.toString());
        }
    }

    /** 契约 5.2：心跳是注释帧 `: heartbeat`，不是 event/data */
    private void sendHeartbeat(SseEmitter emitter) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            }
        } catch (Exception e) {
            log.debug("SSE 心跳发送失败：{}", e.toString());
        }
    }

    /** UUID 后缀保证同一秒内并发讲解不撞号（explain_id 无唯一键，靠生成侧避免歧义） */
    private String nextExplainId() {
        return "EX_" + LocalDateTime.now().format(ID_FORMAT) + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void dispose(Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdownPools() {
        heartbeatPool.shutdownNow();
        fallbackPool.shutdownNow();
    }
}
