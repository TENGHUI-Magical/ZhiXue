package com.zhixue.it;

import com.jayway.jsonpath.JsonPath;
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

    @Test
    void upstreamFailureFallsBackToL3() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-l3", "KPITL3");

        String body = body(explain(token, logId, "KPITL3"));
        assertThat(body).contains("\"level\":\"L3\"", "event:delta", "event:done");
        assertThat(body).doesNotContain("\"code\":60001");
        assertThat(AI_CALLS.get()).isEqualTo(1);
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
        for (int i = 0; i < 50; i++) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ai_generation_log WHERE explain_id = ? AND user_id = ?",
                    Integer.class, explainId, userId);
            if (count != null && count == 1) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("讲解留痕未在 5 秒内写入: " + explainId);
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

    @Test
    void mapP95UnderOneSecondWith15Threads() throws Exception {
        String token = login();
        // 预热一次，消除 JIT/连接池首包
        postJson("/api/diagnosis/map",
                "{\"text\":\"关系模型的三要素是什么\",\"courseId\":1}", token);

        // 06 号文 PT-01：15 并发单轮，P95 取这一轮的最大值
        List<Long> samples = concurrentLatencies(15, 1, () -> postJson("/api/diagnosis/map",
                "{\"text\":\"事务的ACID特性有哪些\",\"courseId\":1}", token));
        long p95 = percentile(samples, 95);
        System.out.println("[perf] map samples=" + samples.size() + " p95=" + p95 + "ms");
        assertThat(p95).isLessThanOrEqualTo(1000L);
    }

    @Test
    void explainFirstByteP95UnderFiveSeconds() throws Exception {
        // 15 并发分摊到两个用户（讲解限流 10 次/分/用户），forceRefresh 跳过缓存
        String token1 = login("it_user");
        String token2 = login("it_user2");
        List<String> logIds = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            logIds.add(createAndConfirm(token1, "ctp1-" + i));
            tokens.add(token1);
        }
        for (int i = 0; i < 7; i++) {
            logIds.add(createAndConfirm(token2, "ctp2-" + i));
            tokens.add(token2);
        }

        int threads = 15;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                String payload = ("{\"logId\":\"%s\",\"kpId\":\"KP0201\","
                        + "\"errorType\":\"CONCEPT_CONFUSION\",\"questionText\":\"求闭包\","
                        + "\"forceRefresh\":true}").formatted(logIds.get(index));
                long t0 = System.nanoTime();
                HttpRequest request = HttpRequest.newBuilder(uri("/api/diagnosis/explain"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("satoken", tokens.get(index))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<InputStream> response =
                        HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                boolean firstDelta = false;
                try (InputStream stream = response.body();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if ("event:delta".equals(line.trim())) {
                            firstDelta = true;
                            break;
                        }
                    }
                }
                if (!firstDelta) {
                    throw new IllegalStateException("SSE 未收到首个 delta");
                }
                return (System.nanoTime() - t0) / 1_000_000;
            }));
        }
        start.countDown();
        List<Long> samples = new ArrayList<>();
        for (Future<Long> future : futures) {
            samples.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        long p95 = percentile(samples, 95);
        System.out.println("[perf] explain-ttfb samples=15 p95=" + p95 + "ms");
        assertThat(p95).isLessThanOrEqualTo(5000L);
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
            server.createContext("/api/rag/explain", exchange -> {
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
                    write(output, "event: chunk\ndata: {\"content\":\"第一段讲解。\"}\n\n");
                    write(output, "event: done\ndata: {\"degraded\":false,\"blocked\":false,"
                            + "\"modelUsed\":\"stub-model\",\"promptVersion\":\"v1\",\"tokens\":10,\"promptTokens\":5}\n\n");
                }
            });
            server.setExecutor(null);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("启动桩 AI 服务失败", e);
        }
    }

    private static void write(OutputStream output, String frame) throws IOException {
        output.write(frame.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
