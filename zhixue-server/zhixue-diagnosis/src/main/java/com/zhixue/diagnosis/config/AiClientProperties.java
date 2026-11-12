package com.zhixue.diagnosis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * zhixue-ai 网关连接配置（绑定 application.yml 中 zhixue.ai.*）。
 * diagnosis 模块调用 FastAPI 三路由时统一读取本类：
 * 超时 timeoutSeconds 触发 60001 降级（接口契约 2.4 节）。
 *
 * @param baseUrl        AI 服务地址，如 http://localhost:8000（不对外网暴露）
 * @param timeoutSeconds 调用超时秒数，契约约定 10s
 * @param internalToken  内部服务鉴权令牌，随 X-Internal-Token 头传递（P0 修复）
 */
@ConfigurationProperties(prefix = "zhixue.ai")
public record AiClientProperties(String baseUrl, int timeoutSeconds, String internalToken) {
}
