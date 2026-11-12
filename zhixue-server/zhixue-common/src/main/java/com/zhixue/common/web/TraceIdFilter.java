package com.zhixue.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 请求追踪过滤器（契约 V2 §1.2）：
 * 每次请求生成 traceId，写入响应头 X-Trace-Id 并放入 request attribute，
 * 供 SSE 等异步场景在请求线程内取用；同时写 MDC 便于日志按 trace 聚合。
 * P0 修复：traceId 从 8-hex（32bit，高并发碰撞）升级为 16-hex（64bit，碰撞概率可忽略）。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String ATTRIBUTE = "traceId";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // P0 修复：16-hex（64bit）替代 8-hex（32bit），降低高并发碰撞概率
        String traceId = "t-" + LocalDate.now().format(DAY) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        response.setHeader(HEADER, traceId);
        request.setAttribute(ATTRIBUTE, traceId);
        MDC.put("traceId", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
