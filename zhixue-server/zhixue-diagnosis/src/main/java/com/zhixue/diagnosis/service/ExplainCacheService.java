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
 * 讲解结果缓存（契约 V2 §6）：key = sha256(kpId + errorType + correctedText + conceptHash)，TTL 7 天。
 * <p>
 * 只缓存 L1 正常完成、未拦截、有正文的结果；Redis 不可用时跳过缓存直调模型，绝不返回 500。
 */
@Service
public class ExplainCacheService {

    private static final Logger log = LoggerFactory.getLogger(ExplainCacheService.class);

    /** 契约 §6：TTL 7 天 */
    private static final Duration TTL = Duration.ofDays(7);

    private static final String KEY_PREFIX = "zhixue:explain:cache:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ExplainCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 计算契约缓存 key：sha256(kpId + errorType + correctedText)。
     *
     * @param kpId         知识点编号
     * @param errorType    五类错因之一
     * @param questionText 学生题干（契约 correctedText）
     * @return Redis 键
     */
    public String key(String kpId, String errorType, String questionText) {
        String raw = nullToEmpty(kpId) + nullToEmpty(errorType) + nullToEmpty(questionText);
        return KEY_PREFIX + sha256(raw);
    }

    /** 概念原文参与版本计算：concept_text 改变后，旧 key 不再命中。 */
    public String key(String kpId, String errorType, String questionText, String conceptText) {
        String raw = nullToEmpty(kpId) + nullToEmpty(errorType) + nullToEmpty(questionText)
                + "|concept:" + sha256(nullToEmpty(conceptText));
        return KEY_PREFIX + sha256(raw);
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

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
        }
    }

    private String nullToEmpty(String value) {
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
