package com.zhixue.diagnosis.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.ClassPortraitView;
import com.zhixue.diagnosis.dto.PortraitView;
import com.zhixue.diagnosis.service.PortraitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学情画像接口（契约 V2 第 7 节 portrait 组）。
 */
@RestController
@RequestMapping("/api/portraits")
public class PortraitController {

    private final PortraitService portraitService;

    public PortraitController(PortraitService portraitService) {
        this.portraitService = portraitService;
    }

    @GetMapping("/me")
    public ApiResponse<PortraitView> me() {
        return ApiResponse.ok(portraitService.me());
    }

    @GetMapping("/class/{courseId}")
    public ApiResponse<ClassPortraitView> classPortrait(@PathVariable Long courseId) {
        return ApiResponse.ok(portraitService.classPortrait(courseId));
    }
}
