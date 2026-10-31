package com.zhixue.plan.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.plan.dto.TeacherStudentViews;
import com.zhixue.plan.service.TeacherStudentService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 教师查看学生学习记录（契约 C12）。角色与课程归属校验在 service 内执行。
 */
@RestController
@RequestMapping("/api/teacher/students")
public class TeacherStudentController {

    private final TeacherStudentService teacherStudentService;

    public TeacherStudentController(TeacherStudentService teacherStudentService) {
        this.teacherStudentService = teacherStudentService;
    }

    @GetMapping
    public ApiResponse<TeacherStudentViews.Page> page(
            @RequestParam long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(schema = @Schema(allowableValues = {"lastActiveDesc", "masteryAsc", "practiceDesc"}))
            @RequestParam(defaultValue = "lastActiveDesc") String sort) {
        return ApiResponse.ok(teacherStudentService.page(courseId, page, size, sort));
    }

    @GetMapping("/{userId}/learning-records")
    public ApiResponse<TeacherStudentViews.LearningRecord> learningRecords(
            @PathVariable long userId,
            @RequestParam long courseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(teacherStudentService.learningRecords(
                courseId, userId, from, to, page, size));
    }
}
