package com.zhixue.it;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约比较器：以冻结 openapi 为基准，检查已实现端点是否漂移。
 * <p>
 * 门禁范围：
 * <ul>
 *   <li>实现侧新增、冻结契约未声明的 path/method；</li>
 *   <li>请求体与响应 data 的结构（$ref、数组 items、内联 object 递归）；</li>
 *   <li>字段 type、enum、请求 required；</li>
 *   <li>path/query 参数的 name/in/required、schema type/enum。</li>
 * </ul>
 * 暂不纳入：format/minLength/maxLength/minimum/maximum/default/nullable、响应 required、
 * content-type、错误响应。这些由官方 openapi-diff 单列，见技术学习/24 号文 §2。
 * <p>
 * 响应 data 为内联对象时也直接比较；任一方向无法解析出 data 都记 issue，
 * 只有两侧都明确没有 data 时才跳过。
 */
final class ContractSchemaComparator {

    private static final Set<String> METHODS = Set.of("get", "post", "put", "delete", "patch");
    private static final Set<String> SSE_SKIP = Set.of("post /api/diagnosis/explain");
    private static final int MAX_DEPTH = 8;

    private ContractSchemaComparator() {
    }

    /** 从当前工作目录向上查找 docs/openapi.yaml，避免依赖固定层级。 */
    static Path openapiPath() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 10 && current != null; i++) {
            Path candidate = current.resolve("docs").resolve("openapi.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "找不到 docs/openapi.yaml，当前工作目录=" + System.getProperty("user.dir"));
    }

    static List<String> compare(Map<?, ?> frozen, Map<?, ?> generated) {
        Map<?, ?> frozenPaths = asMap(frozen.get("paths"));
        Map<?, ?> genPaths = asMap(generated.get("paths"));
        Map<?, ?> frozenSchemas = schemas(frozen);
        Map<?, ?> genSchemas = schemas(generated);
        Map<?, ?> frozenParams = parameters(frozen);
        Map<?, ?> genParams = parameters(generated);

        List<String> issues = new ArrayList<>();
        Set<String> frozenOps = operationKeys(frozenPaths);
        for (String op : operationKeys(genPaths)) {
            if (!frozenOps.contains(op)) {
                issues.add("实现包含契约未声明的端点 " + display(op));
            }
        }

        for (String op : frozenOps) {
            int split = op.indexOf(' ');
            String method = op.substring(0, split);
            String path = op.substring(split + 1);
            Map<?, ?> frozenItem = asMap(frozenPaths.get(path));
            Map<?, ?> genItem = asMap(genPaths.get(path));
            Map<?, ?> frozenOp = asMap(frozenItem.get(method));
            Map<?, ?> genOp = asMap(genItem.get(method));
            if (genOp.isEmpty()) {
                // 冻结契约中未实现的端点：登记为已知缺口，不属于本次对齐门禁
                continue;
            }
            String ctx = method.toUpperCase() + " " + path;
            compareSchemaPair(contentSchema(frozenOp, "requestBody"),
                    contentSchema(genOp, "requestBody"),
                    frozenSchemas, genSchemas, ctx + " 请求体", issues, true, 0, new HashSet<>());
            compareParameters(frozenItem, frozenOp, genItem, genOp, frozenParams, genParams, ctx, issues);
            if (!SSE_SKIP.contains(op)) {
                compareResponseSchema(frozenOp, genOp, frozenSchemas, genSchemas, ctx, issues);
            }
        }
        return issues;
    }

    private static Map<?, ?> schemas(Map<?, ?> document) {
        return asMap(asMap(asMap(document.get("components")).get("schemas")));
    }

    private static Map<?, ?> parameters(Map<?, ?> document) {
        return asMap(asMap(asMap(document.get("components")).get("parameters")));
    }

    private static Set<String> operationKeys(Map<?, ?> paths) {
        Set<String> keys = new LinkedHashSet<>();
        for (Object pathKey : paths.keySet()) {
            String path = String.valueOf(pathKey);
            Map<?, ?> item = asMap(paths.get(pathKey));
            for (Object methodKey : item.keySet()) {
                String method = String.valueOf(methodKey).toLowerCase();
                if (METHODS.contains(method)) {
                    keys.add(method + " " + path);
                }
            }
        }
        return keys;
    }

    private static String display(String op) {
        int split = op.indexOf(' ');
        return op.substring(0, split).toUpperCase() + " " + op.substring(split + 1);
    }

    private static Map<?, ?> contentSchema(Map<?, ?> operation, String bodyKey) {
        Map<?, ?> body = asMap(operation.get(bodyKey));
        Map<?, ?> content = asMap(body.get("content"));
        for (Object key : content.keySet()) {
            String contentType = String.valueOf(key);
            if (contentType.contains("json") || contentType.contains("multipart")
                    || contentType.contains("*/*")) {
                return asMap(asMap(content.get(key)).get("schema"));
            }
        }
        return Map.of();
    }

    private static void compareParameters(Map<?, ?> frozenItem, Map<?, ?> frozenOp,
                                          Map<?, ?> genItem, Map<?, ?> genOp,
                                          Map<?, ?> frozenParams, Map<?, ?> genParams,
                                          String ctx, List<String> issues) {
        Map<String, Map<?, ?>> frozen = parameterMap(frozenItem, frozenOp, frozenParams);
        Map<String, Map<?, ?>> gen = parameterMap(genItem, genOp, genParams);
        for (Map.Entry<String, Map<?, ?>> entry : frozen.entrySet()) {
            Map<?, ?> genParam = gen.get(entry.getKey());
            if (genParam == null) {
                issues.add(ctx + "：契约参数缺失于实现 " + entry.getKey());
                continue;
            }
            Map<?, ?> frozenParam = entry.getValue();
            Object frozenRequired = frozenParam.get("required");
            Object genRequired = genParam.get("required");
            if (!String.valueOf(frozenRequired).equals(String.valueOf(genRequired))) {
                issues.add(ctx + "：参数 " + entry.getKey() + " required 不一致 契约="
                        + frozenRequired + " 实现=" + genRequired);
            }
            comparePropertyConstraints(entry.getKey(),
                    asMap(frozenParam.get("schema")), asMap(genParam.get("schema")),
                    ctx + " 参数", issues);
        }
        for (String key : gen.keySet()) {
            if (!frozen.containsKey(key)) {
                issues.add(ctx + "：实现多出契约未声明参数 " + key);
            }
        }
    }

    private static Map<String, Map<?, ?>> parameterMap(Map<?, ?> pathItem, Map<?, ?> operation,
                                                       Map<?, ?> components) {
        Map<String, Map<?, ?>> result = new LinkedHashMap<>();
        addParameters(result, pathItem.get("parameters"), components);
        addParameters(result, operation.get("parameters"), components);
        return result;
    }

    private static void addParameters(Map<String, Map<?, ?>> target, Object value,
                                      Map<?, ?> components) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            Map<?, ?> param = resolveParameter(asMap(item), components);
            String name = String.valueOf(param.get("name"));
            String in = String.valueOf(param.get("in"));
            if (!"null".equals(name) && !"null".equals(in)) {
                target.put(in + ":" + name, param);
            }
        }
    }

    private static Map<?, ?> resolveParameter(Map<?, ?> param, Map<?, ?> components) {
        if (param.get("$ref") != null) {
            return asMap(components.get(refName(String.valueOf(param.get("$ref")))));
        }
        return param;
    }

    private static void compareResponseSchema(Map<?, ?> frozenOp, Map<?, ?> genOp,
                                              Map<?, ?> frozenSchemas, Map<?, ?> genSchemas,
                                              String ctx, List<String> issues) {
        Map<?, ?> frozenData = responseDataSchema(responseContentSchema(frozenOp), frozenSchemas);
        Map<?, ?> genData = responseDataSchema(responseContentSchema(genOp), genSchemas);
        if (frozenData == null && genData == null) {
            return;
        }
        if (frozenData == null) {
            issues.add(ctx + " 响应 data：实现多出契约未声明的 data");
            return;
        }
        if (genData == null) {
            issues.add(ctx + " 响应 data：实现未解析出契约声明的 data 目标");
            return;
        }
        compareSchemaPair(frozenData, genData, frozenSchemas, genSchemas,
                ctx + " 响应 data", issues, false, 0, new HashSet<>());
    }

    private static Map<?, ?> responseContentSchema(Map<?, ?> operation) {
        Map<?, ?> responses = asMap(operation.get("responses"));
        Map<?, ?> ok = asMap(responses.get("200"));
        Map<?, ?> content = asMap(ok.get("content"));
        for (Object key : content.keySet()) {
            String contentType = String.valueOf(key);
            if (contentType.contains("json") || contentType.contains("*/*")) {
                return asMap(asMap(content.get(key)).get("schema"));
            }
        }
        return Map.of();
    }

    /** 从响应 content schema 解出 data schema：信封（可能 allOf）→ properties.data。 */
    private static Map<?, ?> responseDataSchema(Map<?, ?> topSchema, Map<?, ?> schemas) {
        Map<?, ?> resolved = resolve(topSchema, schemas);
        if (resolved.isEmpty()) {
            resolved = topSchema;
        }
        if (resolved.get("allOf") instanceof List<?> list) {
            for (Object item : list) {
                Map<?, ?> member = resolve(asMap(item), schemas);
                Map<?, ?> props = asMap(member.get("properties"));
                if (props.containsKey("data")) {
                    return asMap(props.get("data"));
                }
            }
        }
        Map<?, ?> props = asMap(resolved.get("properties"));
        return props.containsKey("data") ? asMap(props.get("data")) : null;
    }

    private static void compareSchemaPair(Map<?, ?> frozenSchema, Map<?, ?> genSchema,
                                          Map<?, ?> frozenSchemas, Map<?, ?> genSchemas,
                                          String ctx, List<String> issues) {
        compareSchemaPair(frozenSchema, genSchema, frozenSchemas, genSchemas,
                ctx, issues, true, 0, new HashSet<>());
    }

    private static void compareSchemaPair(Map<?, ?> frozenSchema, Map<?, ?> genSchema,
                                          Map<?, ?> frozenSchemas, Map<?, ?> genSchemas,
                                          String ctx, List<String> issues,
                                          boolean checkRequired, int depth, Set<String> visiting) {
        if (depth > MAX_DEPTH || frozenSchema.isEmpty() || genSchema.isEmpty()) {
            return;
        }
        Map<?, ?> frozenResolved = resolve(frozenSchema, frozenSchemas);
        Map<?, ?> genResolved = resolve(genSchema, genSchemas);
        if (frozenResolved.isEmpty() || genResolved.isEmpty()) {
            return;
        }
        Map<?, ?> frozenProps = asMap(frozenResolved.get("properties"));
        Map<?, ?> genProps = asMap(genResolved.get("properties"));
        if (frozenProps.isEmpty() && genProps.isEmpty()) {
            return;
        }
        for (Object prop : frozenProps.keySet()) {
            String name = String.valueOf(prop);
            if (!genProps.containsKey(prop)) {
                issues.add(ctx + "：契约字段缺失于实现 " + name);
                continue;
            }
            Map<?, ?> frozenProp = asMap(frozenProps.get(prop));
            Map<?, ?> genProp = asMap(genProps.get(prop));
            comparePropertyConstraints(name, frozenProp, genProp, ctx, issues);
            compareNestedSchema(name, frozenProp, genProp, frozenSchemas, genSchemas,
                    ctx, issues, checkRequired, depth, visiting);
        }
        for (Object prop : genProps.keySet()) {
            if (!frozenProps.containsKey(prop)) {
                issues.add(ctx + "：实现多出契约未声明字段 " + prop);
            }
        }
        if (checkRequired) {
            List<?> frozenRequired = frozenResolved.get("required") instanceof List<?> list
                    ? list : List.of();
            List<?> genRequired = genResolved.get("required") instanceof List<?> list
                    ? list : List.of();
            // required 顺序无契约意义，按集合比较；响应侧 required 只作为文档提示，不阻断
            if (!new LinkedHashSet<>(frozenRequired).equals(new LinkedHashSet<>(genRequired))) {
                issues.add(ctx + "：required 不一致 契约=" + frozenRequired
                        + " 实现=" + genRequired);
            }
        }
    }

    private static void compareNestedSchema(String name, Map<?, ?> frozenProp, Map<?, ?> genProp,
                                            Map<?, ?> frozenSchemas, Map<?, ?> genSchemas,
                                            String ctx, List<String> issues, boolean checkRequired,
                                            int depth, Set<String> visiting) {
        String frozenRef = refNameOrNull(frozenProp.get("$ref"));
        String genRef = refNameOrNull(genProp.get("$ref"));
        if (frozenRef != null || genRef != null) {
            String key = frozenRef + "->" + genRef;
            if (!visiting.add(key)) {
                return;
            }
            compareSchemaPair(frozenProp, genProp, frozenSchemas, genSchemas,
                    ctx + "." + name, issues, checkRequired, depth + 1, visiting);
            visiting.remove(key);
            return;
        }
        boolean frozenArray = "array".equals(String.valueOf(frozenProp.get("type")));
        boolean genArray = "array".equals(String.valueOf(genProp.get("type")));
        if (frozenArray || genArray) {
            Map<?, ?> frozenItems = asMap(frozenProp.get("items"));
            Map<?, ?> genItems = asMap(genProp.get("items"));
            if (!frozenItems.isEmpty() && !genItems.isEmpty()) {
                comparePropertyConstraints(name + "[]", frozenItems, genItems, ctx, issues);
                compareNestedSchema(name + "[]", frozenItems, genItems,
                        frozenSchemas, genSchemas, ctx, issues,
                        checkRequired, depth + 1, visiting);
            }
            return;
        }
        if (!asMap(frozenProp.get("properties")).isEmpty()
                || !asMap(genProp.get("properties")).isEmpty()) {
            compareSchemaPair(frozenProp, genProp, frozenSchemas, genSchemas,
                    ctx + "." + name, issues, checkRequired, depth + 1, visiting);
        }
    }

    private static void comparePropertyConstraints(String prop, Map<?, ?> frozenProp,
                                                   Map<?, ?> genProp,
                                                   String ctx, List<String> issues) {
        Object frozenType = frozenProp.get("type");
        Object genType = genProp.get("type");
        if (frozenType != null && genType != null
                && !String.valueOf(frozenType).equals(String.valueOf(genType))) {
            // 契约 integer 与实现 number 视为兼容（int64 序列化为 number）
            boolean numberCompat = ("integer".equals(String.valueOf(frozenType))
                    && "number".equals(String.valueOf(genType)))
                    || ("number".equals(String.valueOf(frozenType))
                    && "integer".equals(String.valueOf(genType)));
            if (!numberCompat) {
                issues.add(ctx + "：字段 " + prop + " 类型不一致 契约="
                        + frozenType + " 实现=" + genType);
            }
        }
        if (frozenProp.get("enum") instanceof List<?> frozenEnum) {
            if (!(genProp.get("enum") instanceof List<?> genEnum)
                    || !frozenEnum.toString().equals(genEnum.toString())) {
                issues.add(ctx + "：字段 " + prop + " 枚举不一致 契约=" + frozenEnum);
            }
        }
    }

    private static Map<?, ?> resolve(Map<?, ?> schema, Map<?, ?> schemas) {
        if (schema.get("$ref") != null) {
            return asMap(schemas.get(refName(String.valueOf(schema.get("$ref")))));
        }
        return schema;
    }

    private static String refNameOrNull(Object ref) {
        return ref == null ? null : refName(String.valueOf(ref));
    }

    private static String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }
}
