package com.zhixue.diagnosis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 开始作答打点请求（契约 openapi PracticeStart；进步证据的服务端计时入口）。
 * clientToken 即 startToken：提交作答时原样携带，服务端据此取回 begin 时刻。
 */
public record PracticeStartRequest(
        @NotBlank(message = "qId 不能为空") String qId,
        @NotBlank(message = "clientToken 不能为空")
        @Size(max = 64, message = "clientToken 最长 64 字") String clientToken) {
}
