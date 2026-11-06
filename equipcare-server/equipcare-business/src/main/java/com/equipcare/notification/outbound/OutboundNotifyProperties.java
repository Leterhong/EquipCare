package com.equipcare.notification.outbound;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通知外发配置（企业微信群机器人 / SMTP 邮件）。
 *
 * <p>双通道互相独立、按需启用：webhook-url 配了就走企微，
 * mail.enabled=true 且 SMTP 信息齐全就走邮件。均默认关闭。
 */
@Component
@ConfigurationProperties(prefix = "equipcare.notify")
public class OutboundNotifyProperties {

    private final Wecom wecom = new Wecom();
    private final Mail mail = new Mail();

    public Wecom getWecom() {
        return wecom;
    }

    public Mail getMail() {
        return mail;
    }

    public static class Wecom {
        /** 企业微信群机器人 webhook 地址（配置即启用） */
        private String webhookUrl = "";

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public boolean enabled() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }

    public static class Mail {
        /** 邮件通知总开关 */
        private boolean enabled = false;
        private String host = "";
        private int port = 465;
        private String username = "";
        private String password = "";
        /** 发件人（默认取 username） */
        private String from = "";
        /** SMTP 连接/读取超时（毫秒） */
        private int timeoutMs = 10_000;

        public boolean enabled() {
            return enabled && host != null && !host.isBlank()
                    && username != null && !username.isBlank()
                    && password != null && !password.isBlank();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
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

        public String getFrom() {
            return from == null || from.isBlank() ? username : from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
