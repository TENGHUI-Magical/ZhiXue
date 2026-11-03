package com.zhixue.diagnosis.dto;

/**
 * 开始作答打点响应（契约 openapi PracticeStartView）。
 * 幂等：同 clientToken 重复打点返回首次的开始时刻，不会重置计时。
 *
 * @param clientToken 原样返回，提交作答时作为 startToken 携带
 * @param startedAt   服务端记录的开始时刻（yyyy-MM-dd HH:mm:ss，Asia/Shanghai）
 */
public record PracticeStartView(String clientToken, String startedAt) {
}
