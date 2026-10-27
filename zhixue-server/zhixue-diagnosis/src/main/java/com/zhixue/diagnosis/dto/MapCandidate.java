package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 映射候选（契约 MapCandidate）。
 *
 * @param kpId    知识点编号
 * @param name    知识点名称
 * @param score   相似度，0—1
 * @param channel 命中通道：KEYWORD / VECTOR / LLM / FUSED
 */
public record MapCandidate(String kpId, String name, double score,
                           @Schema(allowableValues = {"KEYWORD", "VECTOR", "LLM", "FUSED"})
                           String channel) {
}
