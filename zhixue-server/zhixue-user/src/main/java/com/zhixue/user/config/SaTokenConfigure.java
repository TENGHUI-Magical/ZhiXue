package com.zhixue.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器：/api/** 默认要求登录，登录与健康检查除外。
 * 登录态校验之外再查账号状态：停用账号即使 token 未过期也立即失效。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    private final AccountStatusGuard accountStatusGuard;

    public SaTokenConfigure(AccountStatusGuard accountStatusGuard) {
        this.accountStatusGuard = accountStatusGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                    long userId = StpUtil.getLoginIdAsLong();
                    if (!accountStatusGuard.isActive(userId)) {
                        // 先清掉失效会话，再返回 40100，前端按登录失效处理
                        StpUtil.logout();
                        throw new BizException(ErrorCode.UNAUTHORIZED, "账号已停用或不存在，请重新登录");
                    }
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/system/health");
    }
}
