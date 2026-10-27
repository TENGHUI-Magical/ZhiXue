package com.zhixue.course.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.course.dto.KnowledgePointView;
import com.zhixue.course.service.KnowledgePointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程模块接口（接口契约文档 3 节附录示例）。
 */
@RestController
@RequestMapping("/api/course")
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    public KnowledgePointController(KnowledgePointService knowledgePointService) {
        this.knowledgePointService = knowledgePointService;
    }

    /**
     * 按编号查询知识点（返回契约视图，不直出实体）。
     * 其余模块（user 已建登录桩，diagnosis/plan/notification 待扩展）沿用
     * 本模块 实体→Mapper→Service→Controller 四层模式。
     */
    @GetMapping("/kp/{kpId}")
    public ApiResponse<KnowledgePointView> getKp(@PathVariable String kpId) {
        return ApiResponse.ok(knowledgePointService.getViewById(kpId));
    }
}