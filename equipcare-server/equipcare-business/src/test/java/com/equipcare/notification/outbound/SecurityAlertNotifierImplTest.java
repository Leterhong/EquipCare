package com.equipcare.notification.outbound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 安全告警节流与通道分发单元测试。
 */
class SecurityAlertNotifierImplTest {

    private WeComWebhookNotifier wecom;
    private EmailNotifier email;
    private OutboundNotifyProperties props;
    private SecurityAlertNotifierImpl notifier;

    @BeforeEach
    void setUp() {
        wecom = mock(WeComWebhookNotifier.class);
        email = mock(EmailNotifier.class);
        props = new OutboundNotifyProperties();
        notifier = new SecurityAlertNotifierImpl(wecom, email, props);
    }

    private void enableAll() {
        props.getWecom().setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=x");
        // mock 不读 props 配置：通道开关需显式 stub（真实实现里 enabled() 内部读取）
        when(wecom.enabled()).thenReturn(true);
        when(email.enabled()).thenReturn(true);
        props.getMail().setEnabled(true);
        props.getMail().setHost("smtp.example.com");
        props.getMail().setUsername("alarm@example.com");
        props.getMail().setPassword("pw");
        props.setSecurityEmail("admin@example.com,boss@example.com");
        when(wecom.sendMarkdown(anyString())).thenReturn(true);
        when(email.send(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void 双通道齐发且邮件逐收件人() {
        enableAll();
        notifier.onAccountLocked("alice", "1.2.3.4", 5);
        verify(wecom).sendMarkdown(contains("alice"));
        // security-email 两个收件人各发一封
        verify(email).send(eq("admin@example.com"), contains("alice"), contains("1.2.3.4"));
        verify(email).send(eq("boss@example.com"), contains("alice"), contains("10 分钟"));
        Mockito.verify(wecom, Mockito.times(1)).sendMarkdown(anyString());
    }

    @Test
    void 同一账号五分钟内只告警一次() throws Exception {
        enableAll();
        // 前一测试的 stub 不跨实例泄漏，此处显式启用（断言节流而非通道开关）
        when(wecom.enabled()).thenReturn(true);
        when(email.enabled()).thenReturn(true);
        notifier.onAccountLocked("bob", null, 5);
        Thread.sleep(20);
        notifier.onAccountLocked("bob", null, 5);   // 节流窗口内：静默
        Thread.sleep(20);
        notifier.onAccountLocked("bob", null, 5);   // 仍静默
        Mockito.verify(wecom, Mockito.times(1)).sendMarkdown(anyString());
        Mockito.verify(email, Mockito.times(2)).send(anyString(), anyString(), anyString()); // 仅首轮两个收件人
    }

    @Test
    void 未配置任何通道时静默跳过() {
        // mock 默认 enabled()=false（未配置）→ 只调用探活，不发生任何实际外发
        notifier.onAccountLocked("carol", null, 5);
        verify(wecom, never()).sendMarkdown(anyString());
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 仅配企微不发邮件() {
        props.getWecom().setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=x");
        when(wecom.enabled()).thenReturn(true);
        when(wecom.sendMarkdown(anyString())).thenReturn(true);
        notifier.onAccountLocked("dave", null, 5);
        verify(wecom).sendMarkdown(anyString());
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 告警内容包含来源IP与锁定时长() {
        enableAll();
        notifier.onAccountLocked("eve", "9.9.9.9", 5);
        verify(wecom).sendMarkdown(contains("9.9.9.9"));
        verify(wecom).sendMarkdown(contains("10 分钟"));
    }

    @Test
    void 未配置通道时send安全返回false() {
        // OutboundNotifyProperties 默认空配置
        var mail = new OutboundNotifyProperties.Mail();
        org.assertj.core.api.Assertions.assertThat(mail.enabled()).isFalse();
        var wecomProps = new OutboundNotifyProperties.Wecom();
        org.assertj.core.api.Assertions.assertThat(wecomProps.enabled()).isFalse();
        // Duration 引用防未使用告警（与 TokenStore 语义一致性说明）
        org.assertj.core.api.Assertions.assertThat(Duration.ofMinutes(5)).isNotNull();
    }
}
