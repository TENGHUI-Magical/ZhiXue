package com.zhixue.system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话配置守卫：token 绝对有效期、活跃超时、单会话轮换三项必须存在于 application.yml。
 * 删任一项或把 timeout 改回 30 天，本用例即失败。
 */
class SessionConfigTest {

    @Test
    void saTokenSessionIsHardened() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        PropertySource<?> properties = sources.get(0);

        assertThat(String.valueOf(properties.getProperty("sa-token.timeout"))).isEqualTo("604800");
        assertThat(String.valueOf(properties.getProperty("sa-token.active-timeout"))).isEqualTo("7200");
        assertThat(String.valueOf(properties.getProperty("sa-token.is-concurrent"))).isEqualTo("false");
    }
}
