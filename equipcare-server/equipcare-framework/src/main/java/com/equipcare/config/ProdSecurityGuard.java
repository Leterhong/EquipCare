package com.equipcare.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 生产环境安全基线校验（仅在 prod profile 生效）。
 *
 * <p>启动时强制校验敏感配置，不满足则拒绝启动——避免带着开发默认密钥 / 默认密码上线：
 * <ul>
 *   <li>JWT 密钥不得等于源码中的开发默认值，且长度 >= 32 字节（JwtTokenProvider 二次校验）；</li>
 *   <li>数据库密码不得为空、不得为本地开发常见默认弱口令。</li>
 * </ul>
 * 本地 dev / test profile 不受影响。
 */
@Configuration
@Profile("prod")
public class ProdSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(ProdSecurityGuard.class);

    /** 与 application.yml 中开发默认值保持同步；出现任一即视为未更换 */
    private static final String DEV_DEFAULT_JWT_SECRET =
            "EquipCare-Dev-Secret-Key-Change-Me-In-Production-2026";

    private static final String[] DEV_DEFAULT_DB_PASSWORDS = {"123456", "root", "password", ""};

    private final String jwtSecret;
    private final DataSource dataSource;

    public ProdSecurityGuard(@Value("${equipcare.jwt.secret}") String jwtSecret,
                             DataSource dataSource) {
        this.jwtSecret = jwtSecret;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void verify() {
        if (jwtSecret == null || jwtSecret.isBlank()
                || DEV_DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "生产环境检测到未更换的 JWT 密钥：请通过环境变量 EQUIPCARE_JWT_SECRET 注入至少 32 字节随机密钥后重启");
        }
        String envPassword = System.getenv("EQUIPCARE_DB_PASSWORD");
        for (String weak : DEV_DEFAULT_DB_PASSWORDS) {
            if (weak.equals(envPassword)) {
                throw new IllegalStateException(
                        "生产环境检测到弱数据库口令：请通过环境变量 EQUIPCARE_DB_PASSWORD 注入强密码后重启");
            }
        }
        try (Connection ignored = dataSource.getConnection()) {
            log.info("生产环境安全基线校验通过（JWT 密钥已更换、数据库口令非弱默认值、数据库可连接）");
        } catch (Exception ex) {
            throw new IllegalStateException("生产环境安全基线校验失败：数据库连接不可用", ex);
        }
    }
}
