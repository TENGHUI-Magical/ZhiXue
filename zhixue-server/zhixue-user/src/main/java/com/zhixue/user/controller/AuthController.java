package com.zhixue.user.controller;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口桩（接口契约文档 2.1 节）。
 * TODO(P0 任务 43)：按 sys_user 表校验密码 → StpUtil.login(userId) 签发 token → 返回契约结构。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 契约 2.1 请求体 */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 契约 2.1 响应 data：token + 用户子结构（字段与契约一一对应，冻结后只增不改） */
    public record LoginResponse(String token, LoginUser user) {
    }

    public record LoginUser(Long userId, String realName, String role, String className) {
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        // 开发期占位：保证契约字段结构可联调，下一阶段接入真实校验
        // TODO(P0 任务 43)：按 sys_user 表校验密码 → StpUtil.login(userId) 签发 token → 返回契约结构。
        //      错误映射：密码错误 40100、用户不存在 40400（契约 2.1 说明）
        throw new BizException(ErrorCode.BIZ_ERROR, "登录功能待实现（契约已冻结，见接口契约文档 2.1 节）");
    }
}