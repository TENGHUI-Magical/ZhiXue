package com.zhixue.user.config;

import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 账号状态守卫：Sa-Token 会话只证明"曾经登录成功"，不证明账号仍启用。
 * 每次鉴权时回查 sys_user.is_active，停用账号的旧 token 立即失效。
 * 查询失败时按禁用处理（失败关闭），避免数据库异常期间旧 token 继续放行。
 */
@Component
public class AccountStatusGuard {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusGuard.class);

    private final SysUserMapper sysUserMapper;

    public AccountStatusGuard(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 判断登录账号是否仍处于启用状态。
     *
     * @param userId 登录用户 ID
     * @return true 仅当用户存在且 is_active=1
     */
    public boolean isActive(long userId) {
        try {
            SysUser user = sysUserMapper.selectById(userId);
            return user != null && user.getIsActive() != null && user.getIsActive() == 1;
        } catch (Exception e) {
            log.warn("读取账号状态失败，按停用处理 userId={} reason={}", userId, e.toString());
            return false;
        }
    }
}
