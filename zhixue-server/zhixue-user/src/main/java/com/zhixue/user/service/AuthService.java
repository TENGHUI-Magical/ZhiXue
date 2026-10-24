package com.zhixue.user.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

/**
 * 认证服务：BCrypt 校验 + Sa-Token 签发/读取登录态。
 */
@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;

    public AuthService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 登录：用户不存在 40400，密码错误 40100。
     *
     * @param username 登录名
     * @param password 明文口令（仅用于本次校验，不落库）
     * @return token + 用户
     */
    public AuthResult login(String username, String password) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsActive, 1)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        boolean verified = BCrypt.verifyer()
                .verify(password.toCharArray(), user.getPassword().toCharArray())
                .verified;
        if (!verified) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        StpUtil.login(user.getUserId());
        return new AuthResult(StpUtil.getTokenValue(), user);
    }

    /**
     * 当前登录用户。
     *
     * @return 用户
     */
    public SysUser me() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }
        return user;
    }

    /**
     * 登录结果。
     *
     * @param token Sa-Token 令牌
     * @param user  用户
     */
    public record AuthResult(String token, SysUser user) {
    }
}
