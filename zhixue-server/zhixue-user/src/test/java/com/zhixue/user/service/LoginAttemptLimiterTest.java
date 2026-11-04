package com.zhixue.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败限流（账号 + IP 双维度滑动窗口）：
 * 第 5 次失败即锁定、不同账号互不牵连、IP 维度跨账号聚合、成功清零。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        // 模拟窗口内计数：每个 key 依次 +1（不做窗口剔除，单测只验证阈值与隔离）
        Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
        when(zSetOperations.zCard(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        });
        limiter = new LoginAttemptLimiter(redisTemplate);
    }

    @Test
    void fifthAccountFailureReturnsRateLimitedWithRetryAfter() {
        LoginAttemptLimiter.Decision decision = null;
        for (int i = 1; i <= LoginAttemptLimiter.ACCOUNT_MAX_FAILURES; i++) {
            decision = limiter.recordFailure("alice", "10.0.0.1");
        }
        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(LoginAttemptLimiter.LOCK_BASE_SECONDS);
    }

    @Test
    void lockedAccountDoesNotAffectOtherAccount() {
        for (int i = 0; i < LoginAttemptLimiter.ACCOUNT_MAX_FAILURES; i++) {
            limiter.recordFailure("alice", "10.0.0.1");
        }
        when(redisTemplate.getExpire("zhixue:auth:lock:account:alice"))
                .thenReturn(LoginAttemptLimiter.LOCK_BASE_SECONDS);

        assertThat(limiter.check("alice", "10.0.0.1").allowed()).isFalse();
        assertThat(limiter.check("bob", "10.0.0.1").allowed()).isTrue();
    }

    @Test
    void twentyFailuresAcrossAccountsLockTheSharedIp() {
        LoginAttemptLimiter.Decision decision = null;
        for (int i = 0; i < LoginAttemptLimiter.IP_MAX_FAILURES; i++) {
            decision = limiter.recordFailure("user" + i, "203.0.113.9");
        }
        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isFalse();

        when(redisTemplate.getExpire("zhixue:auth:lock:ip:203.0.113.9"))
                .thenReturn(LoginAttemptLimiter.LOCK_MAX_SECONDS);
        assertThat(limiter.check("someone-else", "203.0.113.9").allowed()).isFalse();
    }

    @Test
    void checkDegradesToAllowWhenRedisUnavailable() {
        when(redisTemplate.getExpire(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(limiter.check("alice", "10.0.0.1").allowed()).isTrue();
    }

    @Test
    void successfulLoginClearsAccountCounterAndLock() {
        limiter.recordFailure("carol", "10.0.0.3");
        limiter.clearAccount("carol");

        verify(redisTemplate).delete("zhixue:auth:fail:account:carol");
        verify(redisTemplate).delete("zhixue:auth:lock:account:carol");
    }
}
