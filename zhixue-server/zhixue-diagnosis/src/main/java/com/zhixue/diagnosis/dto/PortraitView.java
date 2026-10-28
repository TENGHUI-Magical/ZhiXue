package com.zhixue.diagnosis.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学生画像视图（契约 openapi Portrait）。
 */
public record PortraitView(LocalDate statDate,
                           Map<String, Double> dimensionScores,
                           List<WeakestKp> weakestKps,
                           Map<String, Integer> errorDist,
                           Integer totalPractice,
                           Integer totalError) {

    public record WeakestKp(String kpId, String name, Double mastery, Integer sampleCount) {
    }
}
