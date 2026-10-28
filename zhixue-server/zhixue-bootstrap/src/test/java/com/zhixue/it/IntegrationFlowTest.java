package com.zhixue.it;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zhixue.diagnosis.service.OcrService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 MySQL + Redis + Tomcat 的核心链路集成测试。
 * <p>
 * 默认 {@code @Tag("it")} 被 Surefire 排除，避免没有 Docker 的本地环境失败；
 * CI 用 {@code mvn -Pit verify} 并设置 RUN_IT=true 执行。
 * AI 服务用 JDK HttpServer 桩替代，不依赖真实模型。
 */
@Tag("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationFlowTest {

    private static final boolean RUN_IT = "true".equalsIgnoreCase(System.getenv("RUN_IT"));
    private static final AtomicInteger AI_CALLS = new AtomicInteger();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0")).withDatabaseName("zhixue");
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    private static final HttpServer AI = RUN_IT ? startAi() : null;

    static {
        if (RUN_IT) {
            MYSQL.start();
            REDIS.start();
        }
    }

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private OcrService ocrService;
    @Autowired
    private ObjectMapper objectMapper;

    /** 测试用户口令：It!Pass123 的 BCrypt 哈希（仅测试库使用） */
    private static final String TEST_HASH = "$2a$10$xARWaBRcoRVDmdtPMopUJecaI4F5/vskTeAA3IcIA9LIULv6v6XPO";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!RUN_IT) {
            return;
        }
        registry.add("spring.datasource.url", IntegrationFlowTest::jdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("zhixue.ai.base-url", () -> "http://127.0.0.1:" + AI.getAddress().getPort());
    }

    @BeforeAll
    static void initSchema() throws Exception {
        if (!RUN_IT) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            executeScript(connection, "../sql/init-v2.sql", true);
            executeScript(connection, "../sql/seed-kp-ch02-ch03.sql", false);
            executeScript(connection, "../sql/seed-question-ch02-ch03.sql", false);
            upsertUser(connection, "it_user");
            upsertUser(connection, "it_user2");
            long chapterId = queryChapterId(connection);
            insertTestKp(connection, chapterId, "KPITL3", "集成测试L3",
                    "用于触发上游 500 并验证 L3 本地模板降级的概念切片", "集成测试误区");
            insertTestKp(connection, chapterId, "KPITL4", "集成测试L4", null, null);
            // 批次 3：降级链与 SSE 连接治理用例专用知识点（概念切片非空，避免落到 L4 60001）
            insertTestKp(connection, chapterId, "KPITL2", "集成测试L2",
                    "用于验证 Python 侧 L1 重试仍失败后降 L2（GLM）的透传与留痕", "集成测试误区");
            insertTestKp(connection, chapterId, "KPITHB", "集成测试心跳",
                    "用于验证上游长时间静默时 Java 侧 15 秒心跳注释帧仍然发送", "集成测试误区");
            insertTestKp(connection, chapterId, "KPITSTALL", "集成测试首字节超时",
                    "用于验证上游不发任何字节时 Java 侧首字节 10 秒超时并降 L3", "集成测试误区");
            insertTestKp(connection, chapterId, "KPITDRIP", "集成测试断开取消",
                    "用于验证客户端中途断开后仍写讲解留痕并取消上游订阅", "集成测试误区");
        }
    }

    private static void upsertUser(Connection connection, String username) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sys_user (username, password, real_name, role, class_name, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE "
                        + "password = VALUES(password), real_name = VALUES(real_name), "
                        + "role = VALUES(role), class_name = VALUES(class_name), is_active = 1")) {
            ps.setString(1, username);
            ps.setString(2, TEST_HASH);
            ps.setString(3, "集成测试");
            ps.setString(4, "STUDENT");
            ps.setString(5, "IT");
            ps.executeUpdate();
        }
    }

    private static long queryChapterId(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT chapter_id FROM course_chapter WHERE course_id = 1 AND chapter_no = 2 LIMIT 1")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static void insertTestKp(Connection connection, long chapterId, String kpId,
                                     String name, String conceptText, String misconception) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO knowledge_point "
                        + "(kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, "
                        + "dimension_key, sort_no, created_by, is_active) "
                        + "VALUES (?, 1, ?, ?, ?, ?, 'P1', 'concept', 99, 1, 1) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), "
                        + "misconception = VALUES(misconception), is_active = 1")) {
            ps.setString(1, kpId);
            ps.setLong(2, chapterId);
            ps.setString(3, name);
            ps.setString(4, conceptText);
            ps.setString(5, misconception);
            ps.executeUpdate();
        }
    }

    @AfterAll
    static void stopAll() {
        if (!RUN_IT) {
            return;
        }
        if (AI != null) {
            AI.stop(0);
        }
        REDIS.stop();
        MYSQL.stop();
    }

    @BeforeEach
    void cleanData() {
        jdbcTemplate.update("DELETE FROM student_error_record");
        jdbcTemplate.update("DELETE FROM ai_generation_log");
        jdbcTemplate.update("DELETE FROM recommendation");
        jdbcTemplate.update("DELETE FROM ocr_task");
        Set<String> keys = redisTemplate.keys("zhixue:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        AI_CALLS.set(0);
    }

    @Test
    void recordCreateIsIdempotentAndConfirmIsCas() throws Exception {
        String token = login();
        String firstLogId = createRecord(token, "ct-1", "KP0201");
        String secondLogId = createRecord(token, "ct-1", "KP0201");

        assertThat(secondLogId).isEqualTo(firstLogId);
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_error_record WHERE client_token = 'ct-1'", Integer.class);
        assertThat(rowCount).isEqualTo(1);

        HttpResponse<String> confirmed = confirm(token, firstLogId);
        assertThat(code(confirmed)).isZero();
        assertThat((String) JsonPath.read(body(confirmed), "$.data.status")).isEqualTo("CONFIRMED");

        HttpResponse<String> duplicate = confirm(token, firstLogId);
        assertThat(code(duplicate)).isEqualTo(40900);
    }

    @Test
    void errorRecordPageFiltersByStatusAndIsUserScoped() throws Exception {
        String token = login();
        String draftLogId = createRecord(token, "ct-page-draft", "KP0201");
        String confirmedLogId = createAndConfirm(token, "ct-page-confirmed", "KP0201");

        HttpResponse<String> all = getJson("/api/error/records?page=1&size=10", token);
        assertThat(code(all)).isZero();
        assertThat((Integer) JsonPath.read(body(all), "$.data.total")).isEqualTo(2);
        assertThat((List<?>) JsonPath.read(body(all), "$.data.records")).hasSize(2);

        HttpResponse<String> drafts = getJson(
                "/api/error/records?page=1&size=10&status=DRAFT", token);
        assertThat((Integer) JsonPath.read(body(drafts), "$.data.total")).isEqualTo(1);
        assertThat((String) JsonPath.read(body(drafts), "$.data.records[0].logId"))
                .isEqualTo(draftLogId);

        HttpResponse<String> confirmed = getJson(
                "/api/error/records?page=1&size=10&status=CONFIRMED", token);
        assertThat((Integer) JsonPath.read(body(confirmed), "$.data.total")).isEqualTo(1);
        assertThat((String) JsonPath.read(body(confirmed), "$.data.records[0].logId"))
                .isEqualTo(confirmedLogId);

        String otherToken = login("it_user2");
        HttpResponse<String> other = getJson("/api/error/records?page=1&size=10", otherToken);
        assertThat((Integer) JsonPath.read(body(other), "$.data.total")).isZero();

        assertThat(code(getJson("/api/error/records?page=1&size=51", token))).isEqualTo(40000);
    }

    @Test
    void createRejectsUnknownKpWithoutInserting() throws Exception {
        String token = login();
        HttpResponse<String> result = createRaw(token, "ct-bad", "KP9999");

        assertThat(code(result)).isEqualTo(40400);
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_error_record WHERE client_token = 'ct-bad'", Integer.class);
        assertThat(rowCount).isZero();
    }

    @Test
    void explainStreamsThenHitsCache() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-sse");

        HttpResponse<String> first = explain(token, logId);
        String firstBody = body(first);
        assertThat(firstBody).contains("event:meta", "event:source", "event:delta", "event:done");
        assertThat(firstBody).doesNotContain("\"cached\":true");
        assertThat(AI_CALLS.get()).isEqualTo(1);

        HttpResponse<String> second = explain(token, logId);
        String secondBody = body(second);
        assertThat(secondBody).contains("\"model\":\"cache\"", "\"cached\":true");
        assertThat(AI_CALLS.get()).isEqualTo(1);
    }

    @Test
    void explainRateLimitReturns42900AfterTenRequests() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-rate");

        for (int i = 0; i < 10; i++) {
            assertThat(body(explain(token, logId))).doesNotContain("\"code\":42900");
        }

        HttpResponse<String> rejected = explain(token, logId);
        assertThat(body(rejected)).contains("\"code\":42900");
        assertThat(rejected.headers().firstValue("Retry-After").orElse("")).isNotBlank();
    }

    @Test
    void ocrReclaimKeepsCompletedTaskAndFailsStaleTask() throws Exception {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM sys_user WHERE username = 'it_user'", Long.class);
        insertOcrTask(userId, "T-STALE", "ct-ocr-stale", "PENDING");
        insertOcrTask(userId, "T-DONE", "ct-ocr-done", "SUCCESS");

        ocrService.reclaimStaleTasks();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ocr_task WHERE task_id = 'T-STALE'", String.class)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ocr_task WHERE task_id = 'T-DONE'", String.class)).isEqualTo("SUCCESS");
    }

    @Test
    void unauthenticatedAndCrossUserRecordAccessRejected() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-auth");

        assertThat(code(getJson("/api/error/records/" + logId, null))).isEqualTo(40100);
        String otherToken = login("it_user2");
        assertThat(code(getJson("/api/error/records/" + logId, otherToken))).isEqualTo(40300);
    }

    @Test
    void explainQueryRejectsOtherUser() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-owner");
        String explainId = explainIdFromSse(body(explain(token, logId)));

        String otherToken = login("it_user2");
        HttpResponse<String> query = getJson("/api/diagnosis/explain/" + explainId, otherToken);
        assertThat(code(query)).isEqualTo(40400);
    }

    @Test
    void uploadRejectsMissingAndOversizedFiles() throws Exception {
        String token = login();

        assertThat(code(upload(token, "ct-missing", null))).isEqualTo(40000);
        assertThat(code(upload(token, "ct-6mb", new byte[6 * 1024 * 1024]))).isEqualTo(40000);
        assertThat(code(upload(token, "ct-11mb", new byte[11 * 1024 * 1024]))).isEqualTo(40000);
    }

    @Test
    void blankConceptReturns60001() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-l4", "KPITL4");

        String body = body(explain(token, logId, "KPITL4"));
        assertThat(body).contains("\"code\":60001");
        assertThat(body).doesNotContain("event:delta");
        assertThat(AI_CALLS.get()).isZero();
    }

    /**
     * IT-DEG-02：zhixue-ai 整体不可用 → 直接落 L3 本地模板。
     * <p>
     * {@code AI_CALLS == 1} 是正确预期，不是“缺口固化”：Java 对 zhixue-ai 只发一次请求，
     * L1 重试与 L2（GLM）都在 Python 内部完成（{@code zhixue-ai/app/services/rag_service.py}）。
     * 整个 AI 服务不可达时 GLM 同样调不到，且架构铁律是“Java 不直连大模型”，
     * 因此这里不该、也不能出现第二次调用。24 号文 §5.3 曾把这条断言判为缺口，属口径误判，见 25 号文更正。
     */
    @Test
    void upstreamFailureFallsBackToL3() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-l3", "KPITL3");

        String body = body(explain(token, logId, "KPITL3"));
        assertThat(body).contains("\"level\":\"L3\"", "event:delta", "event:done");
        assertThat(body).doesNotContain("\"code\":60001");
        assertThat(AI_CALLS.get()).isEqualTo(1);
    }

    /**
     * IT-DEG-01：L1 重试仍失败后由 Python 降 L2（GLM），Java 必须原样透传降级级别并留痕。
     * <p>
     * 桩上游直接吐 {@code degraded L2} + chunk + done，等价于 zhixue-ai 在
     * {@code rag_service.py} 里 L1 两次失败后走 GLM 分支的线上形态。
     * 断言两点：前端能看到 {@code level=L2}（不是笼统的“降级了”），
     * 且 {@code ai_generation_log} 记为 L2 + glm-4-flash，论文与评阅据此可追溯。
     */
    @Test
    void l2DegradeIsRelayedAndPersisted() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-deg-l2", "KPITL2");

        String sse = body(explain(token, logId, "KPITL2"));
        assertThat(sse).contains("event:degraded", "\"level\":\"L2\"", "event:delta", "event:done");
        assertThat(sse).doesNotContain("\"level\":\"L3\"");

        String explainId = explainIdFromSse(sse);
        awaitExplainLog(explainId, userIdOf("it_user"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT degrade_level, is_degraded, model, content_text FROM ai_generation_log "
                        + "WHERE explain_id = ?", explainId);
        assertThat(row.get("degrade_level")).isEqualTo("L2");
        assertThat(((Number) row.get("is_degraded")).intValue()).isEqualTo(1);
        assertThat(row.get("model")).isEqualTo("glm-4-flash");
        assertThat(String.valueOf(row.get("content_text"))).contains("GLM 兜底讲解");
    }

    /**
     * IT-SSE-01：上游长时间静默时，Java 每 15 秒补一个 {@code : heartbeat} 注释帧，
     * 防止代理/浏览器把空闲连接当死连接掐掉（契约 5.2）。
     */
    @Test
    void heartbeatCommentIsSentWhileUpstreamIsSilent() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-heartbeat", "KPITHB");

        long begin = System.nanoTime();
        HttpResponse<InputStream> response = HTTP.send(
                explainRequest(token, logId, "KPITHB", 45),
                HttpResponse.BodyHandlers.ofInputStream());
        StringBuilder seen = new StringBuilder();
        try (InputStream stream = response.body();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                seen.append(line).append('\n');
                if (line.startsWith(":heartbeat")) {
                    break;
                }
            }
        }
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;

        assertThat(seen.toString()).contains(":heartbeat");
        // 心跳先于正文：读到心跳时上游仍在静默，不应已有 delta
        assertThat(seen.toString()).doesNotContain("event:delta");
        assertThat(elapsedMs).isGreaterThanOrEqualTo(14_000L);
        assertThat(elapsedMs).isLessThan(45_000L);
    }

    /**
     * IT-SSE-02：学生关页面（客户端断开）后，已生成正文必须照常留痕，供教师评阅；
     * 同时上游订阅要被取消，不能让模型空跑（契约 5.3）。
     */
    @Test
    void clientDisconnectStillWritesLog() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-disconnect", "KPITDRIP");

        HttpResponse<InputStream> response = HTTP.send(
                explainRequest(token, logId, "KPITDRIP", 30),
                HttpResponse.BodyHandlers.ofInputStream());
        StringBuilder prefix = new StringBuilder();
        try (InputStream stream = response.body();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                prefix.append(line).append('\n');
                if (line.startsWith("event:delta")) {
                    break;  // 拿到首段正文后立刻断开，模拟用户关闭页面
                }
            }
        }
        assertThat(prefix.toString()).contains("event:delta");

        String explainId = explainIdFromSse(prefix.toString());
        awaitExplainLog(explainId, userIdOf("it_user"));

        String content = jdbcTemplate.queryForObject(
                "SELECT content_text FROM ai_generation_log WHERE explain_id = ?",
                String.class, explainId);
        assertThat(content).contains("第一段讲解");
    }

    /**
     * IT-SSE-03：上游握手成功但一个字节都不发 → Java 侧首字节 10 秒超时 → 降 L3 本地模板，
     * 不能把连接挂到整体 60 秒。
     */
    @Test
    void firstByteTimeoutFallsBackToL3() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-stall", "KPITSTALL");

        long begin = System.nanoTime();
        String sse = body(explain(token, logId, "KPITSTALL"));
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;

        assertThat(sse).contains("\"level\":\"L3\"", "event:delta", "event:done");
        assertThat(elapsedMs).isGreaterThanOrEqualTo(10_000L);
        assertThat(elapsedMs).isLessThan(40_000L);

        String explainId = explainIdFromSse(sse);
        awaitExplainLog(explainId, userIdOf("it_user"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT degrade_level FROM ai_generation_log WHERE explain_id = ?",
                String.class, explainId)).isEqualTo("L3");
    }

    /**
     * IT-FLOW-02：低置信度 OCR 结果（&lt; 0.75）必须由学生显式确认才能生成讲解。
     * <p>
     * 契约 2.2 的“强制人工确认”在前端提示，服务端的兜底是状态机：
     * 从 OCR 结果建出来的记录只能是 DRAFT，DRAFT 调讲解必须被 40900 拦住；
     * 确认后同一份记录才能正常讲解。
     */
    @Test
    void lowConfidenceOcrRequiresManualConfirmBeforeExplain() throws Exception {
        String token = login();
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        HttpResponse<String> accepted = upload(token, "oct-low", jpg);
        assertThat(code(accepted)).isZero();
        String taskId = JsonPath.read(body(accepted), "$.data.taskId");

        Map<String, Object> task = awaitOcrTerminal(taskId, token);
        assertThat(task.get("status")).isEqualTo("SUCCESS");
        assertThat(((Number) task.get("confidence")).doubleValue()).isLessThan(0.75);
        assertThat(String.valueOf(task.get("ocrText"))).contains("低置信度题干");

        String draftId = JsonPath.read(body(postJson("/api/error/records", """
                {"courseId":1,"clientToken":"ct-low-conf","kpId":"KP0201","errorType":"CONCEPT_CONFUSION",
                 "ocrText":"低置信度题干：主键与外键的区别","ocrConfidence":0.62}
                """, token)), "$.data.logId");

        String rejected = body(explain(token, draftId));
        assertThat(rejected).contains("\"code\":40900");
        assertThat(rejected).doesNotContain("event:delta");

        assertThat(code(confirm(token, draftId))).isZero();
        assertThat(body(explain(token, draftId))).contains("event:delta", "event:done");
    }

    @Test
    void recommendationsPersistIdempotently() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-rec");

        HttpResponse<String> first = getJson("/api/recommendations?logId=" + logId + "&courseId=1", token);
        assertThat(code(first)).isZero();
        List<Integer> firstIds = JsonPath.read(body(first), "$.data[*].recommendationId");
        assertThat(firstIds).isNotEmpty();

        HttpResponse<String> second = getJson("/api/recommendations?logId=" + logId + "&courseId=1", token);
        assertThat(code(second)).isZero();
        List<Integer> secondIds = JsonPath.read(body(second), "$.data[*].recommendationId");
        assertThat(secondIds).isEqualTo(firstIds);
    }

    @Test
    void authAndRoleBoundaries() throws Exception {
        assertThat(code(getJson("/api/auth/me", null))).isEqualTo(40100);
        assertThat(code(getJson("/api/auth/me", "invalid-token"))).isEqualTo(40100);
        assertThat(code(getJson("/api/course/kp/KP0201", null))).isEqualTo(40100);

        String token = login();
        HttpResponse<String> me = getJson("/api/auth/me", token);
        assertThat(code(me)).isZero();
        assertThat((String) JsonPath.read(body(me), "$.data.role")).isEqualTo("STUDENT");

        HttpResponse<String> kp = getJson("/api/course/kp/KP0201", token);
        assertThat(code(kp)).isZero();
        assertThat((String) JsonPath.read(body(kp), "$.data.kpId")).isEqualTo("KP0201");
    }

    @Test
    void explainLogReadableByOwnerForReview() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-review");
        String explainId = explainIdFromSse(body(explain(token, logId)));
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM sys_user WHERE username = 'it_user'", Long.class);

        awaitExplainLog(explainId, userId);

        HttpResponse<String> review = getJson("/api/diagnosis/explain/" + explainId, token);
        assertThat(code(review)).isZero();
        assertThat((String) JsonPath.read(body(review), "$.data.content")).contains("第一段讲解。");
        assertThat((String) JsonPath.read(body(review), "$.data.model")).isEqualTo("stub-model");
        assertThat((String) JsonPath.read(body(review), "$.data.traceId")).isNotBlank();
        assertThat((Boolean) JsonPath.read(body(review), "$.data.degraded")).isFalse();
        assertThat((Boolean) JsonPath.read(body(review), "$.data.blocked")).isFalse();
        assertThat((String) JsonPath.read(body(review), "$.data.sources[0].kpId")).isEqualTo("KP0201");
    }

    @Test
    void concurrentConfirmOnlyOneSucceeds() throws Exception {
        String token = login();
        String logId = createRecord(token, "ct-concurrent", "KP0201");

        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return code(confirm(token, logId));
                }));
            }
            start.countDown();

            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                codes.add(future.get(20, TimeUnit.SECONDS));
            }
            assertThat(codes.stream().filter(c -> c == 0).count()).isEqualTo(1);
            assertThat(codes.stream().filter(c -> c == 40900).count()).isEqualTo(workers - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM student_error_record WHERE log_id = ?", String.class, logId))
                .isEqualTo("CONFIRMED");
    }

    private void awaitExplainLog(String explainId, long userId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ai_generation_log WHERE explain_id = ? AND user_id = ?",
                    Integer.class, explainId, userId);
            if (count != null && count == 1) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("讲解留痕未在 10 秒内写入: " + explainId);
    }

    private long userIdOf(String username) {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM sys_user WHERE username = ?", Long.class, username);
        assertThat(userId).isNotNull();
        return userId;
    }

    /** 轮询 OCR 任务到终态（SUCCESS/FAILED），返回 data 段。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitOcrTerminal(String taskId, String token) throws Exception {
        for (int i = 0; i < 60; i++) {
            HttpResponse<String> response = getJson("/api/error/tasks/" + taskId, token);
            assertThat(code(response)).isZero();
            Map<String, Object> data = JsonPath.read(body(response), "$.data");
            Object status = data.get("status");
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                return data;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("OCR 任务未在 12 秒内到达终态: " + taskId);
    }

    /** SSE 请求构造：forceRefresh=true，跳过缓存，保证每次真的打到桩上游。 */
    private HttpRequest explainRequest(String token, String logId, String kpId, int timeoutSeconds) {
        String payload = """
                {"logId":"%s","kpId":"%s","errorType":"CONCEPT_CONFUSION","questionText":"求闭包","forceRefresh":true}
                """.formatted(logId, kpId);
        return HttpRequest.newBuilder(uri("/api/diagnosis/explain"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("satoken", token)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void contractImplementedEndpointsAligned() throws Exception {
        HttpResponse<String> docsResponse = getJson("/v3/api-docs", null);
        assertThat(docsResponse.statusCode()).isEqualTo(200);
        Map<?, ?> generated = objectMapper.readValue(body(docsResponse), Map.class);

        Map<?, ?> frozen = new Yaml().load(
                Files.readString(ContractSchemaComparator.openapiPath(), StandardCharsets.UTF_8));

        assertThat(ContractSchemaComparator.compare(frozen, generated))
                .as("已实现端点的契约差异")
                .isEmpty();
    }

    /**
     * PT-01：15 学生并发刷错题本，P95 ≤1s。
     * <p>
     * 06 号文 §6 正式对象是错题列表（GET /api/error/records）；历史上用 POST map 做代理，
     * 已在 openapi 2.0.7 实现端点后改测正式对象。
     */
    @Test
    void errorRecordsP95UnderOneSecondWith15Threads() throws Exception {
        String token = login();
        // 预热一次，消除 JIT/连接池首包
        getJson("/api/error/records?page=1&size=10", token);

        String listPath = "/api/error/records?page=1&size=10";
        List<Long> samples = concurrentLatencies(15, 1, () -> {
            HttpResponse<String> resp = getJson(listPath, token);
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(code(resp)).isZero();
        });
        long p95 = percentile(samples, 95);
        System.out.println("[perf] pt01-error-records samples=" + samples.size() + " p95=" + p95 + "ms");
        assertThat(p95).isLessThanOrEqualTo(1000L);
    }

    /**
     * PT-02：10 并发讲解，分别测 meta 首字节与首个 delta，P95 ≤5s。
     * <p>
     * 06 号文 §6 要求 10 并发（历史版本用 15 并发分摊两用户，现对齐口径）；
     * forceRefresh 跳过缓存。10 并发刚好等于每用户每分钟限流阈值，单用户即可。
     */
    @Test
    void explainMetaAndDeltaP95UnderFiveSeconds() throws Exception {
        // 用 10 个不同客户端 token 创建错题，确保 10 条独立 logId
        String token = login("it_user");
        List<String> logIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logIds.add(createAndConfirm(token, "pt02-" + i));
        }

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<long[]>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                String payload = ("{\"logId\":\"%s\",\"kpId\":\"KP0201\","
                        + "\"errorType\":\"CONCEPT_CONFUSION\",\"questionText\":\"求闭包\","
                        + "\"forceRefresh\":true}").formatted(logIds.get(index));
                long t0 = System.nanoTime();
                HttpRequest request = HttpRequest.newBuilder(uri("/api/diagnosis/explain"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("satoken", token)
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<InputStream> response =
                        HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                long metaMs = -1;
                long deltaMs = -1;
                try (InputStream stream = response.body();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        long elapsed = (System.nanoTime() - t0) / 1_000_000;
                        if (metaMs < 0 && "event:meta".equals(trimmed)) {
                            metaMs = elapsed;
                        } else if (deltaMs < 0 && "event:delta".equals(trimmed)) {
                            deltaMs = elapsed;
                        }
                        if (metaMs >= 0 && deltaMs >= 0) {
                            break;
                        }
                    }
                }
                if (metaMs < 0 || deltaMs < 0) {
                    throw new IllegalStateException(
                            "SSE 未收到完整事件：meta=" + metaMs + ", delta=" + deltaMs);
                }
                return new long[]{metaMs, deltaMs};
            }));
        }
        start.countDown();
        List<Long> metaSamples = new ArrayList<>();
        List<Long> deltaSamples = new ArrayList<>();
        for (Future<long[]> future : futures) {
            long[] pair = future.get(30, TimeUnit.SECONDS);
            metaSamples.add(pair[0]);
            deltaSamples.add(pair[1]);
        }
        pool.shutdownNow();

        long metaP95 = percentile(metaSamples, 95);
        long deltaP95 = percentile(deltaSamples, 95);
        System.out.println("[perf] pt02-explain-meta samples=" + metaSamples.size() + " p95=" + metaP95 + "ms");
        System.out.println("[perf] pt02-explain-delta samples=" + deltaSamples.size() + " p95=" + deltaP95 + "ms");
        assertThat(metaP95).isLessThanOrEqualTo(5000L);
        assertThat(deltaP95).isLessThanOrEqualTo(5000L);
    }

    private void insertOcrTask(long userId, String taskId, String clientToken, String status) {
        jdbcTemplate.update("INSERT INTO ocr_task "
                        + "(task_id, user_id, client_token, image_path, status, retry_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '/tmp/it.jpg', ?, 0, NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 5 MINUTE)",
                taskId, userId, clientToken, status);
    }

    private String login() throws Exception {
        return login("it_user");
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = postJson("/api/auth/login",
                "{\"username\":\"%s\",\"password\":\"It!Pass123\"}".formatted(username), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(code(response)).isZero();
        return JsonPath.read(body(response), "$.data.token");
    }

    private String createAndConfirm(String token, String clientToken) throws Exception {
        return createAndConfirm(token, clientToken, "KP0201");
    }

    private String createAndConfirm(String token, String clientToken, String kpId) throws Exception {
        String logId = createRecord(token, clientToken, kpId);
        assertThat(code(confirm(token, logId, kpId))).isZero();
        return logId;
    }

    private String createRecord(String token, String clientToken, String kpId) throws Exception {
        HttpResponse<String> result = createRaw(token, clientToken, kpId);
        assertThat(code(result)).isZero();
        return JsonPath.read(body(result), "$.data.logId");
    }

    private HttpResponse<String> createRaw(String token, String clientToken, String kpId) throws Exception {
        String payload = """
                {"courseId":1,"clientToken":"%s","kpId":"%s","errorType":"CONCEPT_CONFUSION","correctedText":"求闭包"}
                """.formatted(clientToken, kpId);
        return postJson("/api/error/records", payload, token);
    }

    private HttpResponse<String> confirm(String token, String logId) throws Exception {
        return confirm(token, logId, "KP0201");
    }

    private HttpResponse<String> confirm(String token, String logId, String kpId) throws Exception {
        String payload = "{\"kpId\":\"%s\",\"errorType\":\"CONCEPT_CONFUSION\",\"correctedText\":\"求闭包\"}"
                .formatted(kpId);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/error/records/" + logId + "/confirm"))
                .header("Content-Type", "application/json")
                .header("satoken", token)
                .PUT(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> explain(String token, String logId) throws Exception {
        return explain(token, logId, "KP0201");
    }

    private HttpResponse<String> explain(String token, String logId, String kpId) throws Exception {
        String payload = """
                {"logId":"%s","kpId":"%s","errorType":"CONCEPT_CONFUSION","questionText":"求闭包","forceRefresh":false}
                """.formatted(logId, kpId);
        return postJson("/api/diagnosis/explain", payload, token);
    }

    private HttpResponse<String> postJson(String path, String payload, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("satoken", token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> getJson(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> upload(String token, String clientToken, byte[] fileBytes) throws Exception {
        String boundary = "----ZhiXueIT" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFormField(out, boundary, "clientToken", null,
                clientToken.getBytes(StandardCharsets.UTF_8));
        if (fileBytes != null) {
            writeFormField(out, boundary, "file", "a.jpg", fileBytes);
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/api/error/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("satoken", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void writeFormField(OutputStream out, String boundary, String name,
                                       String filename, byte[] value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\""
                + (filename == null ? "" : "; filename=\"" + filename + "\"") + "\r\n";
        out.write(disposition.getBytes(StandardCharsets.UTF_8));
        if (filename != null) {
            out.write("Content-Type: image/jpeg\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(value);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String explainIdFromSse(String body) {
        Matcher matcher = Pattern.compile("\"explainId\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    // ==================== 性能基线辅助 ====================

    private interface LatencyCall {
        void run() throws Exception;
    }

    private List<Long> concurrentLatencies(int threads, int rounds, LatencyCall call)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                List<Long> samples = new ArrayList<>();
                for (int r = 0; r < rounds; r++) {
                    long t0 = System.nanoTime();
                    call.run();
                    samples.add((System.nanoTime() - t0) / 1_000_000);
                }
                return samples;
            }));
        }
        start.countDown();
        List<Long> all = new ArrayList<>();
        for (Future<List<Long>> future : futures) {
            all.addAll(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return all;
    }

    private static long percentile(List<Long> samples, int p) {
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static String body(HttpResponse<String> response) {
        return response.body();
    }

    private static int code(HttpResponse<String> response) {
        return JsonPath.read(response.body(), "$.code");
    }

    private static String jdbcUrl() {
        String url = MYSQL.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "characterEncoding=utf8&useUnicode=true";
    }

    private static void executeScript(Connection connection, String path, boolean stripDatabase)
            throws Exception {
        String sql = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        if (stripDatabase) {
            sql = sql.replaceAll("(?s)CREATE DATABASE IF NOT EXISTS zhixue.*?;\\s*", "");
        }
        sql = sql.replaceAll("(?m)^USE zhixue;\\s*$", "");
        ScriptUtils.executeSqlScript(connection, new EncodedResource(
                new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }

    private static HttpServer startAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/rag/explain", IntegrationFlowTest::handleExplain);
            server.createContext("/api/ocr/recognize", IntegrationFlowTest::handleOcr);
            // 慢用例（心跳静默 16s、首字节静默 20s）会占住处理线程，必须多线程：
            // 沿用单线程默认执行器会让后续用例排队到超时。
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "stub-ai");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("启动桩 AI 服务失败", e);
        }
    }

    /** 桩 /api/rag/explain：按 kpId 模拟 L2 降级、长静默、首字节超时、中途断流与正常返回。 */
    private static void handleExplain(HttpExchange exchange) throws IOException {
        AI_CALLS.incrementAndGet();
        String requestBody = new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        if (requestBody.contains("\"kpId\":\"KPITL3\"")) {
            exchange.sendResponseHeaders(500, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            if (requestBody.contains("\"kpId\":\"KPITL2\"")) {
                // 等价于 Python 侧 L1 两次失败后走 GLM 分支的线上形态
                write(output, "event: degraded\ndata: {\"level\":\"L2\",\"reason\":\"L1 调用失败\"}\n\n");
                write(output, "event: chunk\ndata: {\"content\":\"GLM 兜底讲解。\"}\n\n");
                write(output, "event: done\ndata: {\"degraded\":true,\"blocked\":false,"
                        + "\"modelUsed\":\"glm-4-flash\",\"promptVersion\":\"v1\","
                        + "\"tokens\":8,\"promptTokens\":4}\n\n");
                return;
            }
            if (requestBody.contains("\"kpId\":\"KPITHB\"")) {
                pause(16_000);   // 让 Java 的 15 秒心跳先落地
                write(output, "event: chunk\ndata: {\"content\":\"心跳之后到达的讲解。\"}\n\n");
                write(output, "event: done\ndata: {\"degraded\":false,\"blocked\":false,"
                        + "\"modelUsed\":\"stub-model\",\"promptVersion\":\"v1\"}\n\n");
                return;
            }
            if (requestBody.contains("\"kpId\":\"KPITSTALL\"")) {
                pause(20_000);   // 握手后一个字节都不发：触发 Java 首字节 10s 超时
                return;
            }
            if (requestBody.contains("\"kpId\":\"KPITDRIP\"")) {
                write(output, "event: chunk\ndata: {\"content\":\"第一段讲解。\"}\n\n");
                pause(5_000);    // 客户端在此期间断开，第二段写入会失败
                write(output, "event: chunk\ndata: {\"content\":\"第二段讲解。\"}\n\n");
                return;
            }
            write(output, "event: chunk\ndata: {\"content\":\"第一段讲解。\"}\n\n");
            write(output, "event: done\ndata: {\"degraded\":false,\"blocked\":false,"
                    + "\"modelUsed\":\"stub-model\",\"promptVersion\":\"v1\",\"tokens\":10,\"promptTokens\":5}\n\n");
        } catch (IOException ignored) {
            // 客户端主动断开（IT-SSE-02）：桩侧写失败属预期，不是服务异常
        }
    }

    /** 桩 /api/ocr/recognize：固定返回 0.62 低置信度结果，用于 IT-FLOW-02。 */
    private static void handleOcr(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] payload = ("{\"text\":\"低置信度题干：主键与外键的区别\",\"confidence\":0.62,"
                + "\"lines\":[{\"text\":\"低置信度题干\",\"score\":0.62}],\"elapsedMs\":12}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private static void write(OutputStream output, String frame) throws IOException {
        output.write(frame.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
