package com.zhixue.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ApiResponse;
import com.zhixue.user.entity.SysUser;
import com.zhixue.user.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（契约 2.1 节：登录 / 登出 / 当前用户）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 契约 2.1 请求体 */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 契约 2.1 响应 data：token + 用户子结构 */
    public record LoginResponse(String token, LoginUser user) {
    }

    public record LoginUser(Long userId, String realName, String role, String className) {
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthService.AuthResult result = authService.login(req.username(), req.password());
        return ApiResponse.ok(new LoginResponse(result.token(), toUser(result.user())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.logout();
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<LoginUser> me() {
        return ApiResponse.ok(toUser(authService.me()));
    }

    private LoginUser toUser(SysUser user) {
        return new LoginUser(user.getUserId(), user.getRealName(), user.getRole(), user.getClassName());
    }
}
