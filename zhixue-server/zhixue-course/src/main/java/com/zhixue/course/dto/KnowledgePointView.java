package com.zhixue.course.dto;

/**
 * 知识点视图（契约 openapi KnowledgePoint）。
 * 对外只暴露契约字段：补齐 chapterName，隐藏 createdBy/isActive/时间戳等内部列。
 */
public record KnowledgePointView(String kpId, Long courseId, Long chapterId, String chapterName,
                                String name, String conceptText, String misconception,
                                String textbookPage, String trialHint, Integer sortNo,
                                String dimensionKey) {
}