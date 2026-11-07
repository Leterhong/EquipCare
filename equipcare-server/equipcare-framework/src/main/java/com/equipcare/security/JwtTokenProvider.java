package com.equipcare.security;
import com.equipcare.common.security.LoginUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。
 *
 * <p>令牌类型通过 claim {@code typ} 区分：{@code access} 用于接口访问，
 * {@code refresh} 仅用于换取新令牌，避免刷新令牌被直接用于业务接口。
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_REAL_NAME = "name";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final int MIN_SECRET_LENGTH = 32;

    private final JwtProperties properties;
    private SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "equipcare.jwt.secret 未配置或长度不足 " + MIN_SECRET_LENGTH + " 字节，"
                            + "请通过环境变量 EQUIPCARE_JWT_SECRET 注入");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT 签名密钥加载完成，签名算法 HS256，访问令牌有效期 {} 分钟", properties.getAccessTokenMinutes());
    }

    public TokenInfo generateAccessToken(LoginUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getAccessTokenMinutes(), ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUsername())
                .id(jti)
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_REAL_NAME, user.getRealName())
                .claim("tid", user.getTenantId() == null ? 0L : user.getTenantId())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
        return new TokenInfo(token, jti, expiry);
    }

    public TokenInfo generateRefreshToken(LoginUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getRefreshTokenMinutes(), ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUsername())
                .id(jti)
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
        return new TokenInfo(token, jti, expiry);
    }

    /**
     * 解析并校验令牌。
     *
     * @throws io.jsonwebtoken.ExpiredJwtException 令牌过期
     * @throws JwtException                        签名错误或格式非法
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public Long getUserId(Claims claims) {
        Object value = claims.get(CLAIM_USER_ID);
        return value == null ? null : Long.valueOf(value.toString());
    }

    public long accessTokenSeconds() {
        return properties.getAccessTokenMinutes() * 60;
    }

    public long refreshTokenSeconds() {
        return properties.getRefreshTokenMinutes() * 60;
    }

    /**
     * 令牌签发结果。
     *
     * @param token     令牌字符串
     * @param jti       令牌唯一标识，用于黑名单与在线会话
     * @param expiresAt 过期时间
     */
    public record TokenInfo(String token, String jti, Instant expiresAt) {
    }
}
