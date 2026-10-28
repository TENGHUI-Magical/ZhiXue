package com.zhixue.course.dto;

import java.util.List;

/**
 * 知识图谱视图（契约 openapi KnowledgeGraph）。
 * 边方向：source = 先修知识点，target = 后继知识点；relation 固定 PREREQ。
 */
public record KnowledgeGraphView(List<Node> nodes, List<Edge> edges) {

    public record Node(String id, String name, Long chapterId) {
    }

    public record Edge(String source, String target, String relation) {
    }
}
