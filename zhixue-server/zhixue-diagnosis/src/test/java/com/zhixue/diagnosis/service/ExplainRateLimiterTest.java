package com.zhixue.diagnosis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 讲解限流单测（契约 V2 §8）：10 次/分钟、超限 42900、Redis 故障降级放行。
 */
@ExtendWith(MockitoExtension.class)
class ExplainRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void allowsTenRequestsAndRejectsEleventh() {
        AtomicLong counter = new AtomicLong();
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> counter.incrementAndGet());
        ExplainRateLimiter limiter = new ExplainRateLimiter(redisTemplate);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.acquire(2L).allowed()).isTrue();
        }

        ExplainRateLimiter.Decision rejected = limiter.acquire(2L);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void redisFailureDegradesToAllow() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        ExplainRateLimiter limiter = new ExplainRateLimiter(redisTemplate);

        assertThat(limiter.acquire(2L).allowed()).isTrue();
    }
}
