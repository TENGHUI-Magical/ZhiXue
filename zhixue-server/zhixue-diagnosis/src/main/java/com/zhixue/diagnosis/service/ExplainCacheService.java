package com.zhixue.diagnosis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 讲解结果缓存（契约 V2 §6）：key = promptVersion + contextHash（全部 prompt 组装材料），TTL 7 天。
 * <p>
 * 只缓存 L1 正常完成、未拦截、有正文的结果；Redis 不可用时跳过缓存直调模型，绝不返回 500。
 */
@Service
public class ExplainCacheService {

    private static final Logger log = LoggerFactory.getLogger(ExplainCacheService.class);

    /** 契约 §6：TTL 7 天 */
    private static final Duration TTL = Duration.ofDays(7);

    private static final String KEY_PREFIX = "zhixue:explain:cache:";

    /**
     * 讲解上下文缓存键版本：prompt 材料集合或组装语义变化时 bump（ctx-v1 → ctx-v2），
     * 旧缓存整代失效（TTL 7 天兜底清空，无需迁移）。与上游返回的 promptVersion（模型模板版本，
     * 记录在缓存值与 ai_generation_log）职责不同：这里管的是 Java → Python 的材料契约。
     */
    static final String CONTEXT_VERSION = "ctx-v1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ExplainCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 计算缓存键：sha256(版本 + 全部 prompt 组装材料)。
     * 材料与 {@code RagExplainService#buildUpstreamPayload} 一一对应（traceId/logId/forceRefresh
     * 等非语义字段除外）——保证“生成依据”与“缓存键依据”是同一份材料，
     * 任一材料变化（concept_text 勘误、misconception 维护、题库标准答案更新、页码勘误）即失效。
     *
     * @param kpId         知识点编号
     * @param kpName       知识点名称（进 prompt）
     * @param conceptText  概念切片
     * @param misconception 常见误区（内容线维护）
     * @param textbookPage 教材页码
     * @param errorType    五类错因之一
     * @param questionText 学生题干（契约 correctedText）
     * @param answerText   题目标准答案（题库维护）
     * @return Redis 键
     */
    public String key(String kpId, String kpName, String conceptText, String misconception,
                      String textbookPage, String errorType, String questionText, String answerText) {
        return key(CONTEXT_VERSION, kpId, kpName, conceptText, misconception,
                textbookPage, errorType, questionText, answerText);
    }

    /** 带版本的键计算（包私有供单测验证版本参与；线上入口固定用 {@link #CONTEXT_VERSION}）。 */
    static String key(String version, String kpId, String kpName, String conceptText,
                      String misconception, String textbookPage, String errorType,
                      String questionText, String answerText) {
        String contextHash = sha256(String.join("\u0001",
                nullToEmpty(kpId), nullToEmpty(kpName), nullToEmpty(conceptText),
                nullToEmpty(misconception), nullToEmpty(textbookPage), nullToEmpty(errorType),
                nullToEmpty(questionText), nullToEmpty(answerText)));
        return KEY_PREFIX + version + ":" + sha256(version + ":" + contextHash);
    }

    /**
     * 读缓存；未命中、内容损坏或 Redis 故障均返回 null。
     *
     * @param key {@link #key} 生成
     * @return 缓存内容；null 表示未命中或不可用
     */
    public Cached get(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, Cached.class);
        } catch (Exception e) {
            log.warn("讲解缓存读取失败，跳过缓存：{}", e.toString());
            return null;
        }
    }

    /**
     * 写缓存；失败只记日志，不影响讲解主流程。
     *
     * @param key    缓存键
     * @param cached 讲解快照
     */
    public void put(String key, Cached cached) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(cached), TTL);
        } catch (Exception e) {
            log.warn("讲解缓存写入失败 key={} reason={}", key, e.toString());
        }
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 缓存快照（只存非降级、非拦截的完整讲解）。
     *
     * @param content       讲解全文
     * @param model         生成模型名
     * @param promptVersion Prompt 版本
     * @param sources       来源列表
     * @param createdAt     生成时间（ISO-8601）
     */
    public record Cached(String content, String model, String promptVersion,
                         List<Source> sources, String createdAt) {
    }

    /**
     * 来源引用（契约 SSE source 事件字段）。
     *
     * @param kpId         知识点编号
     * @param textbookPage 教材页码
     * @param title        知识点名称
     */
    public record Source(String kpId, String textbookPage, String title) {
    }
}
