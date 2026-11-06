package com.zhixue.common.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应空数据口径（docs/03 §1）：无数据时 data 必须是空对象 {}，不能是 null。
 */
class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void okWithoutDataSerializesEmptyObject() throws Exception {
        ApiResponse<Void> response = ApiResponse.ok();

        assertNotNull(response.getData());
        JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(response)).get("data");
        assertTrue(data.isObject());
        assertEquals(0, data.size());
    }

    @Test
    void failWithoutDataSerializesEmptyObject() throws Exception {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.PARAM_ERROR);

        assertNotNull(response.getData());
        JsonNode data = objectMapper.readTree(objectMapper.writeValueAsString(response)).get("data");
        assertTrue(data.isObject());
        assertEquals(0, data.size());
    }
}
