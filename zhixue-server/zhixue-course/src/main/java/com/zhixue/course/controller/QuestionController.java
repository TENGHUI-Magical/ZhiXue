package com.zhixue.course.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.course.dto.QuestionAnswerView;
import com.zhixue.course.dto.QuestionDetailView;
import com.zhixue.course.service.QuestionBankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目详情接口（契约 V2.1.0）。
 * 详情不含答案；答案由学生显式点击后调用 /answer，避免推荐列表提前泄题。
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionBankService questionBankService;

    public QuestionController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
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
