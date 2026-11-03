package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

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
