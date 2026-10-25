package com.zhixue.system.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.config.AiClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final WebClient webClient;

    public SystemController(JdbcTemplate jdbcTemplate,
                            RedisConnectionFactory redisConnectionFactory,
                            AiClientProperties aiClientProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.webClient = WebClient.builder().baseUrl(aiClientProperties.baseUrl()).build();
    }

    /**
     * 健康检查：组件级状态 UP / DEGRADED；任一下降时 status=DEGRADED。
     *
     * @return 统一响应，code=0
     */
    @GetMapping("/health")
    public ApiResponse<Health> health() {
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
        return ApiResponse.ok(new Health(overall, db, redis, ai));
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
