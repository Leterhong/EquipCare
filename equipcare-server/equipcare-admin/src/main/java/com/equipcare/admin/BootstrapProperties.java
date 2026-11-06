package com.equipcare.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 初始管理员创建配置。
 *
 * <p>系统不预置任何账号，首个管理员通过一次性启动参数创建，避免出现默认口令。
 */
@Component
@ConfigurationProperties(prefix = "equipcare.bootstrap")
public class BootstrapProperties {

    /** 是否启用一次性管理员创建 */
    private boolean enabled = false;

    /** 管理员登录名 */
    private String username;

    /** 管理员初始密码（明文传入，落库前 BCrypt 加密） */
    private String password;

    /** 管理员姓名 */
    private String realName = "系统管理员";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }
}
