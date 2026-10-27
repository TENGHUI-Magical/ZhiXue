package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 错题记录视图（契约 V2 §7 / openapi ErrorRecord）。
 * <p>
 * 对外不直出实体：clientToken 等内部幂等字段不返回，kpName 由知识点表补齐。
 *
 * @param logId         错题记录编号
 * @param userId        学生 user_id
 * @param qId           关联题库题目编号
 * @param kpId          知识点编号
 * @param kpName        知识点名称（查询时补齐）
 * @param imageUrl      错题图片路径
 * @param ocrText       OCR 原文
 * @param correctedText 学生修正后的题干
 * @param ocrConfidence OCR 置信度
 * @param errorType     五类错因之一
 * @param studentNote   学生备注
 * @param status        DRAFT / CONFIRMED
 * @param confirmedAt   确认时间（ISO-8601）
 * @param createdAt     创建时间（ISO-8601）
 */
public record ErrorRecordView(String logId, Long userId, String qId, String kpId, String kpName,
                              String imageUrl, String ocrText, String correctedText,
                              BigDecimal ocrConfidence, String errorType, String studentNote,
                              @Schema(allowableValues = {"DRAFT", "CONFIRMED"}) String status,
                              String confirmedAt, String createdAt) {
}
