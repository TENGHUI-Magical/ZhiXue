package com.zhixue.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 课程视图（契约 openapi Course）。
 */
public record CourseView(Long courseId, String courseName, String textbook, String description,
                         List<DimensionDef> dimensionConfig) {

    /** 五维能力配置项；openapi 要求 key/name 必填。 */
    public record DimensionDef(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
    }
}
