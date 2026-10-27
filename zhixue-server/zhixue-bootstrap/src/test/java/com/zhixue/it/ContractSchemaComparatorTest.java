package com.zhixue.it;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约比较器的纯单元回归：不启动 Spring/Testcontainers，专门锁定
 * 嵌套 schema、响应 data 缺失、实现多出端点、参数漂移四类行为。
 */
class ContractSchemaComparatorTest {

    @Test
    void cleanContractHasNoIssues() {
        Map<String, Object> frozen = document(
                Map.of("/api/test/{id}", Map.of("get", operation(
                        List.of(parameter("id", "path", true, Map.of("type", "string")))))),
                itemProperties(true));
        Map<String, Object> generated = document(
                Map.of("/api/test/{id}", Map.of("get", operation(
                        List.of(parameter("id", "path", true, Map.of("type", "string")))))),
                itemProperties(true));

        assertThat(ContractSchemaComparator.compare(frozen, generated)).isEmpty();
    }

    @Test
    void detectsNestedEnumAndMissingField() {
        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                itemProperties(true));
        Map<String, Object> generated = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                Map.of("id", Map.of("type", "string")));

        assertThat(ContractSchemaComparator.compare(frozen, generated))
                .anyMatch(issue -> issue.contains("status") || issue.contains("嵌套"));
    }

    @Test
    void detectsMissingResponseData() {
        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                itemProperties(true));
        Map<String, Object> generated = Map.of(
                "paths", Map.of("/api/test", Map.of("get", operation(List.of()))),
                "components", Map.of("schemas", Map.of(
                        "TestResponse", Map.of("allOf", List.of(
                                Map.of("type", "object", "properties", Map.of(
                                        "code", Map.of("type", "integer"))))))));

        assertThat(ContractSchemaComparator.compare(frozen, generated))
                .anyMatch(issue -> issue.contains("响应 data"));
    }

    @Test
    void detectsExtraEndpoint() {
        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                itemProperties(true));
        Map<String, Object> generated = document(
                Map.of("/api/test", Map.of("get", operation(List.of())),
                        "/api/extra", Map.of("get", operation(List.of()))),
                itemProperties(true));

        assertThat(ContractSchemaComparator.compare(frozen, generated))
                .anyMatch(issue -> issue.contains("/api/extra"));
    }

    @Test
    void detectsParameterRequiredDriftAndExtraParameter() {
        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(
                        List.of(parameter("logId", "query", true, Map.of("type", "string")))))),
                itemProperties(true));
        Map<String, Object> generated = document(
                Map.of("/api/test", Map.of("get", operation(
                        List.of(parameter("logId", "query", false, Map.of("type", "string")),
                                parameter("kpId", "query", false, Map.of("type", "string")))))),
                itemProperties(true));

        assertThat(ContractSchemaComparator.compare(frozen, generated))
                .anyMatch(issue -> issue.contains("required 不一致"))
                .anyMatch(issue -> issue.contains("多出契约未声明参数"));
    }

    private static Map<String, Object> document(Map<String, Object> paths,
                                                Map<String, Object> itemProperties) {
        return Map.of(
                "paths", paths,
                "components", Map.of("schemas", Map.of(
                        "TestResponse", Map.of("allOf", List.of(
                                Map.of("type", "object", "properties", Map.of(
                                        "code", Map.of("type", "integer"))),
                                Map.of("type", "object", "properties", Map.of(
                                        "data", Map.of("$ref", "#/components/schemas/Item"))))),
                        "Item", Map.of("type", "object", "properties", itemProperties))));
    }

    private static Map<String, Object> itemProperties(boolean withEnum) {
        return withEnum
                ? Map.of("id", Map.of("type", "string"),
                        "status", Map.of("type", "string", "enum", List.of("A", "B")))
                : Map.of("id", Map.of("type", "string"));
    }

    private static Map<String, Object> operation(List<Map<String, Object>> parameters) {
        Map<String, Object> responses = Map.of("responses", Map.of("200", Map.of(
                "description", "ok",
                "content", Map.of("application/json", Map.of(
                        "schema", Map.of("$ref", "#/components/schemas/TestResponse"))))));
        if (parameters.isEmpty()) {
            return responses;
        }
        return Map.of("parameters", parameters, "responses", responses.get("responses"));
    }

    private static Map<String, Object> parameter(String name, String in, boolean required,
                                                 Map<String, Object> schema) {
        return Map.of("name", name, "in", in, "required", required, "schema", schema);
    }
}
