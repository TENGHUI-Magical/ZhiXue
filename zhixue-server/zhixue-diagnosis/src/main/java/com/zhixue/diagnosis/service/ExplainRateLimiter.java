package com.zhixue.diagnosis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 讲解接口限流（契约 V2 §8：按用户 10 次/分钟，超出 42900 并带 Retry-After）。
 * <p>
 * Redis 固定窗口计数（INCR + EXPIRE）；Redis 不可用时降级放行，不因限流组件故障阻断讲解。
 */
@Service
public class ExplainRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ExplainRateLimiter.class);

    /** 契约 §8：10 次/分钟/用户 */
    private static final int LIMIT_PER_MINUTE = 10;

    private static final long WINDOW_SECONDS = 60L;

    private static final String KEY_PREFIX = "zhixue:rate:explain:";

    private final StringRedisTemplate redisTemplate;

    public ExplainRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取一次讲解配额。
     *
     * @param userId 当前登录用户
     * @return allowed=false 时 retryAfterSeconds 为建议等待秒数
     */
    public Decision acquire(long userId) {
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - (now % WINDOW_SECONDS);
        String key = KEY_PREFIX + userId + ":" + windowStart;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return Decision.allow();
            }
            if (count == 1L) {
                // 窗口双倍过期，避免边界残留；计数器本身按分钟自然失效
                redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS * 2));
            }
            if (count > LIMIT_PER_MINUTE) {
                long retryAfter = Math.max(1L, WINDOW_SECONDS - (now % WINDOW_SECONDS));
                return new Decision(false, retryAfter);
            }
            return Decision.allow();
        } catch (Exception e) {
            log.warn("讲解限流 Redis 不可用，降级放行 userId={} reason={}", userId, e.toString());
            return Decision.allow();
        }
    }

    /**
     * 限流判定结果。
     *
     * @param allowed           是否放行
     * @param retryAfterSeconds 建议等待秒数；放行时为 0
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision allow() {
            return new Decision(true, 0L);
        }
    }
}
