package com.zhixue.user.config;

import cn.dev33.satoken.stp.StpInterface;
import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色提供者：让 StpUtil.checkRole("TEACHER") 在现有登录态下可用。
 * 角色只从 sys_user.role 读取，不把角色写死在 Session，避免权限变更后旧 token 不生效。
 */
@Component
public class SaTokenRoleProvider implements StpInterface {

    private final SysUserMapper sysUserMapper;

    public SaTokenRoleProvider(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            Long userId = Long.valueOf(String.valueOf(loginId));
            SysUser user = sysUserMapper.selectById(userId);
            // 停用账号即使旧 token 未过期也不应继续拥有角色
            if (user == null || user.getIsActive() == null || user.getIsActive() != 1
                    || user.getRole() == null || user.getRole().isBlank()) {
                return List.of();
            }
            return List.of(user.getRole());
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
