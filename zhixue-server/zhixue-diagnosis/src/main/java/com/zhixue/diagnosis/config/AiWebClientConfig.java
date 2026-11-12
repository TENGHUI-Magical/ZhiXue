package com.zhixue.diagnosis.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * AI 服务 WebClient 统一配置（P0 修复）。
 *
 * 解决三处裸建 WebClient 的问题：
 * 1. 连接超时 2s / 响应超时 10s / 连接池 64 / pendingAcquire 2s
 * 2. 统一携带 X-Internal-Token 请求头（内部服务鉴权）
 * 3. 可被 Spring 注入复用，避免每次 new
 */
@Configuration
public class AiWebClientConfig {

    private final AiClientProperties properties;

    public AiWebClientConfig(AiClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public WebClient aiWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(10))
                .keepAlive(true);

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Internal-Token", properties.internalToken() == null ? "" : properties.internalToken())
                .build();
    }
}
