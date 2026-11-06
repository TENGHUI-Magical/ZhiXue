package com.zhixue.diagnosis.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.ReviewPageView;
import com.zhixue.diagnosis.dto.ReviewSubmitRequest;
import com.zhixue.diagnosis.service.TeacherReviewService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 教师评阅接口（契约 V2 teacher 组）。
 * 角色校验在 service 内执行：StpUtil.checkRole("TEACHER")，学生访问 → 40300。
 */
@RestController
@RequestMapping("/api/teacher/reviews")
public class TeacherReviewController {

    /** 文件日期印章统一上海口径，不随 JVM 默认时区漂移（docs/13 B 组）。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final TeacherReviewService teacherReviewService;

    public TeacherReviewController(TeacherReviewService teacherReviewService) {
        this.teacherReviewService = teacherReviewService;
    }

    @GetMapping
    public ApiResponse<ReviewPageView> queue(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @Parameter(schema = @Schema(allowableValues = {"PENDING", "REVIEWED", "ALL"}))
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(required = false) String kpId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(teacherReviewService.page(page, size, status, kpId, from, to));
    }

    /**
     * 评阅留痕 CSV 导出（学期材料）：筛选条件与队列完全一致，导出完整筛选结果而非当前分页；
     * 权限与队列一致（TEACHER，service 内校验）；学生姓名脱敏、UTF-8 + BOM、中文表头。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> export(
            @Parameter(schema = @Schema(allowableValues = {"PENDING", "REVIEWED", "ALL"}))
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(required = false) String kpId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String filename = "review-log-" + LocalDate.now(ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
                + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename,
                StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers)
                .body(teacherReviewService.exportCsv(status, kpId, from, to));
    }

    @PostMapping("/{logPk}")
    public ApiResponse<Void> submit(@PathVariable long logPk,
                                    @Valid @RequestBody ReviewSubmitRequest request) {
        teacherReviewService.submit(logPk, request);
        return ApiResponse.ok();
    }
}
