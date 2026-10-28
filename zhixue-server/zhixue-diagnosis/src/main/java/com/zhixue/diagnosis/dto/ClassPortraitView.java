package com.zhixue.diagnosis.dto;

import java.util.List;
import java.util.Map;

/**
 * 班级画像视图（契约 openapi ClassPortrait）。
 */
public record ClassPortraitView(Long courseId, Integer studentCount,
                                List<WeakestKpTop> weakestKpTop,
                                Map<String, Integer> errorDist,
                                Double activeRate) {

    public record WeakestKpTop(String kpId, String name, Double avgMastery, Integer errorCount) {
    }
}
