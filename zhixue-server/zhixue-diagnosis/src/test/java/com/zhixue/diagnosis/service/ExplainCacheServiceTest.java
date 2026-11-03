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
    void keyFollowsVersionAndContextHashFormula() throws Exception {
        // 期望键 = 前缀 + 版本 + ":" + sha256(版本 + ":" + sha256(全部 8 项材料以 \u0001 连接))
        String contextHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("KP0201\u0001关系模型\u0001概念A\u0001易混候选码与主码\u0001P34"
                        + "\u0001CONCEPT_CONFUSION\u0001求闭包\u0001B")
                        .getBytes(StandardCharsets.UTF_8)));
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("ctx-v1:" + contextHash).getBytes(StandardCharsets.UTF_8)));

        assertThat(service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B"))
                .isEqualTo("zhixue:explain:cache:ctx-v1:" + expected);
    }

    @Test
    void everyPromptMaterialChangesTheKey() {
        String base = service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B");

        // 逐一替换每个材料位（kpName / conceptText / misconception / textbookPage /
        // errorType / questionText / answerText），键都必须变化
        assertThat(service.key("KP0202", "关系模型", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B")).isNotEqualTo(base);
        assertThat(service.key("KP0201", "关系模型（修订）", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B")).isNotEqualTo(base);
        assertThat(service.key("KP0201", "关系模型", "概念B", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B")).isNotEqualTo(base);
        assertThat(service.key("KP0201", "关系模型", "概念A", "误区（维护后）",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B")).isNotEqualTo(base);
        assertThat(service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P35", "CONCEPT_CONFUSION", "求闭包", "B")).isNotEqualTo(base);
        assertThat(service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P34", "METHOD_MISSING", "求闭包", "B")).isNotEqualTo(base);
        assertThat(service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包（改）", "B")).isNotEqualTo(base);
        assertThat(service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "C")).isNotEqualTo(base);

        // 材料完全一致 → 键稳定（确定性，跨请求可命中）
        assertThat(service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B")).isEqualTo(base);
    }

    @Test
    void contextVersionBumpInvalidatesWholeGeneration() {
        String v1 = service.key("KP0201", "关系模型", "概念A", "易混候选码与主码",
                "P34", "CONCEPT_CONFUSION", "求闭包", "B");
        String v2 = ExplainCacheService.key("ctx-v2", "KP0201", "关系模型", "概念A",
                "易混候选码与主码", "P34", "CONCEPT_CONFUSION", "求闭包", "B");

        assertThat(v1).startsWith("zhixue:explain:cache:ctx-v1:");
        assertThat(v2).startsWith("zhixue:explain:cache:ctx-v2:").isNotEqualTo(v1);
    }

    @Test
    void nullMaterialsAreStableAndNormalizedAsEmpty() {
        // null 与 "" 语义等价（材料无值 vs 空值不该产生不同讲解），键必须归一且稳定
        String withNulls = service.key("KP0201", null, "概念A", null,
                "P34", "CONCEPT_CONFUSION", "求闭包", null);
        String withEmpty = service.key("KP0201", "", "概念A", "",
                "P34", "CONCEPT_CONFUSION", "求闭包", "");

        assertThat(withNulls).isEqualTo(withEmpty);
        assertThat(withNulls).isEqualTo(service.key("KP0201", null, "概念A", null,
                "P34", "CONCEPT_CONFUSION", "求闭包", null));
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
