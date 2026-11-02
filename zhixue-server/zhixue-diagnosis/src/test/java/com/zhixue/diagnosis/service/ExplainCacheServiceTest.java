package com.zhixue.diagnosis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 讲解缓存单测（契约 V2 §6）：sha256 key、7 天 TTL、读损坏/Redis 故障降级。
 */
@ExtendWith(MockitoExtension.class)
class ExplainCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExplainCacheService service;

    @BeforeEach
    void setUp() {
        service = new ExplainCacheService(redisTemplate, objectMapper);
    }

    @Test
    void keyMatchesSha256OfContractFields() throws Exception {
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("KP0201CONCEPT_CONFUSION求闭包".getBytes(StandardCharsets.UTF_8)));

        assertThat(service.key("KP0201", "CONCEPT_CONFUSION", "求闭包"))
                .isEqualTo("zhixue:explain:cache:" + expected);
    }

    @Test
    void conceptTextChangesCacheKeyAndFollowsContractHash() throws Exception {
        var keyMethod = ExplainCacheService.class.getMethod(
                "key", String.class, String.class, String.class, String.class);
        String keyA = (String) keyMethod.invoke(service, "KP0201", "CONCEPT_CONFUSION", "求闭包", "概念A");
        String keyB = (String) keyMethod.invoke(service, "KP0201", "CONCEPT_CONFUSION", "求闭包", "概念B");

        String conceptHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("概念A".getBytes(StandardCharsets.UTF_8)));
        String raw = "KP0201CONCEPT_CONFUSION求闭包|concept:" + conceptHash;
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
        assertThat(keyA).isEqualTo("zhixue:explain:cache:" + expected);
        assertThat(keyB).isNotEqualTo(keyA);
    }

    @Test
    void getReturnsParsedSnapshot() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ExplainCacheService.Cached cached = new ExplainCacheService.Cached(
                "缓存正文", "deepseek-chat", "v1",
                List.of(new ExplainCacheService.Source("KP0201", "P34", "关系模型")),
                LocalDateTime.of(2026, 10, 26, 12, 0).toString());
        when(valueOperations.get("k1")).thenReturn(objectMapper.writeValueAsString(cached));

        ExplainCacheService.Cached result = service.get("k1");

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("缓存正文");
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.sources()).extracting(ExplainCacheService.Source::kpId)
                .containsExactly("KP0201");
    }

    @Test
    void putWritesWithSevenDayTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.put("k1", new ExplainCacheService.Cached(
                "正文", "deepseek-chat", "v1", List.of(), null));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("k1"), json.capture(), eq(Duration.ofDays(7)));
        assertThat(json.getValue()).contains("\"content\":\"正文\"");
    }

    @Test
    void redisFailureNeverThrows() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), eq(Duration.ofDays(7)));

        assertThat(service.get("k1")).isNull();
        service.put("k1", new ExplainCacheService.Cached("正文", "m", "v1", List.of(), null));
    }
}
