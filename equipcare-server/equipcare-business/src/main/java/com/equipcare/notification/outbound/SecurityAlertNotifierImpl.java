package com.equipcare.notification.outbound;

import com.equipcare.common.security.SecurityAlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全告警外发实现（framework 登录流程 → 企微/邮件）。
 *
 * <p>当前覆盖「账号连续失败锁定」事件：
 * <ul>
 *   <li>企微：配置了 webhook 即群播；</li>
 *   <li>邮件：配置 equipcare.notify.security-email 收件人（逗号分隔）后逐个发送。</li>
 * </ul>
 * 未配置任何通道时静默跳过。节流：同一账号 5 分钟内只告警一次，
 * 防止锁定期间反复尝试导致刷屏。
 */
@Component
public class SecurityAlertNotifierImpl implements SecurityAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(SecurityAlertNotifierImpl.class);

    private static final long THROTTLE_MS = 5 * 60 * 1000L;
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WeComWebhookNotifier wecom;
    private final EmailNotifier email;
    private final OutboundNotifyProperties props;
    /** 节流：username → 上次告警时间戳 */
    private final Map<String, AtomicLong> lastAlertAt = new ConcurrentHashMap<>();
    /** 死信告警节流：eventType → 上次告警时间戳 */
    private final Map<String, AtomicLong> lastDeadAlert = new ConcurrentHashMap<>();

    public SecurityAlertNotifierImpl(WeComWebhookNotifier wecom, EmailNotifier email,
                                     OutboundNotifyProperties props) {
        this.wecom = wecom;
        this.email = email;
        this.props = props;
    }

    @Override
    public void onDeadLetter(Long outboxId, String eventType, String cause) {
        // 同类死信 1 小时限流（死信通常成批出现）
        long now = System.currentTimeMillis();
        AtomicLong last = lastDeadAlert.computeIfAbsent(eventType, k -> new AtomicLong());
        if (now - last.get() < 60 * 60 * 1000L) {
            return;
        }
        last.set(now);
        String subject = "安全告警 · 领域事件投递判死（" + eventType + "）";
        String content = "Outbox 事件 " + outboxId + "（" + eventType + "）重试超限已判 DEAD，"
                + "需人工介入排查 event_outbox 表。\n失败原因：" + (cause == null ? "未知" : cause);
        if (wecom.enabled()) {
            wecom.sendMarkdown("**安全告警 · Outbox 死信**\n" + content);
        }
        if (email.enabled() && props.getSecurityEmail() != null && !props.getSecurityEmail().isBlank()) {
            for (String to : props.getSecurityEmail().split(",")) {
                email.send(to.trim(), subject, content);
            }
        }
        log.warn("Outbox 死信告警已发出 outboxId={} type={}", outboxId, eventType);
    }

    @Override
    public void onAccountLocked(String username, String clientIp, int maxFail) {
        long now = System.currentTimeMillis();
        AtomicLong last = lastAlertAt.computeIfAbsent(username, k -> new AtomicLong());
        long prev = last.get();
        if (now - prev < THROTTLE_MS) {
            return; // 节流窗口内：静默
        }
        if (!last.compareAndSet(prev, now)) {
            return; // 并发竞争失败：另一线程已发出
        }

        String time = LocalDateTime.now().format(T);
        String subject = "安全告警 · 账号已锁定（" + username + "）";
        String ip = clientIp == null || clientIp.isBlank() ? "未知" : clientIp;
        String content = """
                账号 %s 连续登录失败达到 %d 次，已被锁定 10 分钟。
                来源 IP：%s
                时间：%s
                若非本人操作，请立即重置该账号密码并检查审计日志。""".formatted(username, maxFail, ip, time);
        String markdown = "**安全告警 · 账号已锁定**\n" + content;

        boolean wecomOk = false;
        if (wecom.enabled()) {
            wecomOk = wecom.sendMarkdown(markdown);
        }
        boolean mailOk = false;
        String securityEmail = props.getSecurityEmail();
        if (email.enabled() && securityEmail != null && !securityEmail.isBlank()) {
            for (String to : securityEmail.split(",")) {
                mailOk |= email.send(to.trim(), subject, content);
            }
        }
        log.info("账号锁定安全告警已发出 username={} wecom={} mail={}", username, wecomOk, mailOk);
    }
}
