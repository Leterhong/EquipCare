package com.equipcare.security;
import com.equipcare.common.security.LoginUser;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与解析单元测试。
 */
class JwtTokenProviderTest {

    private static final String SECRET = "EquipCare-Unit-Test-Secret-Key-At-Least-32-Bytes";

    private JwtTokenProvider provider;
    private LoginUser user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("equipcare-test");
        properties.setAccessTokenMinutes(120);
        properties.setRefreshTokenMinutes(10080);
        provider = new JwtTokenProvider(properties);
        provider.init();
        user = new LoginUser(1001L, "inspector01", "$2a$10$not-a-real-hash", "张巡检",
                2L, 1, List.of("INSPECTOR"), List.of("inspect:task:view"));
    }

    @Test
    @DisplayName("访问令牌可解析，携带 userId、用户名、类型与 jti")
    void accessTokenRoundTrip() {
        JwtTokenProvider.TokenInfo info = provider.generateAccessToken(user);
        assertThat(info.token()).isNotBlank();
        assertThat(info.jti()).isNotBlank();
        assertThat(info.expiresAt()).isNotNull();

        Claims claims = provider.parse(info.token());
        assertThat(provider.getUserId(claims)).isEqualTo(1001L);
        assertThat(claims.getSubject()).isEqualTo("inspector01");
        assertThat(claims.getIssuer()).isEqualTo("equipcare-test");
        assertThat(claims.getId()).isEqualTo(info.jti());
        assertThat(provider.isAccessToken(claims)).isTrue();
        assertThat(provider.isRefreshToken(claims)).isFalse();
    }

    @Test
    @DisplayName("刷新令牌与访问令牌类型可区分，防止刷新令牌直接访问业务接口")
    void refreshTokenType() {
        JwtTokenProvider.TokenInfo info = provider.generateRefreshToken(user);
        Claims claims = provider.parse(info.token());
        assertThat(provider.isRefreshToken(claims)).isTrue();
        assertThat(provider.isAccessToken(claims)).isFalse();
    }

    @Test
    @DisplayName("每次签发的 jti 不同，可用于单点登出与黑名单")
    void jtiIsUnique() {
        JwtTokenProvider.TokenInfo first = provider.generateAccessToken(user);
        JwtTokenProvider.TokenInfo second = provider.generateAccessToken(user);
        assertThat(first.jti()).isNotEqualTo(second.jti());
        assertThat(first.token()).isNotEqualTo(second.token());
    }

    @Test
    @DisplayName("被篡改的令牌解析失败")
    void tamperedTokenRejected() {
        String token = provider.generateAccessToken(user).token();
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThatThrownBy(() -> provider.parse(tampered)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("其他密钥签发的令牌无法通过校验")
    void wrongSecretRejected() {
        JwtProperties other = new JwtProperties();
        other.setSecret("Another-Secret-Key-That-Is-Long-Enough-32-Bytes");
        JwtTokenProvider otherProvider = new JwtTokenProvider(other);
        otherProvider.init();
        String foreignToken = otherProvider.generateAccessToken(user).token();
        assertThatThrownBy(() -> provider.parse(foreignToken)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("密钥长度不足 32 字节时拒绝启动")
    void shortSecretRejected() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");
        JwtTokenProvider shortProvider = new JwtTokenProvider(properties);
        assertThatThrownBy(shortProvider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("访问令牌有效期与配置一致")
    void accessTokenSeconds() {
        assertThat(provider.accessTokenSeconds()).isEqualTo(120 * 60L);
    }
}
