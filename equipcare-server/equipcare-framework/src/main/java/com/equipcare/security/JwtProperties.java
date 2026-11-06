package com.equipcare.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。密钥必须通过环境变量注入，长度不少于 32 字节（HS256 要求）。
 */
@Component
@ConfigurationProperties(prefix = "equipcare.jwt")
public class JwtProperties {

    /** 签名密钥，生产环境必须由环境变量 EQUIPCARE_JWT_SECRET 提供 */
    private String secret;

    /** 签发者 */
    private String issuer = "equipcare";

    /** 访问令牌有效期（分钟） */
    private long accessTokenMinutes = 120;

    /** 刷新令牌有效期（分钟） */
    private long refreshTokenMinutes = 10080;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public void setAccessTokenMinutes(long accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public long getRefreshTokenMinutes() {
        return refreshTokenMinutes;
    }

    public void setRefreshTokenMinutes(long refreshTokenMinutes) {
        this.refreshTokenMinutes = refreshTokenMinutes;
    }
}
