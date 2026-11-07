package com.equipcare.common.security;

import com.equipcare.common.constant.RedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 基于 Redis 的令牌与会话存储。
 *
 * <p>承担五件事：
 * <ol>
 *   <li>在线会话：jti → LoginUser，避免每次请求查库；</li>
 *   <li>令牌黑名单：退出登录后立即失效；</li>
 *   <li>登录失败计数：连续失败锁定账号；</li>
 *   <li>Refresh Token 登记：按用户登记有效刷新令牌 jti，登出 / 改密 / 禁用时全量吊销；</li>
 *   <li>可用性探活：供刷新令牌等安全关键操作 fail-closed 判断。</li>
 * </ol>
 *
 * <p><b>Redis 故障降级策略（Phase 11）：</b>
 * <ul>
 *   <li>非关键读写（会话缓存、黑名单查询、失败计数）失败时降级并告警日志，
 *       认证链路退化为「JWT 签名验证 + 数据库会话回源」，不跳过认证、不绕过权限；</li>
 *   <li>安全关键操作（刷新令牌、登出吊销）通过 {@link #requireAvailable()} 显式探活，
 *       Redis 不可用时安全失败（503），绝不降级为无验证模式；</li>
 *   <li>降级期间黑名单暂不可查属于已知残余风险，恢复后立即生效。</li>
 * </ul>
 */
@Component
public class TokenStore {

    private static final Logger log = LoggerFactory.getLogger(TokenStore.class);

    /** 降级告警节流：同一分钟内只打印一条，避免 Redis 故障时日志风暴 */
    private static final long DEGRADE_LOG_INTERVAL_MS = 60_000;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private volatile long lastDegradeLogAt = 0;

    /**
     * 登录失败计数的进程内兜底（第九期安全整改）：Redis 不可用时改用内存计数，
     * 保证暴力破解防护不因缓存故障而完全失效。单实例部署语义完整；
     * 多实例时各实例独立计数（防护减弱但不失效）。
     */
    private final java.util.Map<String, long[]> memLoginFails = new java.util.concurrent.ConcurrentHashMap<>();

    public TokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================================================================
    // 可用性探活
    // ==================================================================

    /**
     * 探测 Redis 是否可用（PING 语义）。
     *
     * <p>刷新令牌、登出吊销等安全关键操作必须先调用本方法：
     * 返回 false 时应安全失败（抛 503），不得在无法校验黑名单 / 无法吊销的情况下继续。
     */
    public boolean requireAvailable() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return pong != null && !pong.isEmpty();
        } catch (Exception ex) {
            logDegrade("探活", ex);
            return false;
        }
    }

    // ==================================================================
    // 在线会话
    // ==================================================================

    public void saveLoginUser(String jti, LoginUser loginUser, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(loginUser);
            redisTemplate.opsForValue().set(RedisKeys.loginUser(jti), json, ttl);
            String userTokensKey = RedisKeys.userTokens(loginUser.getUserId());
            redisTemplate.opsForSet().add(userTokensKey, jti);
            redisTemplate.expire(userTokensKey, ttl);
        } catch (JsonProcessingException ex) {
            log.error("序列化登录用户失败 userId={}", loginUser.getUserId(), ex);
        } catch (org.springframework.dao.DataAccessException ex) {
            // 降级：会话缓存写失败不影响登录，过滤器会回源数据库重建会话
            logDegrade("写入在线会话", ex);
        }
    }

    /** 读取会话；Redis 故障时返回 null，由调用方回源数据库（不视为未登录） */
    public LoginUser getLoginUser(String jti) {
        try {
            String json = redisTemplate.opsForValue().get(RedisKeys.loginUser(jti));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, LoginUser.class);
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("读取在线会话", ex);
            return null;
        } catch (JsonProcessingException ex) {
            log.warn("反序列化登录用户失败 jti={}", jti, ex);
            return null;
        }
    }

    // ==================================================================
    // 令牌黑名单
    // ==================================================================

    public void blacklist(String jti, Duration ttl) {
        if (jti == null) {
            return;
        }
        Duration effective = ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(1) : ttl;
        try {
            redisTemplate.opsForValue().set(RedisKeys.tokenBlacklist(jti), "1", effective);
            redisTemplate.delete(RedisKeys.loginUser(jti));
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("写入令牌黑名单", ex);
        }
    }

    /**
     * 查询黑名单。Redis 故障时返回 false（降级放行）：
     * 认证仍需通过 JWT 签名验证与数据库会话回源，不会绕过身份认证与权限检查。
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.tokenBlacklist(jti)));
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("查询令牌黑名单", ex);
            return false;
        }
    }

    // ==================================================================
    // Refresh Token 生命周期（Phase 11 批三）
    // ==================================================================

    /** 登录 / 刷新成功后登记该用户的有效刷新令牌 jti，TTL 与刷新令牌一致 */
    public void saveRefreshToken(Long userId, String jti, Duration ttl) {
        try {
            String key = RedisKeys.userRefreshTokens(userId);
            redisTemplate.opsForSet().add(key, jti);
            redisTemplate.expire(key, ttl);
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("登记刷新令牌", ex);
        }
    }

    /**
     * 吊销该用户全部刷新令牌：逐个加入黑名单并清空登记集合。
     * 登出 / 改密 / 禁用用户时调用；Redis 不可用时抛出连接异常，由调用方安全失败。
     */
    public void revokeAllRefreshTokens(Long userId) {
        String key = RedisKeys.userRefreshTokens(userId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members != null) {
            for (String jti : members) {
                redisTemplate.opsForValue()
                        .set(RedisKeys.tokenBlacklist(jti), "1", Duration.ofDays(8));
                redisTemplate.delete(RedisKeys.loginUser(jti));
            }
        }
        redisTemplate.delete(key);
    }

    /**
     * 校验刷新令牌是否仍登记在用户名下（登出 / 改密后集合被清空，旧令牌即被拒绝）。
     * 集合整体缺失（Redis 重启或过期）时按未登记处理，要求重新登录。
     */
    public boolean isRefreshTokenRegistered(Long userId, String jti) {
        try {
            String key = RedisKeys.userRefreshTokens(userId);
            Boolean exists = redisTemplate.hasKey(key);
            if (!Boolean.TRUE.equals(exists)) {
                return false;
            }
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, jti));
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("校验刷新令牌登记", ex);
            return false;
        }
    }

    /** 用户被禁用或重置密码时，强制下线其全部会话并吊销全部刷新令牌 */
    public void removeAllByUser(Long userId) {
        revokeAllRefreshTokens(userId);
        String key = RedisKeys.userTokens(userId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members != null) {
            for (String jti : members) {
                redisTemplate.delete(RedisKeys.loginUser(jti));
            }
        }
        redisTemplate.delete(key);
    }

    // ==================================================================
    // 登录失败计数（Redis 故障时降级为不计数，登录流程继续）
    // ==================================================================

    public long increaseLoginFail(String username, Duration ttl) {
        String key = RedisKeys.loginFail(username);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count == null ? 0 : count;
    }

    public long getLoginFail(String username) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String value = ops.get(RedisKeys.loginFail(username));
        return value == null ? 0 : Long.parseLong(value);
    }

    public void clearLoginFail(String username) {
        redisTemplate.delete(RedisKeys.loginFail(username));
    }

    /**
     * 登录失败计数系列操作的降级包装：Redis 故障时按「无失败记录」处理并告警。
     * 锁定策略降级为进程内内存计数兜底（多实例时各实例独立计数），暴力破解防护不因缓存故障失效。
     */
    public long getLoginFailSafe(String username) {
        try {
            return getLoginFail(username);
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("读取登录失败计数", ex);
            return memGetFail(username);
        }
    }

    /** 内存兜底：读取失败计数（窗口过期自动清零） */
    private long memGetFail(String username) {
        long[] slot = memLoginFails.get(username);
        if (slot == null) {
            return 0;
        }
        // slot = [count, windowStartMillis, windowMillis]
        if (System.currentTimeMillis() - slot[1] > slot[2]) {
            memLoginFails.remove(username);
            return 0;
        }
        return slot[0];
    }

    /** 内存兜底：累加失败计数（窗口基于首次失败时间，与 Redis 版 TTL 语义一致） */
    private long memIncrFail(String username, Duration ttl) {
        long now = System.currentTimeMillis();
        long[] slot = memLoginFails.compute(username, (k, v) -> {
            if (v == null || now - v[1] > v[2]) {
                return new long[]{1, now, ttl.toMillis()};
            }
            v[0] = v[0] + 1;
            return v;
        });
        return slot[0];
    }

    public long increaseLoginFailSafe(String username, Duration ttl) {
        try {
            return increaseLoginFail(username, ttl);
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("累加登录失败计数", ex);
            return memIncrFail(username, ttl);
        }
    }

    public void clearLoginFailSafe(String username) {
        memLoginFails.remove(username);
        try {
            clearLoginFail(username);
        } catch (org.springframework.dao.DataAccessException ex) {
            logDegrade("清除登录失败计数", ex);
        }
    }

    /** 降级告警：节流到每分钟一条，只输出操作名与异常类型，不输出令牌 / 密码 */
    private void logDegrade(String operation, Exception ex) {
        long now = System.currentTimeMillis();
        if (now - lastDegradeLogAt >= DEGRADE_LOG_INTERVAL_MS) {
            lastDegradeLogAt = now;
            log.warn("Redis 不可用，功能降级运行 operation={} reason={}", operation, ex.getClass().getSimpleName());
        }
    }
}
