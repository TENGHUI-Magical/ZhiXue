package com.zhixue.it;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空库不变量：只跑 {@code init-v2.sql} 建出的 practice_record 索引集合，
 * 必须已经等于顺序执行迁移脚本后的终态，否则新环境拿不到
 * {@code /*+ INDEX(practice_record idx_user_window) *}{@code /} 依赖的覆盖索引，
 * hint 会被优化器静默忽略。
 *
 * <p>保留 {@code idx_course_agg} 的依据：全仓 grep 只在 init-v2.sql、迁移脚本注释与
 * docs/05 出现，没有 hint 强制依赖；docs/05 §2.5 与迁移脚本的逐条 EXPLAIN 结论都写明
 * 班级 {@code aggregateByUserKp}/{@code aggregateErrorType} 与单学生 {@code aggregateByUser}
 * 实际选中它，删除会让课程维度聚合退回范围扫描 + 回表。因此终态保留它，
 * 只删除被 {@code idx_user_window} 取代的 {@code idx_user_agg}。
 */
class InitV2IndexInvariantTest {

    private static final Path INIT_SQL = Path.of("../sql/init-v2.sql");
    private static final Path WINDOW_MIGRATION =
            Path.of("../sql/migration/2026-11-03-diagnosis-window-index.sql");

    @Test
    void practiceRecordIndexesAlreadyEqualMigrationTerminalState() throws IOException {
        String initSql = stripComments(Files.readString(INIT_SQL, StandardCharsets.UTF_8));
        String migrationSql = stripComments(Files.readString(WINDOW_MIGRATION, StandardCharsets.UTF_8));

        Set<String> initIndexes = practiceRecordIndexes(initSql);
        assertTrue(initIndexes.contains("idx_user_window"),
                "init-v2.sql 的 practice_record 缺少 idx_user_window，空库无法命中窗口覆盖索引；实际索引=" + initIndexes);
        assertFalse(initIndexes.contains("idx_user_agg"),
                "init-v2.sql 不应再出现已废弃的 idx_user_agg；实际索引=" + initIndexes);
        assertTrue(initIndexes.contains("idx_course_agg"),
                "idx_course_agg 仍是课程维度聚合的实际选中索引，不应删除；实际索引=" + initIndexes);
        assertTrue(initIndexes.contains("idx_user_q"),
                "答错热路径 countByUserAndQid 依赖 idx_user_q；实际索引=" + initIndexes);

        // idx_user_window 的列序必须与迁移脚本完全一致，否则 EXPLAIN 命中率与覆盖列会漂移
        assertEquals(columns(initSql, "KEY idx_user_window\\s*\\(([^)]*)\\)"),
                columns(migrationSql, "ADD INDEX idx_user_window\\s*\\(([^)]*)\\)"),
                "init-v2.sql 的 idx_user_window 列定义与迁移脚本不一致");

        // 终态 = init ∪ ADD INDEX − DROP INDEX；等于 init 才说明“建库即终态”
        Set<String> added = indexNames(migrationSql, "ADD INDEX\\s+(\\w+)");
        Set<String> dropped = indexNames(migrationSql, "DROP INDEX\\s+(\\w+)");
        assertEquals(Set.of("idx_user_window"), added, "迁移脚本应只新增 idx_user_window");
        assertEquals(Set.of("idx_user_agg"), dropped, "迁移脚本应只删除 idx_user_agg");

        Set<String> terminal = new LinkedHashSet<>(initIndexes);
        terminal.addAll(added);
        terminal.removeAll(dropped);
        assertEquals(initIndexes, terminal,
                "init-v2.sql 建库结果与迁移终态不一致：迁移会得到 " + terminal + "，init 只有 " + initIndexes);
    }

    /** 去掉整行 {@code --} 注释，避免注释里的示例文字（如 “ADD INDEX IF NOT EXISTS”）被当成 DDL。 */
    private String stripComments(String sql) {
        return sql.replaceAll("(?m)^[ \\t]*--.*$", "");
    }

    /** 解析 practice_record 建表块内的 PRIMARY KEY / UNIQUE KEY / KEY 名称。 */
    private Set<String> practiceRecordIndexes(String sql) {
        Matcher table = Pattern.compile(
                        "CREATE TABLE IF NOT EXISTS practice_record\\s*\\((.*?)\\n\\)\\s*ENGINE",
                        Pattern.DOTALL)
                .matcher(sql);
        assertTrue(table.find(), "init-v2.sql 中找不到 practice_record 建表块");

        Set<String> names = new LinkedHashSet<>();
        Matcher key = Pattern.compile(
                        "[ \\t]*(UNIQUE\\s+KEY|PRIMARY\\s+KEY|KEY)[ \\t]+(\\w+)?[ \\t]*\\(")
                .matcher(table.group(1));
        while (key.find()) {
            names.add(key.group(1).startsWith("PRIMARY") ? "PRIMARY" : key.group(2));
        }
        return names;
    }

    private Set<String> indexNames(String sql, String regex) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile(regex).matcher(sql);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private List<String> columns(String sql, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(sql);
        assertTrue(matcher.find(), "找不到匹配 " + regex + " 的索引定义");
        return Arrays.stream(matcher.group(1).split(",")).map(String::trim).toList();
    }
}
