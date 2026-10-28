package com.zhixue.diagnosis.dto;

import java.util.List;

/**
 * 教师评阅队列项（契约 openapi ReviewItem）。
 */
public record ReviewItemView(Long logPk, String explainId, String userName, String kpId,
                             String errorType, String model, boolean degraded, String content,
                             List<SourceRef> sources, String createdAt) {

    public record SourceRef(String kpId, String textbookPage, String title) {
    }
}
