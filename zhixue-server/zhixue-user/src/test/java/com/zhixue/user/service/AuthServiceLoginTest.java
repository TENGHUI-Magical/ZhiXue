package com.zhixue.user.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录链路：锁定前置拦截（42900）、失败计数与 40100 不可区分、成功清零并轮换 token。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceLoginTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    private static final String GOOD_PASSWORD = "Good#Pass2026";

    @Test
    void blockedAccountIsRejectedBeforeAuthentication() {
        when(loginAttemptLimiter.check("alice", "10.0.0.1"))
                .thenReturn(new LoginAttemptLimiter.Decision(false, 30));
        AuthService service = new AuthService(sysUserMapper, loginAttemptLimiter);

        assertThatThrownBy(() -> service.login("alice", GOOD_PASSWORD, "10.0.0.1"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(ex.getMessage()).contains("30");
                });
        verifyNoInteractions(sysUserMapper);
    }

    @Test
    void wrongPasswordReturnsUnauthorizedAndRecordsFailure() {
        when(loginAttemptLimiter.check(anyString(), anyString()))
                .thenReturn(LoginAttemptLimiter.Decision.allow());
        when(loginAttemptLimiter.recordFailure(anyString(), anyString()))
                .thenReturn(LoginAttemptLimiter.Decision.allow());
        when(sysUserMapper.selectOne(any())).thenReturn(userWithPasswordHash("other"));
        AuthService service = new AuthService(sysUserMapper, loginAttemptLimiter);

        assertThatThrownBy(() -> service.login("alice", GOOD_PASSWORD, "10.0.0.1"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(loginAttemptLimiter).recordFailure("alice", "10.0.0.1");
        verify(loginAttemptLimiter, never()).clearAccount(anyString());
    }

    @Test
    void failureAtThresholdSurfacesRateLimited() {
        when(loginAttemptLimiter.check(anyString(), anyString()))
                .thenReturn(LoginAttemptLimiter.Decision.allow());
        when(loginAttemptLimiter.recordFailure(anyString(), anyString()))
                .thenReturn(new LoginAttemptLimiter.Decision(false, 60));
        when(sysUserMapper.selectOne(any())).thenReturn(userWithPasswordHash("other"));
        AuthService service = new AuthService(sysUserMapper, loginAttemptLimiter);

        assertThatThrownBy(() -> service.login("alice", GOOD_PASSWORD, "10.0.0.1"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void successfulLoginClearsFailuresAndRotatesToken() {
        SysUser user = userWithPasswordHash(GOOD_PASSWORD);
        when(loginAttemptLimiter.check(anyString(), anyString()))
                .thenReturn(LoginAttemptLimiter.Decision.allow());
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        AuthService service = new AuthService(sysUserMapper, loginAttemptLimiter);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("new-token");

            AuthService.AuthResult result = service.login("alice", GOOD_PASSWORD, "10.0.0.1");

            assertThat(result.token()).isEqualTo("new-token");
            verify(loginAttemptLimiter).clearAccount("alice");
            // 轮换：先登出旧会话，再签发新 token
            stp.verify(() -> StpUtil.logout(1L));
            stp.verify(() -> StpUtil.login(1L));
        }
    }

    private SysUser userWithPasswordHash(String plainPassword) {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUsername("alice");
        user.setIsActive(1);
        user.setRole("STUDENT");
        user.setPassword(BCrypt.withDefaults().hashToString(10, plainPassword.toCharArray()));
        return user;
    }
}
