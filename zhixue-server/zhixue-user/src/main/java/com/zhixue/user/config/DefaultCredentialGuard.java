package com.zhixue.user.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.user.entity.SysUser;
import com.zhixue.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * P0 修复：默认凭据守卫。
 * 启动期扫描 sys_user 表中已知的演示口令哈希（seed-demo-all.sql 的 BCrypt 固定值），
 * 生产环境（spring.profiles.active=prod）命中时直接 fail-fast 拒绝启动。
 * 开发环境仅打印 WARN 日志。
 */
@Component
public class DefaultCredentialGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DefaultCredentialGuard.class);

    /** seed-demo-all.sql 中 Demo@2026 的 BCrypt 哈希（公开已知值，非泄露密钥）。 */
    private static final String DEMO_BCRYPT_HASH = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

    private final SysUserMapper userMapper;
    private final boolean isProd;

    public DefaultCredentialGuard(SysUserMapper userMapper,
                                  @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.userMapper = userMapper;
        this.isProd = "prod".equals(activeProfile);
    }

    @Override
    public void afterPropertiesSet() {
        try {
            Long demoCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPassword, DEMO_BCRYPT_HASH));
            if (demoCount == null || demoCount == 0) {
                return;
            }
            if (isProd) {
                throw new IllegalStateException(
                        "[P0 安全] 检测到 " + demoCount + " 个默认演示账号（Demo@2026）仍在使用！"
                        + "生产环境必须在启动前重置密码或删除演示账号。"
                        + "参考 SQL: UPDATE sys_user SET password='<new-bcrypt-hash>' WHERE password='"
                        + DEMO_BCRYPT_HASH + "';");
            }
            log.warn("[安全提示] 检测到 {} 个默认演示账号（Demo@2026），开发环境放行但生产环境将拒绝启动", demoCount);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // 表不存在或查询失败时不阻断启动（首次部署时表可能尚未创建）
            log.debug("默认凭据检查跳过（表可能不存在）: {}", e.getMessage());
        }
    }
}
