package com.zhixue.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 登录失败限流：账号 + 客户端 IP 双维度滑动窗口（Redis ZSET，15 分钟窗口）。
 * <p>
 * 阈值：单账号 5 次失败锁定，单 IP 20 次失败锁定（防同一出口 IP 换账号爆破）。
 * 账号锁按时长递增：60s 起，每多失败 1 次翻倍，封顶 15 分钟；IP 锁固定 15 分钟。
 * 登录成功清零账号失败计数与账号锁；IP 聚合计数不清零（避免用合法账号重置爆破计数）。
 * P0 修复：Redis 不可用时降级为本地进程内限流（fail-closed），不再静默放行。
 */
@Service
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    /** 滑动窗口长度：15 分钟 */
    static final long WINDOW_SECONDS = 900L;

    /** 单账号窗口内失败阈值 */
    static final int ACCOUNT_MAX_FAILURES = 5;

    /** 单 IP 窗口内失败阈值 */
    static final int IP_MAX_FAILURES = 20;

    /** 账号锁基础时长 60s，每多 1 次失败翻倍 */
    static final long LOCK_BASE_SECONDS = 60L;

    /** 单次锁定时长上限 */
    static final long LOCK_MAX_SECONDS = 900L;

    private static final String ACCOUNT_FAIL_PREFIX = "zhixue:auth:fail:account:";
    private static final String ACCOUNT_LOCK_PREFIX = "zhixue:auth:lock:account:";
    private static final String IP_FAIL_PREFIX = "zhixue:auth:fail:ip:";
    private static final String IP_LOCK_PREFIX = "zhixue:auth:lock:ip:";

    /** P0 修复：本地降级计数器（Redis 不可用时使用，fail-closed） */
    private final java.util.concurrent.ConcurrentHashMap<String, LocalCounter> localCounters =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;

    public LoginAttemptLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 登录前检查：账号锁或 IP 锁任一命中即拒绝。
     * P0 修复：Redis 不可用时降级为本地限流检查（fail-closed）。
     *
     * @param username  登录名
     * @param clientIp  客户端 IP
     * @return 拒绝时 retryAfterSeconds 为剩余锁定秒数
     */
    public Decision check(String username, String clientIp) {
        try {
            long accountLock = ttlSeconds(ACCOUNT_LOCK_PREFIX + username);
            long ipLock = ttlSeconds(IP_LOCK_PREFIX + clientIp);
            long retryAfter = Math.max(accountLock, ipLock);
            return retryAfter > 0 ? Decision.blocked(retryAfter) : Decision.allow();
        } catch (Exception e) {
            log.warn("登录前检查 Redis 不可用，降级为本地限流 username={} ip={} reason={}",
                    username, clientIp, e.toString());
            return localCheck(username, clientIp);
        }
    }

    /**
     * 记录一次失败；命中阈值时写入递增锁并直接返回拒绝。
     * P0 修复：Redis 不可用时降级为本地计数（fail-closed）。
     *
     * @param username 登录名
     * @param clientIp 客户端 IP
     * @return 拒绝时即可作为第 N 次失败的限流结果
     */
    public Decision recordFailure(String username, String clientIp) {
        try {
            long now = System.currentTimeMillis() / 1000L;
            long accountFailures = countInWindow(ACCOUNT_FAIL_PREFIX + username, now);
            long ipFailures = countInWindow(IP_FAIL_PREFIX + clientIp, now);
            if (accountFailures >= ACCOUNT_MAX_FAILURES) {
                long lockSeconds = lockSecondsFor(accountFailures);
                lock(ACCOUNT_LOCK_PREFIX + username, lockSeconds);
                return Decision.blocked(lockSeconds);
            }
            if (ipFailures >= IP_MAX_FAILURES) {
                lock(IP_LOCK_PREFIX + clientIp, LOCK_MAX_SECONDS);
                return Decision.blocked(LOCK_MAX_SECONDS);
            }
            return Decision.allow();
        } catch (Exception e) {
            log.warn("登录限流 Redis 不可用，降级为本地计数 username={} ip={} reason={}",
                    username, clientIp, e.toString());
            return localRecordFailure(username, clientIp);
        }
    }

    /**
     * 登录成功：清零该账号的失败计数与锁定，不影响同 IP 的聚合计数。
     *
     * @param username 登录名
     */
    public void clearAccount(String username) {
        try {
            redisTemplate.delete(ACCOUNT_FAIL_PREFIX + username);
            redisTemplate.delete(ACCOUNT_LOCK_PREFIX + username);
        } catch (Exception e) {
            log.warn("登录成功清零失败计数时 Redis 不可用 username={} reason={}", username, e.toString());
        }
    }

    /** 滑动窗口计数：先写入本次失败，再剔除窗口外的旧记录。 */
    private long countInWindow(String key, long now) {
        String member = now + ":" + ThreadLocalRandom.current().nextLong();
        redisTemplate.opsForZSet().add(key, member, now);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - WINDOW_SECONDS);
        Long size = redisTemplate.opsForZSet().zCard(key);
        redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS * 2));
        return size == null ? 0L : size;
    }

    /** 递增锁定：60s、120s、240s… 封顶 15 分钟；溢出保护。 */
    private long lockSecondsFor(long failures) {
        long extra = Math.min(failures - ACCOUNT_MAX_FAILURES, 4L);
        return Math.min(LOCK_MAX_SECONDS, LOCK_BASE_SECONDS * (1L << extra));
    }

    private void lock(String key, long seconds) {
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(seconds));
    }

    private long ttlSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0 ? 0L : ttl;
    }

    /**
     * 限流判定结果。
     *
     * @param allowed           是否放行
     * @param retryAfterSeconds 拒绝时剩余锁定秒数；放行时为 0
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision allow() {
            return new Decision(true, 0L);
        }

        static Decision blocked(long retryAfterSeconds) {
            return new Decision(false, Math.max(1L, retryAfterSeconds));
        }
    }

    // ===== P0 修复：本地降级限流（Redis 不可用时 fail-closed） =====

    /** 本地计数器：窗口内失败次数 + 锁定截止时间。 */
    private static final class LocalCounter {
        volatile long failures;
        volatile long lockUntilEpochSeconds;
    }

    /** 本地降级检查：账号锁或 IP 锁任一命中即拒绝。 */
    private Decision localCheck(String username, String clientIp) {
        long now = System.currentTimeMillis() / 1000L;
        LocalCounter account = localCounters.get("account:" + username);
        LocalCounter ip = localCounters.get("ip:" + clientIp);
        long retryAfter = 0;
        if (account != null && account.lockUntilEpochSeconds > now) {
            retryAfter = Math.max(retryAfter, account.lockUntilEpochSeconds - now);
        }
        if (ip != null && ip.lockUntilEpochSeconds > now) {
            retryAfter = Math.max(retryAfter, ip.lockUntilEpochSeconds - now);
        }
        return retryAfter > 0 ? Decision.blocked(retryAfter) : Decision.allow();
    }

    /** 本地降级记录失败：命中阈值时设置锁定。 */
    private Decision localRecordFailure(String username, String clientIp) {
        long now = System.currentTimeMillis() / 1000L;
        LocalCounter account = localCounters.computeIfAbsent("account:" + username, k -> new LocalCounter());
        LocalCounter ip = localCounters.computeIfAbsent("ip:" + clientIp, k -> new LocalCounter());
        synchronized (account) {
            account.failures++;
            if (account.failures >= ACCOUNT_MAX_FAILURES) {
                long lockSeconds = lockSecondsFor(account.failures);
                account.lockUntilEpochSeconds = now + lockSeconds;
                return Decision.blocked(lockSeconds);
            }
        }
        synchronized (ip) {
            ip.failures++;
            if (ip.failures >= IP_MAX_FAILURES) {
                ip.lockUntilEpochSeconds = now + LOCK_MAX_SECONDS;
                return Decision.blocked(LOCK_MAX_SECONDS);
            }
        }
        return Decision.allow();
    }
}
