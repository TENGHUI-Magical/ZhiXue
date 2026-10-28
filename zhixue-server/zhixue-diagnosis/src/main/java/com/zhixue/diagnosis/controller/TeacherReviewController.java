package com.zhixue.diagnosis.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.ReviewPageView;
import com.zhixue.diagnosis.dto.ReviewSubmitRequest;
import com.zhixue.diagnosis.service.TeacherReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师评阅接口（契约 V2 teacher 组）。
 * 角色校验在 service 内执行：StpUtil.checkRole("TEACHER")，学生访问 → 40300。
 */
@RestController
@RequestMapping("/api/teacher/reviews")
public class TeacherReviewController {

    private final TeacherReviewService teacherReviewService;

    public TeacherReviewController(TeacherReviewService teacherReviewService) {
        this.teacherReviewService = teacherReviewService;
    }

    @GetMapping
    public ApiResponse<ReviewPageView> queue(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(teacherReviewService.page(page, size));
    }

    @PostMapping("/{logPk}")
    public ApiResponse<Void> submit(@PathVariable long logPk,
                                    @Valid @RequestBody ReviewSubmitRequest request) {
        teacherReviewService.submit(logPk, request);
        return ApiResponse.ok(null);
    }
}
