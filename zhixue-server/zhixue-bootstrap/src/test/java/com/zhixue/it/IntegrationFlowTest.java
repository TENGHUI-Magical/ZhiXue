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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 MySQL + Redis 的核心链路集成测试。
 * <p>
 * 默认 {@code @Tag("it")} 被 Surefire 排除，避免没有 Docker 的本地环境失败；
 * CI 用 {@code mvn -Pit verify} 并设置 RUN_IT=true 执行。
 * AI 服务用 JDK HttpServer 桩替代，不依赖真实模型。
 */
@Tag("it")
@SpringBootTest
class IntegrationFlowTest {

    private static final boolean RUN_IT = "true".equalsIgnoreCase(System.getenv("RUN_IT"));
    private static final AtomicInteger AI_CALLS = new AtomicInteger();

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

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private OcrService ocrService;

    /** 测试用户口令：It!Pass123 的 BCrypt 哈希（仅测试库使用） */
    private static final String TEST_HASH = "$2a$10$xARWaBRcoRVDmdtPMopUJecaI4F5/vskTeAA3IcIA9LIULv6v6XPO";

    private MockMvc mockMvc;

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
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_user (username, password, real_name, role, class_name, is_active) "
                            + "VALUES (?, ?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE "
                            + "password = VALUES(password), real_name = VALUES(real_name), "
                            + "role = VALUES(role), class_name = VALUES(class_name), is_active = 1")) {
                ps.setString(1, "it_user");
                ps.setString(2, TEST_HASH);
                ps.setString(3, "集成测试");
                ps.setString(4, "STUDENT");
                ps.setString(5, "IT");
                ps.executeUpdate();
            }
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
    void setUpAndCleanData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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

        MvcResult confirmed = confirm(token, firstLogId);
        assertThat(code(confirmed)).isZero();
        assertThat((String) JsonPath.read(body(confirmed), "$.data.status")).isEqualTo("CONFIRMED");

        MvcResult duplicate = confirm(token, firstLogId);
        assertThat(code(duplicate)).isEqualTo(40900);
    }

    @Test
    void createRejectsUnknownKpWithoutInserting() throws Exception {
        String token = login();
        MvcResult result = createRaw(token, "ct-bad", "KP9999");

        assertThat(code(result)).isEqualTo(40400);
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_error_record WHERE client_token = 'ct-bad'", Integer.class);
        assertThat(rowCount).isZero();
    }

    @Test
    void explainStreamsThenHitsCache() throws Exception {
        String token = login();
        String logId = createAndConfirm(token, "ct-sse");

        MvcResult first = explain(token, logId);
        String firstBody = body(first);
        assertThat(firstBody).contains("event:meta", "event:source", "event:delta", "event:done");
        assertThat(firstBody).doesNotContain("\"cached\":true");
        assertThat(AI_CALLS.get()).isEqualTo(1);

        MvcResult second = explain(token, logId);
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

        MvcResult rejected = explain(token, logId);
        assertThat(body(rejected)).contains("\"code\":42900");
        assertThat(rejected.getResponse().getHeader("Retry-After")).isNotBlank();
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

    private void insertOcrTask(long userId, String taskId, String clientToken, String status) {
        jdbcTemplate.update("INSERT INTO ocr_task "
                        + "(task_id, user_id, client_token, image_path, status, retry_count, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '/tmp/it.jpg', ?, 0, NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 5 MINUTE)",
                taskId, userId, clientToken, status);
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it_user\",\"password\":\"It!Pass123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(code(result)).isZero();
        return JsonPath.read(body(result), "$.data.token");
    }

    private String createAndConfirm(String token, String clientToken) throws Exception {
        String logId = createRecord(token, clientToken, "KP0201");
        assertThat(code(confirm(token, logId))).isZero();
        return logId;
    }

    private String createRecord(String token, String clientToken, String kpId) throws Exception {
        MvcResult result = createRaw(token, clientToken, kpId);
        assertThat(code(result)).isZero();
        return JsonPath.read(body(result), "$.data.logId");
    }

    private MvcResult createRaw(String token, String clientToken, String kpId) throws Exception {
        String payload = """
                {"courseId":1,"clientToken":"%s","kpId":"%s","errorType":"CONCEPT_CONFUSION","correctedText":"求闭包"}
                """.formatted(clientToken, kpId);
        return mockMvc.perform(post("/api/error/records")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult confirm(String token, String logId) throws Exception {
        return mockMvc.perform(put("/api/error/records/{logId}/confirm", logId)
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpId\":\"KP0201\",\"errorType\":\"CONCEPT_CONFUSION\",\"correctedText\":\"求闭包\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult explain(String token, String logId) throws Exception {
        String payload = """
                {"logId":"%s","kpId":"KP0201","errorType":"CONCEPT_CONFUSION","questionText":"求闭包","forceRefresh":false}
                """.formatted(logId);
        MvcResult result = mockMvc.perform(post("/api/diagnosis/explain")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }
        return result;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static int code(MvcResult result) throws Exception {
        return JsonPath.read(body(result), "$.code");
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
