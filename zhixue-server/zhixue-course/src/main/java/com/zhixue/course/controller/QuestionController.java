package com.zhixue.course.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.course.dto.QuestionAnswerView;
import com.zhixue.course.dto.QuestionBriefView;
import com.zhixue.course.dto.QuestionDetailView;
import com.zhixue.course.service.QuestionBankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目查询接口（契约 V2.2.0）。
 * 详情与列表均不含答案；答案由学生显式点击后调用 /answer，避免列表提前泄题。
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionBankService questionBankService;

    public QuestionController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    /** 按知识点列出题目；limit 默认 20，服务端收敛到 1—50。 */
    @GetMapping
    public ApiResponse<List<QuestionBriefView>> list(
            @RequestParam String kpId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionBankService.listBriefByKp(kpId, limit));
    }

    @GetMapping("/{qId}")
    public ApiResponse<QuestionDetailView> detail(@PathVariable String qId) {
        return ApiResponse.ok(questionBankService.getDetail(qId));
    }

    @GetMapping("/{qId}/answer")
    public ApiResponse<QuestionAnswerView> answer(@PathVariable String qId) {
        return ApiResponse.ok(questionBankService.getAnswer(qId));
    }
}
