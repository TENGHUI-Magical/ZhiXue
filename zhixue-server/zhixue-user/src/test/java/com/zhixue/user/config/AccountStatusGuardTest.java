package com.zhixue.user.config;

import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 账号状态守卫：停用 / 不存在 / 查询异常一律视为失效（失败关闭）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountStatusGuardTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Test
    void activeUserPasses() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(1));
        assertThat(new AccountStatusGuard(sysUserMapper).isActive(1L)).isTrue();
    }

    @Test
    void disabledUserFails() {
        when(sysUserMapper.selectById(2L)).thenReturn(user(0));
        assertThat(new AccountStatusGuard(sysUserMapper).isActive(2L)).isFalse();
    }

    @Test
    void missingUserFails() {
        when(sysUserMapper.selectById(3L)).thenReturn(null);
        assertThat(new AccountStatusGuard(sysUserMapper).isActive(3L)).isFalse();
    }

    @Test
    void repositoryFailureFailsClosed() {
        when(sysUserMapper.selectById(4L)).thenThrow(new RuntimeException("db down"));
        assertThat(new AccountStatusGuard(sysUserMapper).isActive(4L)).isFalse();
    }

    private SysUser user(int isActive) {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setIsActive(isActive);
        return user;
    }
}
