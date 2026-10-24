package com.zhixue.diagnosis.dto;

/**
 * OCR 提交响应（契约 2.2 节第一步）：{ "taskId": "..." }
 */
public record OcrAccepted(String taskId) {
}
