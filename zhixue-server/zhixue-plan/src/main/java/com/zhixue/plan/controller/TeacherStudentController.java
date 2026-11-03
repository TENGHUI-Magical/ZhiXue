package com.zhixue.plan.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.plan.dto.TeacherStudentViews;
import com.zhixue.plan.service.TeacherStudentService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 教师查看学生学习记录（契约 C12）。角色与课程归属校验在 service 内执行。
 */
@RestController
@RequestMapping("/api/teacher/students")
public class TeacherStudentController {

    /** 文件日期印章统一上海口径，不随 JVM 默认时区漂移（docs/13 B 组）。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

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

    /**
     * 班级学情 CSV 导出（学期材料）：完整筛选结果而非当前分页；权限与列表一致
     * （TEACHER + 课程归属，service 内校验）；UTF-8 + BOM、中文表头。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam long courseId,
            @Parameter(schema = @Schema(allowableValues = {"lastActiveDesc", "masteryAsc", "practiceDesc"}))
            @RequestParam(defaultValue = "lastActiveDesc") String sort) {
        byte[] csv = teacherStudentService.exportCsv(courseId, sort);
        String filename = "class-learning-" + courseId + "-"
                + LocalDate.now(ZONE).format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename,
                StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(csv);
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
