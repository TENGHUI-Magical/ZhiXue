package com.zhixue.it;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约比较器的纯单元回归：不启动 Spring/Testcontainers，专门锁定
 * 嵌套 schema、响应 data 缺失、实现多出端点、参数漂移四类行为。
 */
class ContractSchemaComparatorTest {

    private static final Set<String> METHODS = Set.of("get", "post", "put", "delete", "patch");

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
    void nullableEnumWithNullIsCompatible() {
        Map<String, Object> frozenProperties = Map.of(
                "id", Map.of("type", "string"),
                "status", Map.of("type", List.of("string", "null"),
                        "enum", java.util.Arrays.asList("A", "B", null)));
        Map<String, Object> generatedProperties = Map.of(
                "id", Map.of("type", "string"),
                "status", Map.of("type", List.of("string", "null"),
                        "enum", List.of("A", "B")));

        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))), frozenProperties);
        Map<String, Object> generated = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))), generatedProperties);

        assertThat(ContractSchemaComparator.compare(frozen, generated)).isEmpty();
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

    @Test
    void enumOrderIsInsensitive() {
        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                Map.of("id", Map.of("type", "string"),
                        "status", Map.of("type", "string", "enum", List.of("A", "B"))));
        Map<String, Object> generated = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                Map.of("id", Map.of("type", "string"),
                        "status", Map.of("type", "string", "enum", List.of("B", "A"))));

        assertThat(ContractSchemaComparator.compare(frozen, generated)).isEmpty();
    }

    @Test
    void detectsNestedAllOfDrift() {
        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                Map.of("id", Map.of("type", "string"),
                        "status", Map.of("type", "string", "enum", List.of("A", "B"))));
        Map<String, Object> generated = documentWithSchemas(Map.of(
                "TestResponse", responseSchema(),
                "Item", Map.of("allOf", List.of(
                        Map.of("type", "object", "properties", Map.of(
                                "id", Map.of("type", "string"))),
                        Map.of("type", "object", "properties", Map.of(
                                "status", Map.of("type", "string")))))));
        Map<String, Object> generatedDoc = Map.of(
                "paths", Map.of("/api/test", Map.of("get", operation(List.of()))),
                "components", generated);

        assertThat(ContractSchemaComparator.compare(frozen, generatedDoc))
                .anyMatch(issue -> issue.contains("status"));
    }

    @Test
    void detectsEmptySchemaAsymmetry() {
        Map<String, Object> frozen = document(
                Map.of("/api/test", Map.of("get", operation(List.of()))),
                Map.of("id", Map.of("type", "string")));
        Map<String, Object> generated = documentWithSchemas(Map.of(
                "TestResponse", Map.of("allOf", List.of(
                        Map.of("type", "object", "properties", Map.of(
                                "code", Map.of("type", "integer"))),
                        Map.of("type", "object", "properties", Map.of(
                                "data", Map.of()))))));
        Map<String, Object> generatedDoc = Map.of(
                "paths", Map.of("/api/test", Map.of("get", operation(List.of()))),
                "components", generated);

        assertThat(ContractSchemaComparator.compare(frozen, generatedDoc))
                .anyMatch(issue -> issue.contains("schema 一侧为空"));
    }

    @Test
    void nullAndEmptyResponseDataAreCompatible() {
        Map<String, Object> frozen = Map.of(
                "paths", Map.of("/api/test", Map.of("get", operation(List.of()))),
                "components", Map.of("schemas", Map.of(
                        "TestResponse", Map.of("allOf", List.of(
                                Map.of("type", "object", "properties", Map.of(
                                        "code", Map.of("type", "integer"))),
                                Map.of("type", "object", "properties", Map.of(
                                        "data", Map.of("type", "null"))))))));
        Map<String, Object> generated = Map.of(
                "paths", Map.of("/api/test", Map.of("get", operation(List.of()))),
                "components", Map.of("schemas", Map.of(
                        "TestResponse", Map.of("allOf", List.of(
                                Map.of("type", "object", "properties", Map.of(
                                        "code", Map.of("type", "integer"))),
                                Map.of("type", "object", "properties", Map.of(
                                        "data", Map.of())))))));

        assertThat(ContractSchemaComparator.compare(frozen, generated)).isEmpty();
    }

    @Test
    void recursiveSchemaDoesNotOverflow() {
        Map<String, Object> schemas = Map.of(
                "TestResponse", responseSchema(),
                "Item", Map.of("type", "object", "properties", Map.of(
                        "id", Map.of("type", "string"),
                        "child", Map.of("$ref", "#/components/schemas/Item"))));
        Map<String, Object> frozen = Map.of(
                "paths", Map.of("/api/test", Map.of("get", operation(List.of()))),
                "components", Map.of("schemas", schemas));
        Map<String, Object> generated = Map.of(
                "paths", Map.of("/api/test", Map.of("get", operation(List.of()))),
                "components", Map.of("schemas", schemas));

        assertThat(ContractSchemaComparator.compare(frozen, generated)).isEmpty();
    }

    @Test
    void frozenOpenapiIsSelfConsistent() throws Exception {
        Map<?, ?> doc = new Yaml().load(Files.readString(
                ContractSchemaComparator.openapiPath(), StandardCharsets.UTF_8));
        Map<?, ?> schemas = asMap(asMap(asMap(doc.get("components")).get("schemas")));
        List<String> issues = new ArrayList<>();
        lint(schemas, issues);
        assertThat(issues).isEmpty();
    }

    @Test
    void degradeLevelExcludesMainModelL1() throws Exception {
        Map<?, ?> doc = new Yaml().load(Files.readString(
                ContractSchemaComparator.openapiPath(), StandardCharsets.UTF_8));
        Map<?, ?> schemas = asMap(asMap(asMap(doc.get("components")).get("schemas")));
        Object raw = asMap(asMap(schemas.get("ExplainLog")).get("properties")).get("degradeLevel");
        Map<?, ?> degradeLevel = raw instanceof Map<?, ?> map ? map : Map.of();
        assertThat(asMap(degradeLevel).get("enum"))
                .as("L1 是主力模型，不是降级级别")
                .isEqualTo(java.util.Arrays.asList("L2", "L3", "L4", null));
    }

    @Test
    void noHttpStatusErrorResponsesDeclared() throws Exception {
        Map<?, ?> doc = new Yaml().load(Files.readString(
                ContractSchemaComparator.openapiPath(), StandardCharsets.UTF_8));
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<?, ?> pathEntry : asMap(doc.get("paths")).entrySet()) {
            Map<?, ?> item = asMap(pathEntry.getValue());
            for (Map.Entry<?, ?> opEntry : item.entrySet()) {
                String method = String.valueOf(opEntry.getKey()).toLowerCase();
                if (!METHODS.contains(method)) {
                    continue;
                }
                for (Object status : asMap(asMap(opEntry.getValue()).get("responses")).keySet()) {
                    String code = String.valueOf(status);
                    if (code.startsWith("4") || code.startsWith("5")) {
                        offenders.add(method.toUpperCase() + " " + pathEntry.getKey() + " " + code);
                    }
                }
            }
        }
        // 本项目所有业务错误统一返回 HTTP 200 + body.code≠0（GlobalExceptionHandler 不设 @ResponseStatus）。
        // 若在 paths 里声明 4xx/5xx，既与实现不符，也会让 springdoc 永远对不上——
        // 这正是 2026-10-28 之前“错误响应未生成”的根因。错误码目录保留在 components.responses。
        assertThat(offenders)
                .as("契约不得声明 HTTP 4xx/5xx 响应，业务错误统一 200 + code≠0")
                .isEmpty();
    }

    private static void lint(Object node, List<String> issues) {
        if (node instanceof Map<?, ?> map) {
            Object enumValues = map.get("enum");
            if (enumValues instanceof List<?> values) {
                Object type = map.get("type");
                for (Object value : values) {
                    if (!enumValueMatchesType(value, type, map.get("nullable"))) {
                        issues.add("enum 值 " + value + " 与 type=" + type + " 不一致");
                    }
                }
            }
            Object required = map.get("required");
            Map<?, ?> properties = asMap(map.get("properties"));
            if (required instanceof List<?> names && !properties.isEmpty()) {
                for (Object name : names) {
                    if (!properties.containsKey(name)) {
                        issues.add("required 字段 " + name + " 不在 properties 中");
                    }
                }
            }
            for (Object value : map.values()) {
                lint(value, issues);
            }
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                lint(value, issues);
            }
        }
    }

    private static boolean enumValueMatchesType(Object value, Object type, Object nullable) {
        String typeText = String.valueOf(type);
        if (value == null) {
            return typeText.contains("null") || Boolean.TRUE.equals(nullable);
        }
        if (value instanceof String) {
            return typeText.contains("string");
        }
        if (value instanceof Integer || value instanceof Long) {
            return typeText.contains("integer") || typeText.contains("number");
        }
        if (value instanceof Number) {
            return typeText.contains("number");
        }
        if (value instanceof Boolean) {
            return typeText.contains("boolean");
        }
        if (value instanceof Map) {
            return typeText.contains("object");
        }
        if (value instanceof List) {
            return typeText.contains("array");
        }
        return false;
    }

    private static Map<String, Object> responseSchema() {
        return Map.of("allOf", List.of(
                Map.of("type", "object", "properties", Map.of(
                        "code", Map.of("type", "integer"))),
                Map.of("type", "object", "properties", Map.of(
                        "data", Map.of("$ref", "#/components/schemas/Item")))));
    }

    private static Map<String, Object> documentWithSchemas(Map<String, Object> schemas) {
        return Map.of("schemas", schemas);
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
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
