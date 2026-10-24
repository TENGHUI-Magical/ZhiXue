package com.zhixue.diagnosis.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.dto.ExplainRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    /** 契约 ExplainRequest.errorType 的五个英文枚举 */
    private static final Set<String> ERROR_TYPES = Set.of(
            "CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
            "CALCULATION_ERROR", "BOUNDARY_ERROR");

    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final KnowledgePointService knowledgePointService;
    private final StudentErrorRecordService errorRecordService;
    private final AiClientProperties aiClientProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    private final ScheduledExecutorService heartbeatPool =
            Executors.newSingleThreadScheduledExecutor(daemonFactory("sse-heartbeat"));
    private final ExecutorService fallbackPool =
            Executors.newCachedThreadPool(daemonFactory("sse-fallback"));

    public RagExplainService(KnowledgePointService knowledgePointService,
                             StudentErrorRecordService errorRecordService,
                             AiClientProperties aiClientProperties,
                             ObjectMapper objectMapper) {
        this.knowledgePointService = knowledgePointService;
        this.errorRecordService = errorRecordService;
        this.aiClientProperties = aiClientProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(aiClientProperties.baseUrl()).build();
    }

    /**
     * 建立 SSE 连接并开始转发讲解。
     *
     * @param request 契约 ExplainRequest；竖切阶段 logId 允许为空
     * @return SSE 发射器；知识点不存在或 errorType 非法时先发 error 事件再关闭
     */
    public SseEmitter explain(ExplainRequest request) {
        SseEmitter emitter = new SseEmitter(TOTAL_TIMEOUT_MS);
        long start = System.currentTimeMillis();
        String explainId = nextExplainId();

        KnowledgePoint kp;
        String errorType;
        try {
            // 契约前置：必须是 CONFIRMED 错题记录，且记录里的 kpId 与请求一致
            var record = errorRecordService.getConfirmed(request.getLogId());
            if (!record.getKpId().equals(request.getKpId())) {
                sendError(emitter, ErrorCode.DUPLICATE_SUBMIT.getCode(),
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

        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean chunkReceived = new AtomicBoolean(false);
        AtomicInteger characters = new AtomicInteger();
        AtomicInteger tokens = new AtomicInteger();
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
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onError(e -> cleanup.run());
        emitter.onTimeout(() -> {
            cleanup.run();
            sendError(emitter, ErrorCode.AI_TIMEOUT.getCode(), "讲解超时，请重试", true);
            emitter.complete();
        });

        sendMeta(emitter, explainId);
        sendSource(emitter, kp);

        Map<String, Object> payload = buildUpstreamPayload(request, kp, errorType);
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
                            // meta/source 由 Java 统一发出，丢弃上游同名事件，避免重复
                        }
                        case "chunk" -> {
                            String text = readContent(event.data());
                            if (!text.isEmpty()) {
                                chunkReceived.set(true);
                                characters.addAndGet(text.length());
                                send(emitter, "delta", Map.of("text", text));
                            }
                        }
                        case "degraded" -> sendRaw(emitter, "degraded", event.data());
                        case "done" -> tokens.set(readTokens(event.data()));
                        default -> {
                            // 未知事件忽略，保持前向兼容
                        }
                    }
                },
                error -> {
                    log.warn("AI 讲解上游失败，切本地模板：{}", error.toString());
                    fallback(emitter, kp, request, explainId, start, chunkReceived,
                            characters, cleanup);
                },
                () -> {
                    sendDone(emitter, explainId, start, tokens.get());
                    emitter.complete();
                    cleanup.run();
                });
        upstream.set(subscription);
        return emitter;
    }

    /** 上游不可用时，用概念切片本地拼讲解；已收到部分 delta 时只补降级标记与 done。 */
    private void fallback(SseEmitter emitter, KnowledgePoint kp, ExplainRequest request,
                          String explainId, long start, AtomicBoolean chunkReceived,
                          AtomicInteger characters, Runnable cleanup) {
        fallbackPool.execute(() -> {
            try {
                if (!chunkReceived.get()) {
                    send(emitter, "degraded", Map.of("level", "L3", "reason", "AI 服务不可用"));
                    for (String part : localTemplate(kp, request)) {
                        characters.addAndGet(part.length());
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

    private void sendMeta(SseEmitter emitter, String explainId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("explainId", explainId);
        meta.put("traceId", "t-" + explainId);
        meta.put("model", META_MODEL);
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

    private void sendDone(SseEmitter emitter, String explainId, long start, int tokens) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("explainId", explainId);
        done.put("latencyMs", System.currentTimeMillis() - start);
        done.put("tokens", tokens);
        send(emitter, "done", done);
    }

    private void sendError(SseEmitter emitter, int code, String message, boolean retryable) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        send(emitter, "error", error);
    }

    private Map<String, Object> buildUpstreamPayload(ExplainRequest request, KnowledgePoint kp,
                                                     String errorType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kpId", kp.getKpId());
        payload.put("kpName", kp.getName());
        payload.put("conceptText", kp.getConceptText());
        payload.put("misconception", kp.getMisconception());
        payload.put("textbookPage", kp.getTextbookPage());
        payload.put("questionText", request.getQuestionText());
        payload.put("errorType", errorType);
        payload.put("courseId", 1L);
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

    /** 上游 done 的 data 是 {"tokens":N,...}；缺失时返回 0 */
    private int readTokens(String data) {
        if (data == null || data.isBlank()) {
            return 0;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(data,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object tokens = map.get("tokens");
            return tokens instanceof Number number ? number.intValue() : 0;
        } catch (Exception e) {
            log.debug("上游 done 解析失败：{}", e.toString());
            return 0;
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
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.TEXT_PLAIN));
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

    private String nextExplainId() {
        return "EX_" + LocalDateTime.now().format(ID_FORMAT) + "_"
                + Integer.toHexString((int) (System.nanoTime() & 0xffff));
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
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
