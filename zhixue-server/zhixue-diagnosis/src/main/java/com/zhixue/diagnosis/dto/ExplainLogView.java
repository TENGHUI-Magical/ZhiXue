package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 讲解记录查询响应（契约 V2 §5.4 / openapi ExplainLog）。
 * <p>
 * 评阅回流三字段（05 §4.6）为该讲解自身的复核状态：reviewResult 只表示
 * “教师看过该讲解并给出结论”，PASS 不等于答案正确；teacherScore 不回流。
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
 * @param reviewResult 教师复核结论 PASS / RETURN；未评阅为 null
 * @param reviewComment 教师评语（RETURN 必有，PASS 可无）；未评阅为 null
 * @param reviewedAt  复核时间；未评阅为 null
 */
public record ExplainLogView(String explainId, String traceId, String model, boolean degraded,
                             @Schema(nullable = true,
                                     allowableValues = {"L2", "L3", "L4"}) String degradeLevel,
                             String content, List<SourceView> sources,
                             boolean blocked, String createdAt,
                             @Schema(nullable = true, allowableValues = {"PASS", "RETURN"}) String reviewResult,
                             String reviewComment, String reviewedAt) {

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
