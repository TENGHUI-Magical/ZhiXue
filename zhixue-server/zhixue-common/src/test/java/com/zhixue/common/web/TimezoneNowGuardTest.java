package com.zhixue.common.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时区口径守门（docs/13 规则 8）：主源一律不得出现不带 ZoneId 的
 * {@code LocalDateTime.now()} / {@code LocalDate.now()}。
 * <p>
 * 扫描 zhixue-server 下所有模块的 {@code src/main/java}；发现即失败。确需例外时，
 * 必须在本类 {@link #ALLOWANCES} 按「文件 + 精确代码行」逐条登记理由——按行登记可避免
 * 整个文件被放行，白名单所在文件里新增任何裸 now() 仍会变红。
 */
class TimezoneNowGuardTest {

    private static final Pattern BARE_NOW = Pattern.compile(
            "\\b(LocalDateTime|LocalDate)\\s*\\.\\s*now\\s*\\(\\s*\\)");

    /** 白名单：相对路径 + 精确代码行 + 理由。 */
    private static final List<Allowance> ALLOWANCES = List.of(
            new Allowance(
                    "zhixue-common/src/main/java/com/zhixue/common/web/TraceIdFilter.java",
                    "String traceId = \"t-\" + LocalDate.now().format(DAY) + \"-\"",
                    "docs/13 B7：traceId 的日期段只写响应头/MDC，不落库、不参与业务时间口径；"
                            + "改成上海时区只会影响日志外观，不影响任何业务数据"));

    @Test
    void mainSourcesMustNotUseTimeZoneNaiveNow() throws IOException {
        Path serverRoot = serverRoot();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(serverRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(TimezoneNowGuardTest::isMainSource)
                    .forEach(path -> scan(serverRoot, path, violations));
        }
        assertTrue(violations.isEmpty(),
                "主源出现不带 ZoneId 的 now()（docs/13 规则 8），请改用 now(ZONE)，"
                        + "或在 ALLOWANCES 逐条登记理由：\n" + String.join("\n", violations));
    }

    private static void scan(Path root, Path file, List<String> violations) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取源码失败: " + relative, e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String code = lines.get(i);
            Matcher matcher = BARE_NOW.matcher(code);
            if (matcher.find() && !isAllowed(relative, code.trim())) {
                violations.add(relative + ":" + (i + 1) + " -> " + code.trim());
            }
        }
    }

    private static boolean isAllowed(String relative, String code) {
        return ALLOWANCES.stream().anyMatch(allowance -> allowance.file().equals(relative)
                && allowance.line().equals(code));
    }

    private static boolean isMainSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return !normalized.contains("/target/") && normalized.contains("/src/main/java/");
    }

    /** 从当前工作目录向上定位 zhixue-server 根（模块目录 / 仓库根启动都能找到）。 */
    private static Path serverRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 8 && candidate != null; depth++) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("zhixue-diagnosis"))
                    && Files.isDirectory(candidate.resolve("zhixue-common"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("未找到 zhixue-server 根目录（当前工作目录："
                + System.getProperty("user.dir") + "）");
    }

    private record Allowance(String file, String line, String reason) {
    }
}
