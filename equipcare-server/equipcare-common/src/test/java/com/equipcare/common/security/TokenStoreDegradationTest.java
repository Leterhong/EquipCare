package com.equipcare.common.security;

import com.equipcare.common.constant.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 11 批二 · Redis 故障降级行为单元测试。
 *
 * <p>验证策略：
 * <ul>
 *   <li>非关键读写（会话缓存 / 黑名单查询 / 失败计数）异常时降级，不向上抛出；</li>
 *   <li>安全关键操作可通过 {@link TokenStore#requireAvailable()} 感知故障（fail-closed）；</li>
 *   <li>Redis 恢复后行为恢复正常。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenStoreDegradationTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    private TokenStore tokenStore;

    private final DataAccessResourceFailureException down =
            new DataAccessResourceFailureException("connection refused");

    @BeforeEach
    void setUp() {
        tokenStore = new TokenStore(redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    private void makeRedisDown() {
        when(redisTemplate.hasKey(anyString())).thenThrow(down);
        when(valueOps.get(anyString())).thenThrow(down);
        lenient().doThrow(down).when(valueOps).set(anyString(), anyString());
        lenient().doThrow(down).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOps.increment(anyString())).thenThrow(down);
        lenient().when(redisTemplate.delete(anyString())).thenThrow(down);
        lenient().when(redisTemplate.getConnectionFactory()).thenThrow(down);
    }

    private void makeRedisUp() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOps.get(anyString())).thenReturn(null);
        lenient().when(redisTemplate.getConnectionFactory()).thenReturn(null);
    }

    @Test
    @DisplayName("Redis 宕机：黑名单查询降级为 false，不抛异常")
    void blacklistDegradesToFalse() {
        makeRedisDown();
        assertThat(tokenStore.isBlacklisted("jti123")).isFalse();
    }

    @Test
    @DisplayName("Redis 宕机：会话读取降级为 null，触发过滤器数据库回源")
    void getLoginUserDegradesToNull() {
        makeRedisDown();
        assertThat(tokenStore.getLoginUser("jti123")).isNull();
    }

    @Test
    @DisplayName("Redis 宕机：会话写入与黑名单写入静默降级，不抛异常")
    void writeOperationsDegradeSilently() {
        makeRedisDown();
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        tokenStore.saveLoginUser("jti123", user, Duration.ofMinutes(5));
        tokenStore.blacklist("jti123", Duration.ofMinutes(5));
        assertThat(tokenStore.isBlacklisted("jti123")).isFalse();
    }

    @Test
    @DisplayName("Redis 宕机：登录失败计数走进程内内存兜底，暴力破解防护不失效")
    void loginFailCountersDegrade() {
        makeRedisDown();
        // 降级语义（第九期安全整改）：Redis 不可用时改用进程内内存计数兜底，
        // 暴力破解防护不因缓存故障失效——首次累加应返回 1 而非 0。
        assertThat(tokenStore.getLoginFailSafe("alice")).isZero();
        assertThat(tokenStore.increaseLoginFailSafe("alice", Duration.ofMinutes(10))).isEqualTo(1);
        assertThat(tokenStore.increaseLoginFailSafe("alice", Duration.ofMinutes(10))).isEqualTo(2);
        assertThat(tokenStore.getLoginFailSafe("alice")).isEqualTo(2);
        tokenStore.clearLoginFailSafe("alice");
        assertThat(tokenStore.getLoginFailSafe("alice")).isZero();
    }

    @Test
    @DisplayName("Redis 宕机：探活返回 false，安全关键操作应 fail-closed")
    void requireAvailableReturnsFalseWhenDown() {
        makeRedisDown();
        assertThat(tokenStore.requireAvailable()).isFalse();
    }

    @Test
    @DisplayName("Redis 正常：探活 PING 成功，关键操作可继续")
    void requireAvailableTrueWhenUp() {
        when(redisTemplate.getConnectionFactory()).thenReturn(null);
        // getConnectionFactory 返回 null 无法 ping —— 用真实语义模拟：不抛异常即视为可用
        org.springframework.data.redis.connection.RedisConnectionFactory factory =
                org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        org.springframework.data.redis.connection.RedisConnection connection =
                org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        assertThat(tokenStore.requireAvailable()).isTrue();
    }

    @Test
    @DisplayName("Redis 恢复后：黑名单与会话读写恢复正常语义")
    void recoversAfterOutage() {
        makeRedisDown();
        assertThat(tokenStore.isBlacklisted("jti123")).isFalse();

        // 恢复：黑名单命中应返回 true（doReturn 避免 when() 触发旧桩抛异常）
        org.mockito.Mockito.doReturn(true).when(redisTemplate)
                .hasKey(RedisKeys.tokenBlacklist("jti123"));
        assertThat(tokenStore.isBlacklisted("jti123")).isTrue();
    }

    @Test
    @DisplayName("刷新令牌登记集合缺失时按未登记处理（拒绝刷新）")
    void refreshTokenRegistrationMissingRejected() {
        makeRedisUp();
        when(redisTemplate.hasKey(RedisKeys.userRefreshTokens(1L))).thenReturn(false);
        assertThat(tokenStore.isRefreshTokenRegistered(1L, "jti123")).isFalse();
    }

    @Test
    @DisplayName("刷新令牌已登记时可校验通过")
    void refreshTokenRegisteredPasses() {
        makeRedisUp();
        when(redisTemplate.hasKey(RedisKeys.userRefreshTokens(1L))).thenReturn(true);
        when(setOps.isMember(RedisKeys.userRefreshTokens(1L), "jti123")).thenReturn(true);
        assertThat(tokenStore.isRefreshTokenRegistered(1L, "jti123")).isTrue();
    }

    @Test
    @DisplayName("Redis 宕机：刷新令牌登记校验安全失败（按未登记拒绝）")
    void refreshTokenCheckDegradesToReject() {
        makeRedisDown();
        assertThat(tokenStore.isRefreshTokenRegistered(1L, "jti123")).isFalse();
    }
}
