package com.zhixue.course.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ApiResponse;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionAnswerView;
import com.zhixue.course.dto.QuestionBriefView;
import com.zhixue.course.dto.QuestionDetailView;
import com.zhixue.course.mapper.PracticeAccessMapper;
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
 * P0 修复：/answer 增加角色 + 作答回执双条件校验，防止学生作答前批量拉取答案。
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionBankService questionBankService;
    private final PracticeAccessMapper practiceAccessMapper;

    public QuestionController(QuestionBankService questionBankService,
                              PracticeAccessMapper practiceAccessMapper) {
        this.questionBankService = questionBankService;
        this.practiceAccessMapper = practiceAccessMapper;
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

    /**
     * 查看答案与解析（P0 修复：双条件放行）。
     * 教师角色（TEACHER）直接放行；学生必须已有该题的非空作答记录（作答回执）。
     * 防止学生在作答前批量拉取标准答案污染诊断数据源。
     */
    @GetMapping("/{qId}/answer")
    public ApiResponse<QuestionAnswerView> answer(@PathVariable String qId) {
        long userId = StpUtil.getLoginIdAsLong();
        // 教师角色直接放行
        if (StpUtil.hasRole("TEACHER")) {
            return ApiResponse.ok(questionBankService.getAnswer(qId));
        }
        // 学生必须有非空作答回执
        int submitted = practiceAccessMapper.countSubmitted(userId, qId);
        if (submitted <= 0) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "请先完成作答后再查看答案（作答回执校验）");
        }
        return ApiResponse.ok(questionBankService.getAnswer(qId));
    }
}
