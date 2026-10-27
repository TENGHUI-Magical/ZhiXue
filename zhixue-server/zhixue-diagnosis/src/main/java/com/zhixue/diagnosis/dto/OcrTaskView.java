package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * OCR 轮询响应（契约 2.2 节第二步 / openapi OcrTask）。
 * 字段与冻结契约一一对应：errorCode 为整数、错误文案字段为 errorMessage。
 *
 * @param taskId       任务 ID
 * @param status       PENDING / RUNNING / SUCCESS / FAILED
 * @param ocrText      识别文本；未完成时为 null
 * @param confidence   平均置信度；未完成时为 null
 * @param fields       结构化结果：stem/rawText/lines/structured/engine
 * @param errorCode    失败错误码（整数），如 60002
 * @param errorMessage 失败原因
 */
public record OcrTaskView(String taskId,
                          @Schema(allowableValues = {"PENDING", "RUNNING", "SUCCESS", "FAILED"})
                          String status, String ocrText, Double confidence,
                          Map<String, Object> fields, Integer errorCode, String errorMessage) {
}