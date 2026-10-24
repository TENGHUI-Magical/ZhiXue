package com.zhixue.diagnosis.dto;

/**
 * 推荐项（契约 Recommendation）。
 * 当前未落 recommendation 表，recommendationId 返回 null。
 *
 * @param recommendationId 推荐记录 ID；未持久化时为 null
 * @param qId              题目编号
 * @param layer            层：CONSOLIDATE / VARIANT / ADVANCE
 * @param reason           推荐理由，≤15 字
 * @param difficulty       难度 1—3
 * @param isAdopted        是否已被作答
 * @param questionText     题干
 */
public record Recommendation(Long recommendationId, String qId, String layer, String reason,
                             Integer difficulty, boolean isAdopted, String questionText) {
}
