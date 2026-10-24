package com.zhixue.diagnosis.dto;

import java.util.Map;

/**
 * OCR 轮询响应（契约 2.2 节第二步）。
 *
 * @param taskId     任务 ID
 * @param status     PENDING / RUNNING / SUCCESS / FAILED
 * @param confidence 平均置信度；未完成时为 null
 * @param fields     结构化结果：stem/rawText/lines/structured/engine
 * @param errorCode  失败错误码，如 60002
 * @param errorMsg   失败原因
 */
public record OcrTaskView(String taskId, String status, Double confidence,
                          Map<String, Object> fields, String errorCode, String errorMsg) {
}
