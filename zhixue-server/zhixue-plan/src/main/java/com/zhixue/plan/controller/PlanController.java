package com.zhixue.plan.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.plan.dto.CheckinRequest;
import com.zhixue.plan.dto.PlanGenerateRequest;
import com.zhixue.plan.dto.PlanView;
import com.zhixue.plan.service.PlanService;
import jakarta.validation.Valid;
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

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ApiResponse<List<PlanView>> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(planService.list(from, to));
    }

    @PostMapping("/generate")
    public ApiResponse<List<PlanView>> generate(@Valid @RequestBody PlanGenerateRequest request) {
        return ApiResponse.ok(planService.generate(request));
    }

    @PostMapping("/{planId}/checkin")
    public ApiResponse<Void> checkin(@PathVariable Long planId,
                                     @Valid @RequestBody CheckinRequest request) {
        planService.checkin(planId, request);
        return ApiResponse.ok(null);
    }
}
