package com.zhixue.plan.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.DueReviewItem;
import com.zhixue.diagnosis.service.DueReviewService;
import com.zhixue.plan.dto.CheckinRequest;
import com.zhixue.plan.dto.CheckinResult;
import com.zhixue.plan.dto.PlanGenerateRequest;
import com.zhixue.plan.dto.PlanView;
import com.zhixue.plan.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习计划接口（契约 V2 第 7 节 plan 组）。
 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;
    private final DueReviewService dueReviewService;

    public PlanController(PlanService planService, DueReviewService dueReviewService) {
        this.planService = planService;
        this.dueReviewService = dueReviewService;
    }

    @GetMapping
    public ApiResponse<List<PlanView>> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(planService.list(from, to));
    }

    /** 到期复习队列（05 §3.8）：今日该复习的知识点 + 建议重做的题 + 可解释理由。 */
    @GetMapping("/due-reviews")
    public ApiResponse<List<DueReviewItem>> dueReviews(@RequestParam Long courseId) {
        return ApiResponse.ok(dueReviewService.dueReviews(courseId));
    }

    @PostMapping("/generate")
    public ApiResponse<List<PlanView>> generate(@Valid @RequestBody PlanGenerateRequest request) {
        try {
            return ApiResponse.ok(planService.generate(request));
        } catch (DuplicateKeyException e) {
            // 并发双击：第一个事务已提交，重试会复用已存在日期，不再冒 50000
            return ApiResponse.ok(planService.generate(request));
        }
    }

    @PostMapping("/{planId}/checkin")
    public ApiResponse<CheckinResult> checkin(@PathVariable Long planId,
                                              @Valid @RequestBody CheckinRequest request) {
        return ApiResponse.ok(planService.checkin(planId, request));
    }
}
