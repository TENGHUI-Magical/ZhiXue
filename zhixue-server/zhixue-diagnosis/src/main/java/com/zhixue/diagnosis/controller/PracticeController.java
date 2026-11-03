package com.zhixue.diagnosis.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.PracticeCreateRequest;
import com.zhixue.diagnosis.dto.PracticeResultView;
import com.zhixue.diagnosis.dto.PracticeStartRequest;
import com.zhixue.diagnosis.dto.PracticeStartView;
import com.zhixue.diagnosis.service.PracticeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 练习作答接口（契约 V2 第 7 节 practice 组）。
 */
@RestController
@RequestMapping("/api/practices")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /**
     * 开始作答打点（进步证据的服务端计时入口）：首次打开题目时调用，
     * 返回的 clientToken 在提交作答时作为 startToken 携带。
     */
    @PostMapping("/start")
    public ApiResponse<PracticeStartView> begin(@Valid @RequestBody PracticeStartRequest request) {
        return ApiResponse.ok(practiceService.begin(request));
    }

    @PostMapping
    public ApiResponse<PracticeResultView> submit(@Valid @RequestBody PracticeCreateRequest request) {
        return ApiResponse.ok(practiceService.submit(request));
    }
}
