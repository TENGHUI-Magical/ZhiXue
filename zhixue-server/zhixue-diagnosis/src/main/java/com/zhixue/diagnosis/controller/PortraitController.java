package com.zhixue.diagnosis.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ApiResponse;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.diagnosis.dto.ClassPortraitView;
import com.zhixue.diagnosis.dto.PortraitView;
import com.zhixue.diagnosis.service.ExplainRateLimiter;
import com.zhixue.diagnosis.service.PortraitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学情画像接口（契约 V2 第 7 节 portrait 组）。
 * P1-3：接入按用户配额；超限仍走 HTTP 200 + code=42900（不改变业务错误契约）。
 */
@RestController
@RequestMapping("/api/portraits")
public class PortraitController {

    /** me 是单学生 3 条聚合 SQL；class 是 4+ 条重聚合（含窗口函数），配额更严。 */
    private static final int ME_LIMIT_PER_MINUTE = 30;
    private static final int CLASS_LIMIT_PER_MINUTE = 10;

    private final PortraitService portraitService;
    private final ExplainRateLimiter rateLimiter;

    public PortraitController(PortraitService portraitService,
                              ExplainRateLimiter rateLimiter) {
        this.portraitService = portraitService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/me")
    public ApiResponse<PortraitView> me() {
        ExplainRateLimiter.Decision decision = rateLimiter.check(
                "portrait-me", StpUtil.getLoginIdAsLong(), ME_LIMIT_PER_MINUTE);
        if (!decision.allowed()) {
            return ApiResponse.fail(ErrorCode.RATE_LIMITED,
                    "画像查询过于频繁，请 " + decision.retryAfterSeconds() + " 秒后重试");
        }
        return ApiResponse.ok(portraitService.me());
    }

    @GetMapping("/class/{courseId}")
    public ApiResponse<ClassPortraitView> classPortrait(@PathVariable Long courseId) {
        ExplainRateLimiter.Decision decision = rateLimiter.check(
                "portrait-class", StpUtil.getLoginIdAsLong(), CLASS_LIMIT_PER_MINUTE);
        if (!decision.allowed()) {
            return ApiResponse.fail(ErrorCode.RATE_LIMITED,
                    "班级画像查询过于频繁，请 " + decision.retryAfterSeconds() + " 秒后重试");
        }
        return ApiResponse.ok(portraitService.classPortrait(courseId));
    }
}
