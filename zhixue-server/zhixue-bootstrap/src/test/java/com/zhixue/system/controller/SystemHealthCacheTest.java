package com.zhixue.system.controller;

import com.zhixue.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /health 公开端点：10s 缓存窗口内重复请求不重复探 db/redis/ai，防止被当作探测放大器。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemHealthCacheTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private WebClient webClient;

    @Test
    void repeatedCallsWithinCacheWindowProbeComponentsOnce() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(webClient.get()).thenThrow(new IllegalStateException("ai down"));
        SystemController controller = new SystemController(
                jdbcTemplate, redisConnectionFactory, webClient);

        ApiResponse<SystemController.Health> first = controller.health();
        ApiResponse<SystemController.Health> second = controller.health();

        assertThat(first.getCode()).isZero();
        assertThat(second.getData()).isEqualTo(first.getData());
        // 缓存命中：db / redis / ai 各只探一次
        verify(jdbcTemplate, times(1)).queryForObject("SELECT 1", Integer.class);
        verify(redisConnection, times(1)).ping();
        verify(webClient, times(1)).get();
    }
}
