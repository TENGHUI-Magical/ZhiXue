package com.zhixue.course.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.course.dto.ChapterView;
import com.zhixue.course.dto.CourseView;
import com.zhixue.course.dto.KnowledgeGraphView;
import com.zhixue.course.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程接口（契约 V2 第 7 节 course 组）。
 * 注意：路径是复数 /api/courses，与已有单数 /api/course/kp/{kpId} 并存。
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<List<CourseView>> list() {
        return ApiResponse.ok(courseService.listActive());
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<ChapterView>> chapters(@PathVariable Long courseId) {
        return ApiResponse.ok(courseService.listChapters(courseId));
    }

    @GetMapping("/{courseId}/knowledge-graph")
    public ApiResponse<KnowledgeGraphView> knowledgeGraph(@PathVariable Long courseId) {
        return ApiResponse.ok(courseService.knowledgeGraph(courseId));
    }
}
