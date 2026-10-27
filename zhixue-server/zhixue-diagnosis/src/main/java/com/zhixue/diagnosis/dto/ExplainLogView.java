package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 讲解记录查询响应（契约 V2 §5.4 / openapi ExplainLog）。
 *
 * @param explainId   讲解编号
 * @param traceId     全链路追踪 ID
 * @param model       生成模型（cache 表示缓存回放）
 * @param degraded    是否降级
 * @param degradeLevel 降级级别；未降级为 null
 * @param content     已生成正文
 * @param sources     来源列表
 * @param blocked     是否触发学术诚信拦截
 * @param createdAt   生成时间（ISO-8601）
 */
public record ExplainLogView(String explainId, String traceId, String model, boolean degraded,
                             @Schema(nullable = true,
                                     allowableValues = {"L1", "L2", "L3", "L4"}) String degradeLevel,
                             String content, List<SourceView> sources,
                             boolean blocked, String createdAt) {

    /**
     * 来源引用（openapi SourceRef）。
     *
     * @param kpId         知识点编号
     * @param textbookPage 教材页码
     * @param title        知识点名称
     */
    public record SourceView(String kpId, String textbookPage, String title) {
    }
}
