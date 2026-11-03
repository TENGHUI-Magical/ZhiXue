package com.zhixue.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ApiResponse;
import com.zhixue.user.entity.SysUser;
import com.zhixue.user.service.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /** 契约 2.1 请求体：username 对齐 DB VARCHAR(50)，password 对齐契约 6—64 */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(max = 50, message = "用户名最长 50 字") String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需在 6—64 字") String password) {
    }

    /** 契约 openapi LoginResult：token + userId + username + role 平铺 */
    public record LoginResponse(String token, Long userId, String username,
                                @Schema(allowableValues = {"STUDENT", "TEACHER"}) String role) {
    }

    /** 契约 openapi UserInfo（/api/auth/me） */
    public record LoginUser(Long userId, String username, String realName,
                            @Schema(allowableValues = {"STUDENT", "TEACHER"}) String role) {
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest request) {
        // 只取容器层 remoteAddr；不信任客户端可伪造的 X-Forwarded-For
        AuthService.AuthResult result = authService.login(req.username(), req.password(),
                request.getRemoteAddr());
        SysUser user = result.user();
        return ApiResponse.ok(new LoginResponse(result.token(), user.getUserId(),
                user.getUsername(), user.getRole()));
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
        return new LoginUser(user.getUserId(), user.getUsername(),
                user.getRealName(), user.getRole());
    }
}
