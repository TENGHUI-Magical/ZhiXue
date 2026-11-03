package com.zhixue.system.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.config.AiClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * 系统健康检查（契约 V2 §7 system 组，openapi /api/system/health）。
 * 分别探测 db / redis / ai，任一不可用返回 DEGRADED，但接口本身不返回 5xx。
 * <p>
 * 安全说明：本端点无鉴权，仅暴露组件级状态；生产部署需在网关限制为内网探针，
 * 或按部署方案收敛为只返回 status 字段，避免暴露内部依赖拓扑。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    private static final Duration AI_PROBE_TIMEOUT = Duration.ofSeconds(2);

    /**
     * 健康结果缓存时长：/health 公开且每次探三个组件，缓存 10s 防止被当作组件探测放大器。
     * 探针本身的超时语义不变：缓存过期后的首个请求仍会实时探测。
     */
    private static final long CACHE_TTL_NANOS = Duration.ofSeconds(10).toNanos();

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final WebClient webClient;

    private volatile Health cachedHealth;
    private volatile long cachedAtNanos;

    @Autowired
    public SystemController(JdbcTemplate jdbcTemplate,
                            RedisConnectionFactory redisConnectionFactory,
                            AiClientProperties aiClientProperties) {
        this(jdbcTemplate, redisConnectionFactory,
                WebClient.builder().baseUrl(aiClientProperties.baseUrl()).build());
    }

    /** 测试用构造：允许注入已 mock 的 WebClient，避免真实网络探测。 */
    SystemController(JdbcTemplate jdbcTemplate,
                     RedisConnectionFactory redisConnectionFactory,
                     WebClient webClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.webClient = webClient;
    }

    /**
     * 健康检查：组件级状态 UP / DEGRADED；任一下降时 status=DEGRADED。
     * 结果缓存 10s，窗口内重复调用不重复探组件。
     *
     * @return 统一响应，code=0
     */
    @GetMapping("/health")
    public ApiResponse<Health> health() {
        Health snapshot = cachedHealth;
        long now = System.nanoTime();
        if (snapshot != null && now - cachedAtNanos < CACHE_TTL_NANOS) {
            return ApiResponse.ok(snapshot);
        }
        synchronized (this) {
            now = System.nanoTime();
            if (cachedHealth != null && now - cachedAtNanos < CACHE_TTL_NANOS) {
                return ApiResponse.ok(cachedHealth);
            }
            Health fresh = probeAll();
            cachedHealth = fresh;
            cachedAtNanos = now;
            return ApiResponse.ok(fresh);
        }
    }

    /** 实探 db / redis / ai 三个组件。 */
    private Health probeAll() {
        String db = probe("db", () -> Integer.valueOf(1)
                .equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class)));
        String redis = probe("redis", () -> {
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping());
            }
        });
        String ai = probe("ai", () -> {
            Map<?, ?> body = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(AI_PROBE_TIMEOUT);
            return body != null && "UP".equals(String.valueOf(body.get("status")));
        });
        String overall = "UP".equals(db) && "UP".equals(redis) && "UP".equals(ai) ? "UP" : "DEGRADED";
        return new Health(overall, db, redis, ai);
    }

    /** 单组件探测：异常一律降级为 DEGRADED，不向调用方抛 5xx。 */
    private String probe(String component, Probe probe) {
        try {
            return probe.test() ? "UP" : "DEGRADED";
        } catch (Exception e) {
            log.warn("健康检查组件不可用 component={} reason={}", component, e.toString());
            return "DEGRADED";
        }
    }

    @FunctionalInterface
    private interface Probe {
        boolean test() throws Exception;
    }

    /**
     * 健康检查响应（openapi Health）。
     *
     * @param status 总体状态：UP / DEGRADED
     * @param db     数据库
     * @param redis  缓存
     * @param ai     AI 服务
     */
    public record Health(String status, String db, String redis, String ai) {
    }
}
