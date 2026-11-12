package com.zhixue.diagnosis.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import jakarta.annotation.PreDestroy;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.dto.ExplainRequest;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.function.Consumer;
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
    /** 统一时区口径：缓存时间戳与新鲜度判定不能随 JVM 默认时区漂移（docs/13 B 组） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 整体超时：契约 5.2 节 */
    private static final long TOTAL_TIMEOUT_MS = 60_000L;

    /** 心跳间隔：契约 5.2 节 */
    private static final long HEARTBEAT_SECONDS = 15L;

    /** meta.model 暂用 auto；接入模型配置同步后改为真实模型名 */
    private static final String META_MODEL = "auto";

    /**
     * 降级级别与模型名常量：与 zhixue-ai/app/services/rag_service.py
     * 的 LEVEL_L2 / LEVEL_L3 / MODEL_LOCAL_TEMPLATE 同口径，任一侧改动必须同步另一侧。
     */
    private static final String LEVEL_L2 = "L2";
    private static final String LEVEL_L3 = "L3";
    private static final String MODEL_LOCAL_TEMPLATE = "local-template";
    private static final String MODEL_UNKNOWN = "unknown";

    /** Prompt 模板版本：必须与 zhixue-ai/app/services/rag_service.py 的 PROMPT_VERSION 一致（单测校验）。 */
    static final String PROMPT_VERSION = "v2";
    private static final int PREREQ_MIN_SAMPLES = 3;
    private static final String PRACTICE_HINT = "请完成下方同类练习，检验自己是否真正掌握。";
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "\\A\\s*【错因定位】\\s*(.*?)\\s*【完整讲解】\\s*(.*?)\\s*【自查】\\s*(.*?)\\s*\\z",
            Pattern.DOTALL);
    private static final Pattern MISCONCEPTION_SPLIT = Pattern.compile("(?:\\r?\\n|\\\\n)");

    private static final Pattern PAGE_CITATION = Pattern.compile(
            "(?<![A-Za-z0-9])P\\s*(\\d+)(?:\\s*[—–-]\\s*(\\d+))?"
                    + "|第\\s*(\\d+)(?:\\s*[—–-]\\s*(\\d+))?\\s*页",
            Pattern.CASE_INSENSITIVE);

    /** 教材页码上限位数：4 位（9999 页）足够覆盖任何教材，更长数字串按脏数据忽略。 */
    private static final int MAX_PAGE_DIGITS = 4;

    /** 单条引用区间上限：500 页；超过视为脏数据，防止数亿级区间撑爆内存或长时间占满 CPU。 */
    private static final int MAX_PAGE_SPAN = 500;

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
    private final PracticeRecordMapper practiceRecordMapper;
    private final PortraitService portraitService;
    private final MasteryCalculator masteryCalculator;
    private final WebClient webClient;

    record ExplanationContext(String myAnswer, int attemptCount, int wrongCount,
                              PrereqWeakness prereqWeakness) {
    }

    record PrereqWeakness(String kpId, String kpName, double mastery,
                          int sampleCount, String conceptText) {
    }

    private record PrereqCandidate(String kpId, double mastery) {
    }

    /**
     * 降级判定结果。
     *
     * @param level    降级级别；null 表示 L1（主模型完整产出，不写降级级别）
     * @param model    落库 model；L3 本地模板为 local-template，L2 保留实际模型名
     * @param degraded 是否降级
     */
    record DegradeOutcome(String level, String model, boolean degraded) {
    }

    /**
     * 降级口径唯一判定处（与 zhixue-ai/app/services/rag_service.py 的 LEVEL_L2/LEVEL_L3 同口径）。
     * <ul>
     *   <li>L1：主模型完整产出，degrade_level=null，model 为实际模型；</li>
     *   <li>L2：有正文但非 L1 完整产出——备用模型完整产出，或远程流中断/超时截断；
     *       保留实际模型名，degrade_level=L2 即截断/降级标记；</li>
     *   <li>L3：本地模板产出（远程零产出），model=local-template；</li>
     *   <li>零正文且未产出模板（首字节超时、客户端过早断开）：仍记 L3，model 保留实际值
     *       或 unknown，不伪造 local-template。</li>
     * </ul>
     *
     * @param hasContent    已收集正文长度 &gt; 0
     * @param streamBroken  流未正常结束（未收到上游 done / 上游异常 / 整体超时 / 客户端断开）
     * @param signaledLevel 上游 degraded 事件声明的级别（L2/L3），无则 null
     * @param actualModel   当前已知的实际模型名
     * @return 统一的降级判定结果
     */
    static DegradeOutcome classifyDegrade(boolean hasContent, boolean streamBroken,
                                          String signaledLevel, String actualModel) {
        String model = actualModel == null || actualModel.isBlank() ? MODEL_UNKNOWN : actualModel;
        if (LEVEL_L3.equals(signaledLevel)) {
            // 上游已产出本地学习卡，或 Java 自己补齐模板：L3 一律记 local-template
            return new DegradeOutcome(LEVEL_L3, MODEL_LOCAL_TEMPLATE, true);
        }
        if (hasContent) {
            if (streamBroken) {
                // 有正文但流被打断/超时：部分产出，绝不能记成 L3 或 local-template
                return new DegradeOutcome(LEVEL_L2, model, true);
            }
            if (signaledLevel != null && !signaledLevel.isBlank()) {
                // 备用模型完整产出（现有 L2 语义），保留实际模型名
                return new DegradeOutcome(signaledLevel, model, true);
            }
            return new DegradeOutcome(null, model, false);
        }
        return new DegradeOutcome(LEVEL_L3, model, true);
    }

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
                             ExplainCacheService explainCacheService,
                             PracticeRecordMapper practiceRecordMapper,
                             PortraitService portraitService,
                             @Value("${mastery.half-life-days:30}") double halfLifeDays) {
        this.knowledgePointService = knowledgePointService;
        this.errorRecordService = errorRecordService;
        this.questionBankService = questionBankService;
        this.aiClientProperties = aiClientProperties;
        this.objectMapper = objectMapper;
        this.aiGenerationLogMapper = aiGenerationLogMapper;
        this.explainCacheService = explainCacheService;
        this.practiceRecordMapper = practiceRecordMapper;
        this.portraitService = portraitService;
        this.masteryCalculator = new MasteryCalculator(halfLifeDays);
        this.webClient = buildAiWebClient(aiClientProperties);
        this.heartbeatPool.setRemoveOnCancelPolicy(true);
    }

    /** 构建带超时与内部鉴权头的 WebClient（P0 修复：连接 2s / 响应 10s）。 */
    private static WebClient buildAiWebClient(AiClientProperties props) {
        var httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(java.time.Duration.ofSeconds(10));
        return WebClient.builder()
                .baseUrl(props.baseUrl())
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Internal-Token", props.internalToken() == null ? "" : props.internalToken())
                .build();
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

        // 同一份规范化上下文同时用于缓存键和上游 prompt；任一学生证据变化都会换键。
        String cacheText = resolveCacheText(record, request);
        ExplanationContext explanationContext = buildExplanationContext(userId, record, kp);
        String canonicalContext = canonicalContext(userId, record, kp, errorType, cacheText,
                answerText, explanationContext);
        String cacheKey = explainCacheService.key(PROMPT_VERSION, canonicalContext);
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
                sendDone(emitter, explainId, start, 0, true, replay, kp);
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
        AtomicBoolean upstreamDone = new AtomicBoolean(false);
        AtomicBoolean upstreamError = new AtomicBoolean(false);
        AtomicBoolean degradedEventSent = new AtomicBoolean(false);
        AtomicInteger characters = new AtomicInteger();
        AtomicReference<Disposable> upstream = new AtomicReference<>();

        ScheduledFuture<?> heartbeat = heartbeatPool.scheduleAtFixedRate(
                () -> {
                    if (!closed.get()) {
                        sendHeartbeat(emitter);
                    }
                },
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        // streamBroken：流没有正常收尾（未收到上游 done / 上游异常 / 整体超时 / 客户端断开）。
        // 落库级别一律经 classifyDegrade，避免 L2 部分产出被散落的 if 误标成 L3。
        Consumer<Boolean> cleanup = streamBroken -> {
            if (closed.compareAndSet(false, true)) {
                heartbeat.cancel(false);
                dispose(upstream.get());
                DegradeOutcome outcome = blocked.get()
                        ? new DegradeOutcome(null, modelUsed.get(), false)
                        : classifyDegrade(content.length() > 0, streamBroken,
                        degradeLevel.get(), modelUsed.get());
                // 客户端断开也要留痕：契约 5.3 要求已生成内容与耗时照常记录
                fallbackPool.execute(() -> writeLog(explainId, traceId, userId, request.getLogId(),
                        kp.getKpId(), outcome.model(), outcome.degraded(), outcome.level(),
                        System.currentTimeMillis() - start, promptVersion.get(),
                        promptTokens.get(), completionTokens.get(),
                        content.toString(), blocked.get(), logged));
            }
        };
        emitter.onCompletion(() -> cleanup.accept(!upstreamDone.get()));
        emitter.onError(e -> cleanup.accept(true));
        emitter.onTimeout(() -> {
            cleanup.accept(true);
            sendError(emitter, ErrorCode.AI_TIMEOUT.getCode(), "讲解超时，请重试", true);
            emitter.complete();
        });

        sendMeta(emitter, explainId, traceId, META_MODEL);
        sendSource(emitter, kp);

        Map<String, Object> payload = buildUpstreamPayload(
                request, kp, errorType, traceId, cacheText, answerText, explanationContext);
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
                            // 上游 meta 的 model 是服务间内部字段（不下发前端），用于流中断时保留真实模型名
                            String model = meta.get("model") == null ? null : String.valueOf(meta.get("model"));
                            if (model != null && !model.isBlank() && !META_MODEL.equals(model)) {
                                modelUsed.set(model);
                            }
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
                            degradedEventSent.set(true);
                            String level = readString(event.data(), "level");
                            if (level != null && !level.isBlank()) {
                                degradeLevel.set(level);
                            }
                            // modelUsed 是服务间内部字段：只用于留痕，不下发给前端（契约 §5.1 只暴露 level/reason）
                            String model = readString(event.data(), "modelUsed");
                            if (model != null && !model.isBlank()) {
                                modelUsed.set(model);
                            }
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("level", degradeLevel.get());
                            String reason = readString(event.data(), "reason");
                            if (reason != null) {
                                out.put("reason", reason);
                            }
                            send(emitter, "degraded", out);
                        }
                        case "done" -> {
                            upstreamDone.set(true);
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
                            upstreamError.set(true);
                            emitter.complete();
                            cleanup.accept(true);
                        }
                        default -> {
                            // 未知事件忽略，保持前向兼容
                        }
                    }
                },
                error -> {
                    log.warn("AI 讲解上游失败，按已产出内容标记降级：{}", error.toString());
                    fallback(emitter, kp, request, explainId, start, chunkReceived,
                            characters, cleanup, content, modelUsed, degraded, degradeLevel,
                            degradedEventSent);
                },
                () -> {
                    if (upstreamError.get()) {
                        // 上游已发 error 事件并由该分支收尾，不再补模板/done
                        cleanup.accept(true);
                        return;
                    }
                    // 未收到上游 done 就结束 = 流被截断（如连接被干净关闭），同样按部分产出留痕
                    boolean streamBroken = !upstreamDone.get();
                    if (streamBroken && content.length() == 0) {
                        // 零产出等价于上游不可用：走同一兜底（由 classifyDegrade 判为 L3）
                        log.warn("AI 讲解上游提前结束且无正文，切本地模板");
                        fallback(emitter, kp, request, explainId, start, chunkReceived,
                                characters, cleanup, content, modelUsed, degraded, degradeLevel,
                                degradedEventSent);
                        return;
                    }
                    if (streamBroken) {
                        degraded.set(true);
                        DegradeOutcome outcome = classifyDegrade(content.length() > 0, true,
                                degradeLevel.get(), modelUsed.get());
                        degradeLevel.set(outcome.level());
                        modelUsed.set(outcome.model());
                        if (!degradedEventSent.getAndSet(true)) {
                            send(emitter, "degraded", Map.of("level", outcome.level(),
                                    "reason", "上游流中断，已返回部分内容"));
                        }
                    } else {
                        saveCache(cacheKey, kp, content, modelUsed, promptVersion, degraded, blocked);
                    }
                    sendDone(emitter, explainId, start, completionTokens.get(), false,
                            content.toString(), kp);
                    emitter.complete();
                    cleanup.accept(streamBroken);
                });
        upstream.set(subscription);
        return emitter;
    }

    /**
     * 上游不可用时由 Java 兜底：远程零产出才用本地模板；已有部分正文则只标记部分产出（L2）。
     * 级别与 model 统一由 {@link #classifyDegrade} 判定，禁止在这里另写 if 分支。
     */
    private void fallback(SseEmitter emitter, KnowledgePoint kp, ExplainRequest request,
                          String explainId, long start, AtomicBoolean chunkReceived,
                          AtomicInteger characters, Consumer<Boolean> cleanup, StringBuffer content,
                          AtomicReference<String> modelUsed, AtomicBoolean degraded,
                          AtomicReference<String> degradeLevel, AtomicBoolean degradedEventSent) {
        fallbackPool.execute(() -> {
            try {
                degraded.set(true);
                boolean remoteContent = chunkReceived.get() || content.length() > 0;
                if (!remoteContent) {
                    // 远程零产出：真正回退本地模板，model 才写 local-template
                    degradeLevel.set(LEVEL_L3);
                    modelUsed.set(MODEL_LOCAL_TEMPLATE);
                }
                DegradeOutcome outcome = classifyDegrade(remoteContent, true,
                        degradeLevel.get(), modelUsed.get());
                degradeLevel.set(outcome.level());
                modelUsed.set(outcome.model());
                if (!degradedEventSent.getAndSet(true)) {
                    send(emitter, "degraded", Map.of("level", outcome.level(), "reason",
                            remoteContent ? "上游流中断，已返回部分内容" : "AI 服务不可用，已切换本地学习卡"));
                }
                if (!remoteContent) {
                    for (String part : localTemplate(kp, request)) {
                        characters.addAndGet(part.length());
                        content.append(part);
                        send(emitter, "delta", Map.of("text", part));
                        sleepQuietly(30);
                    }
                }
                sendDone(emitter, explainId, start, 0, false, content.toString(), kp);
                emitter.complete();
            } catch (Exception e) {
                log.warn("本地模板讲解发送失败", e);
                emitter.completeWithError(e);
            } finally {
                cleanup.accept(true);
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

    /** 解析引用页码（包级可见，便于单测覆盖脏数据边界）。 */
    Set<Integer> pageNumbers(String text) {
        Set<Integer> pages = new HashSet<>();
        if (text == null) {
            return pages;
        }
        Matcher matcher = PAGE_CITATION.matcher(text);
        while (matcher.find()) {
            String startValue = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String endValue = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            // 缓存正文与教材页码都不是受控输入：先按位数筛掉超长数字串，
            // 否则裸 parseInt 抛 NumberFormatException 会直接打挂讲解接口。
            if (!isPageNumber(startValue) || (endValue != null && !isPageNumber(endValue))) {
                continue;
            }
            // 全部转 long 计算，避免 int 自增在上界 Integer.MAX_VALUE 处回绕造成死循环。
            long start = Long.parseLong(startValue);
            long end = endValue == null ? start : Long.parseLong(endValue);
            long lo = Math.min(start, end);
            long hi = Math.max(start, end);
            // 单条引用区间超过阈值视为脏数据并忽略整段：正常教材引用远小于该跨度，
            // 不设上限时 1—20 亿的区间会向 HashSet 塞数亿整数（OOM）或长时间占满 CPU。
            if (hi - lo + 1 > MAX_PAGE_SPAN) {
                log.warn("讲解页码引用区间异常，已忽略 text={}", matcher.group());
                continue;
            }
            for (long page = lo; page <= hi; page++) {
                pages.add((int) page);
            }
        }
        return pages;
    }

    /** 教材页码最多 4 位（9999 页）；超长数字串不是页码，按脏数据拒绝。 */
    private static boolean isPageNumber(String value) {
        return value != null && value.length() <= MAX_PAGE_DIGITS;
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

    private void sendDone(SseEmitter emitter, String explainId, long start, int tokens,
                          boolean cached, String content, KnowledgePoint kp) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("explainId", explainId);
        done.put("latencyMs", System.currentTimeMillis() - start);
        done.put("tokens", tokens);
        if (cached) {
            done.put("cached", true);
        }
        done.putAll(structuredOutput(content, kp));
        send(emitter, "done", done);
    }

    /** 正常讲解返回三段字段；学习卡返回定位、页码、最多三条误区与练习提示。 */
    Map<String, Object> structuredOutput(String content, KnowledgePoint kp) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (content == null) {
            return output;
        }
        if (content.strip().startsWith("【学习卡】")) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("kpName", kp.getName() == null ? kp.getKpId() : kp.getName());
            card.put("textbookPage", kp.getTextbookPage() == null ? "页码待确认" : kp.getTextbookPage());
            card.put("misconceptions", misconceptions(kp.getMisconception(), 3));
            card.put("practiceHint", PRACTICE_HINT);
            output.put("learningCard", card);
            return output;
        }
        Matcher matcher = SECTION_PATTERN.matcher(content);
        if (!matcher.matches()) {
            return output;
        }
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("errorLocation", matcher.group(1).trim());
        sections.put("explanation", matcher.group(2).trim());
        sections.put("selfCheck", matcher.group(3).trim());
        if (sections.values().stream().allMatch(value -> !String.valueOf(value).isBlank())) {
            output.put("sections", sections);
        }
        return output;
    }

    /**
     * L1 正常完成且未降级/未拦截时写缓存；L2/L3 与空正文不缓存，避免把降级结果固化 7 天。
     */
    private void saveCache(String cacheKey, KnowledgePoint kp, StringBuffer content,
                           AtomicReference<String> modelUsed, AtomicReference<String> promptVersion,
                           AtomicBoolean degraded, AtomicBoolean blocked) {
        if (degraded.get() || blocked.get() || content.length() == 0
                || !PROMPT_VERSION.equals(promptVersion.get())) {
            return;
        }
        ExplainCacheService.Cached cached = new ExplainCacheService.Cached(
                content.toString(), modelUsed.get(), promptVersion.get(),
                List.of(new ExplainCacheService.Source(kp.getKpId(), kp.getTextbookPage(), kp.getName())),
                LocalDateTime.now(ZONE).toString());
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

    /** 只取该题最近一条作答与聚合计数，不把练习历史明细塞进 Prompt。 */
    ExplanationContext buildExplanationContext(long userId, StudentErrorRecord record, KnowledgePoint kp) {
        String myAnswer = null;
        int attemptCount = 0;
        int wrongCount = 0;
        String qId = record.getQId();
        if (qId != null && !qId.isBlank()) {
            PracticeRecord latest = practiceRecordMapper.selectOne(new LambdaQueryWrapper<PracticeRecord>()
                    .eq(PracticeRecord::getUserId, userId)
                    .eq(PracticeRecord::getQId, qId)
                    .orderByDesc(PracticeRecord::getCreatedAt)
                    .orderByDesc(PracticeRecord::getPracticeId)
                    .last("LIMIT 1"));
            myAnswer = latest == null ? null : latest.getMyAnswer();
            attemptCount = practiceRecordMapper.countByUserAndQid(userId, qId);
            Long wrong = practiceRecordMapper.selectCount(new LambdaQueryWrapper<PracticeRecord>()
                    .eq(PracticeRecord::getUserId, userId)
                    .eq(PracticeRecord::getQId, qId)
                    .eq(PracticeRecord::getIsCorrect, 0));
            wrongCount = Math.toIntExact(wrong);
        }
        return new ExplanationContext(myAnswer, attemptCount, wrongCount,
                weakestPrereq(userId, kp.getKpId()));
    }

    /** 直接先修中只选样本数 >=3 且有错误证据的最低掌握度项，同分按 kpId 保证稳定。 */
    private PrereqWeakness weakestPrereq(long userId, String kpId) {
        List<String> prereqIds = knowledgePointService.prerequisites(kpId);
        if (prereqIds.isEmpty()) {
            return null;
        }
        Map<String, UserKpAgg> aggregates = portraitService.userKpAggregates(userId);
        List<PrereqCandidate> candidates = new ArrayList<>();
        for (String prereqId : prereqIds) {
            UserKpAgg agg = aggregates.get(prereqId);
            if (agg == null || agg.getEvents() == null || agg.getEvents() < PREREQ_MIN_SAMPLES
                    || agg.getErrorCount() == null || agg.getErrorCount() == 0) {
                continue;
            }
            double mastery = masteryCalculator.mastery(
                    agg.getSuccessWeight() == null ? 0.0 : agg.getSuccessWeight(),
                    agg.getSeveritySum() == null ? 0.0 : agg.getSeveritySum());
            candidates.add(new PrereqCandidate(prereqId, mastery));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(PrereqCandidate::mastery)
                .thenComparing(PrereqCandidate::kpId));
        PrereqCandidate weakest = candidates.get(0);
        KnowledgePoint prereq = knowledgePointService.getById(weakest.kpId());
        if (prereq == null) {
            return null;
        }
        UserKpAgg agg = aggregates.get(weakest.kpId());
        return new PrereqWeakness(prereq.getKpId(),
                prereq.getName() == null ? prereq.getKpId() : prereq.getName(),
                weakest.mastery(), agg.getEvents(), prereq.getConceptText());
    }

    /** 固定字段顺序序列化，避免缓存键受 Map 遍历顺序影响。 */
    String canonicalContext(long userId, StudentErrorRecord record, KnowledgePoint kp,
                            String errorType, String questionText, String answerText,
                            ExplanationContext context) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("userId", userId);
        canonical.put("courseId", kp.getCourseId() == null ? 1L : kp.getCourseId());
        canonical.put("kpId", kp.getKpId());
        canonical.put("kpName", kp.getName());
        canonical.put("qId", record.getQId());
        canonical.put("errorType", errorType);
        canonical.put("questionText", questionText);
        canonical.put("conceptText", kp.getConceptText());
        canonical.put("misconception", kp.getMisconception());
        canonical.put("textbookPage", kp.getTextbookPage());
        canonical.put("answerText", answerText);
        canonical.put("myAnswer", context.myAnswer());
        canonical.put("attemptCount", context.attemptCount());
        canonical.put("wrongCount", context.wrongCount());
        canonical.put("prereqWeakness", prereqPayload(context.prereqWeakness()));
        return objectMapper.writeValueAsString(canonical);
    }

    private Map<String, Object> prereqPayload(PrereqWeakness weakness) {
        if (weakness == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kpId", weakness.kpId());
        payload.put("kpName", weakness.kpName());
        payload.put("mastery", weakness.mastery());
        payload.put("sampleCount", weakness.sampleCount());
        payload.put("conceptText", weakness.conceptText());
        return payload;
    }

    Map<String, Object> buildUpstreamPayload(ExplainRequest request, KnowledgePoint kp,
                                             String errorType, String traceId,
                                             String questionText, String answerText,
                                             ExplanationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("kpId", kp.getKpId());
        payload.put("kpName", kp.getName());
        payload.put("conceptText", kp.getConceptText());
        payload.put("misconception", kp.getMisconception());
        payload.put("textbookPage", kp.getTextbookPage());
        payload.put("questionText", questionText);
        payload.put("myAnswer", context.myAnswer());
        payload.put("attemptCount", context.attemptCount());
        payload.put("wrongCount", context.wrongCount());
        payload.put("prereqWeakness", prereqPayload(context.prereqWeakness()));
        payload.put("answerText", answerText);
        payload.put("errorType", errorType);
        payload.put("courseId", kp.getCourseId() == null ? 1L : kp.getCourseId());
        payload.put("logId", request.getLogId());
        payload.put("forceRefresh", Boolean.TRUE.equals(request.getForceRefresh()));
        return payload;
    }

    /** L3 学习卡：只给知识点定位、页码、最多三条误区与练习入口，不输出讲解长文。 */
    List<String> localTemplate(KnowledgePoint kp, ExplainRequest request) {
        String name = kp.getName() == null ? kp.getKpId() : kp.getName();
        String page = kp.getTextbookPage() == null ? "页码待确认" : kp.getTextbookPage();
        List<String> mistakes = misconceptions(kp.getMisconception(), 3);
        List<String> lines = new ArrayList<>();
        lines.add("【学习卡】");
        lines.add("【知识点】" + name + "；教材" + page);
        lines.add("【常见误区】");
        if (mistakes.isEmpty()) {
            lines.add("暂未登记常见误区，请对照教材概念自查。");
        } else {
            for (int i = 0; i < mistakes.size(); i++) {
                lines.add((i + 1) + ". " + mistakes.get(i));
            }
        }
        lines.add("【练习提示】" + PRACTICE_HINT);
        return List.of(String.join("\n", lines));
    }

    private List<String> misconceptions(String text, int limit) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(limit);
        for (String item : MISCONCEPTION_SPLIT.split(text)) {
            String clean = first(item, 90);
            if (!clean.isEmpty()) {
                result.add(clean);
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return result;
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
        return "EX_" + LocalDateTime.now(ZONE).format(ID_FORMAT) + "_"
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
