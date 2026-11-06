package com.equipcare.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全策略配置。
 */
@Component
@ConfigurationProperties(prefix = "equipcare.security")
public class SecurityProperties {

    /** 连续失败多少次锁定账号 */
    private int loginMaxFail = 5;

    /** 锁定时长（分钟），同时作为失败计数窗口 */
    private int lockMinutes = 10;

    public int getLoginMaxFail() {
        return loginMaxFail;
    }

    public void setLoginMaxFail(int loginMaxFail) {
        this.loginMaxFail = loginMaxFail;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }
}
