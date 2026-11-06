package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注解 SQL 的回归门禁：
 * <ol>
 *   <li>所有 {@code @Select} 必须能被 MyBatis 的 XML 解析器接受——{@code <script>} 里的
 *       {@code <>} 必须写成 {@code &lt;&gt;}，否则应用启动即失败（单测/集成测试都不覆盖注册阶段）。</li>
 *   <li>窗口覆盖索引 hint 只能出现在“有 user 过滤”的调用形态，课程维度查询不得被强制走
 *       user 前缀索引，否则会退化成按窗口扫全课程。</li>
 *   <li>hint 指向的索引必须真实存在于空库建表脚本 {@code sql/init-v2.sql}——索引不存在时
 *       MySQL 会静默忽略 hint，测出来的收益在新部署上等于零。</li>
 * </ol>
 */
class PracticeRecordMapperSqlTest {

    @Test
    void everySelectAnnotationParses() {
        XMLLanguageDriver driver = new XMLLanguageDriver();
        MybatisConfiguration configuration = new MybatisConfiguration();
        Method[] methods = PracticeRecordMapper.class.getDeclaredMethods();
        int parsed = 0;
        for (Method method : methods) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String script = String.join("\n", select.value());
            assertDoesNotThrow(
                    () -> driver.createSqlSource(configuration, script, method.getParameterTypes()[0]),
                    "SQL 解析失败：" + method.getName());
            parsed++;
        }
        assertTrue(parsed >= 10, "应至少解析 10 条注解 SQL，实际 " + parsed);
    }

    @Test
    void windowIndexHintOnlyAppearsWithUserFilter() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(PracticeRecordMapper.class);

        String withUser = sqlOf(configuration, "aggregateByUser",
                params("qIds", List.of("Q1"), "userIds", List.of(1L), "since", null));
        String withoutUser = sqlOf(configuration, "aggregateByUser",
                params("qIds", List.of("Q1"), "userIds", List.of(), "since", null));
        assertTrue(withUser.contains("idx_user_window"));
        assertFalse(withoutUser.contains("idx_user_window"));

        assertTrue(sqlOf(configuration, "aggregateByUserKp",
                params("qIds", List.of("Q1"), "userIds", List.of(1L),
                        "halfLifeDays", 30.0, "now", null, "since", null))
                .contains("idx_user_window"));
        assertTrue(sqlOf(configuration, "aggregateErrorType",
                params("qIds", List.of("Q1"), "userId", 1L, "since", null))
                .contains("idx_user_window"));
        assertFalse(sqlOf(configuration, "aggregateErrorType",
                params("qIds", List.of("Q1"), "userId", null, "since", null))
                .contains("idx_user_window"));
    }

    /**
     * hint 指向的索引必须真实存在于空库建表脚本，否则 MySQL 静默忽略 hint——
     * 窗口收敛的实测收益在新部署上归零，且不会报任何错。
     */
    @Test
    void everyIndexHintTargetExistsInInitSql() throws IOException {
        Set<String> hinted = hintedIndexes();
        assertTrue(hinted.contains("idx_user_window"),
                "窗口聚合应保留 idx_user_window hint；实际 hint 目标=" + hinted);
        Set<String> defined = practiceRecordIndexKeys();
        for (String index : hinted) {
            assertTrue(defined.contains(index),
                    "hint 目标 " + index + " 不在 init-v2.sql 的 practice_record 中，MySQL 会静默忽略；实际索引="
                            + defined);
        }
        assertFalse(defined.contains("idx_user_agg"),
                "init-v2.sql 不应再建已废弃的 idx_user_agg（被 idx_user_window 取代）；实际索引=" + defined);
    }

    private Set<String> hintedIndexes() {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("/\\*\\+\\s*INDEX\\(\\s*practice_record\\s+(\\w+)")
                .matcher(annotatedSql());
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private String annotatedSql() {
        StringBuilder sql = new StringBuilder();
        for (Method method : PracticeRecordMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select != null) {
                sql.append(String.join("\n", select.value())).append('\n');
            }
        }
        return sql.toString();
    }

    private Set<String> practiceRecordIndexKeys() throws IOException {
        Path initSql = Path.of("../sql/init-v2.sql");
        assertTrue(Files.exists(initSql), "找不到 " + initSql.toAbsolutePath()
                + "（surefire 工作目录=" + Path.of("").toAbsolutePath() + "）");
        String sql = Files.readString(initSql, StandardCharsets.UTF_8)
                .replaceAll("(?m)^[ \\t]*--.*$", "");
        Matcher table = Pattern.compile(
                        "CREATE TABLE IF NOT EXISTS practice_record\\s*\\((.*?)\\n\\)\\s*ENGINE",
                        Pattern.DOTALL)
                .matcher(sql);
        assertTrue(table.find(), "init-v2.sql 中找不到 practice_record 建表块");
        Set<String> names = new LinkedHashSet<>();
        Matcher key = Pattern.compile("(?m)^[ \\t]*(?:UNIQUE[ \\t]+)?KEY[ \\t]+(\\w+)")
                .matcher(table.group(1));
        while (key.find()) {
            names.add(key.group(1));
        }
        return names;
    }

    private Map<String, Object> params(Object... pairs) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private String sqlOf(MybatisConfiguration configuration, String id, Map<String, Object> params) {
        MappedStatement statement = configuration.getMappedStatement(
                PracticeRecordMapper.class.getName() + "." + id);
        BoundSql boundSql = statement.getBoundSql(params);
        return boundSql.getSql();
    }
}
