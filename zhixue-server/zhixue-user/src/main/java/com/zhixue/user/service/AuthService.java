package com.zhixue.user.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 认证服务：BCrypt 校验 + Sa-Token 签发/读取登录态。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * 用户不存在时也执行一次 BCrypt 校验，避免通过响应耗时枚举账号。
     * 该哈希对应一个已废弃的本地占位口令，仅用于恒定时间比较。
     */
    private static final String DUMMY_HASH = "$2a$10$7gTMsmEghWWChdEa83pnFu2CcL7s08WGi12XK4xwvN2N8fSFssvIe";

    private final SysUserMapper sysUserMapper;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(SysUserMapper sysUserMapper, LoginAttemptLimiter loginAttemptLimiter) {
        this.sysUserMapper = sysUserMapper;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    /**
     * 登录：用户不存在与口令错误统一返回 40100「用户名或密码错误」，避免账号枚举。
     * 失败按账号 + IP 双维度滑动窗口计数，达到阈值返回 42900 并带剩余锁定秒数。
     * 登录成功后轮换 token：先作废该账号旧会话，再签发新 token。
     *
     * @param username 登录名
     * @param password 明文口令（仅用于本次校验，不落库）
     * @param clientIp 客户端 IP，用于 IP 维度限流
     * @return token + 用户
     */
    public AuthResult login(String username, String password, String clientIp) {
        LoginAttemptLimiter.Decision preCheck = loginAttemptLimiter.check(username, clientIp);
        if (!preCheck.allowed()) {
            throw new BizException(ErrorCode.RATE_LIMITED,
                    "登录尝试过于频繁，请 " + preCheck.retryAfterSeconds() + " 秒后重试");
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsActive, 1)
                .last("LIMIT 1"));
        if (user == null) {
            BCrypt.verifyer().verify(password.toCharArray(), DUMMY_HASH.toCharArray());
            // 不把用户名写进日志，避免日志侧 PII 与账号枚举线索
            log.info("登录失败：用户不存在");
            throw loginFailure(username, clientIp);
        }
        boolean verified = BCrypt.verifyer()
                .verify(password.toCharArray(), user.getPassword().toCharArray())
                .verified;
        if (!verified) {
            log.info("登录失败：口令错误");
            throw loginFailure(username, clientIp);
        }
        loginAttemptLimiter.clearAccount(username);
        rotateToken(user.getUserId());
        return new AuthResult(StpUtil.getTokenValue(), user);
    }

    /** 记录失败并在达到阈值时返回 42900；未达阈值仍返回 40100，保持账号枚举不可区分。 */
    private BizException loginFailure(String username, String clientIp) {
        LoginAttemptLimiter.Decision decision = loginAttemptLimiter.recordFailure(username, clientIp);
        if (!decision.allowed()) {
            return new BizException(ErrorCode.RATE_LIMITED,
                    "登录失败次数过多，请 " + decision.retryAfterSeconds() + " 秒后重试");
        }
        return new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }

    /** 登录轮换：使该账号此前签发的 token 立即失效，避免旧 token 继续可用。 */
    private void rotateToken(Long userId) {
        StpUtil.logout(userId);
        StpUtil.login(userId);
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
