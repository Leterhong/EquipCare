package com.equipcare.notification.outbound;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SMTP 邮件通知器。
 *
 * <p>按 equipcare.notify.mail.* 自建 {@link JavaMailSenderImpl}（不用 spring.mail
 * 命名空间，避免与全局邮件配置耦合）。465 端口默认走 SSL。
 * 发送失败仅记日志，绝不向调用方抛出（外发是尽力而为）。
 */
@Service
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final OutboundNotifyProperties props;
    private volatile JavaMailSenderImpl sender;

    public EmailNotifier(OutboundNotifyProperties props) {
        this.props = props;
    }

    /** 是否已配置邮件通道 */
    public boolean enabled() {
        return props.getMail().enabled();
    }

    /**
     * 发送纯文本邮件给单个收件人。
     *
     * @return true=SMTP 接受
     */
    public boolean send(String to, String subject, String text) {
        if (!enabled() || to == null || to.isBlank()) {
            return false;
        }
        try {
            JavaMailSenderImpl mailSender = sender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(props.getMail().getFrom());
            helper.setTo(InternetAddress.parse(to));
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("邮件通知发送失败 to={} subject={}: {}", to, subject, e.getMessage());
            return false;
        }
    }

    /** 懒构建 + 配置热生效（每次发送前检查 host 是否变化） */
    private JavaMailSenderImpl sender() {
        OutboundNotifyProperties.Mail mail = props.getMail();
        JavaMailSenderImpl impl = sender;
        if (impl == null || !mail.getHost().equals(impl.getHost()) || mail.getPort() != impl.getPort()) {
            impl = new JavaMailSenderImpl();
            impl.setHost(mail.getHost());
            impl.setPort(mail.getPort());
            impl.setUsername(mail.getUsername());
            impl.setPassword(mail.getPassword());
            impl.setDefaultEncoding(StandardCharsets.UTF_8.name());
            Properties p = impl.getJavaMailProperties();
            p.put("mail.smtp.auth", "true");
            p.put("mail.smtp.connectiontimeout", String.valueOf(mail.getTimeoutMs()));
            p.put("mail.smtp.timeout", String.valueOf(mail.getTimeoutMs()));
            p.put("mail.smtp.writetimeout", String.valueOf(mail.getTimeoutMs()));
            if (mail.getPort() == 465) {
                p.put("mail.smtp.ssl.enable", "true");
            } else {
                p.put("mail.smtp.starttls.enable", "true");
            }
            sender = impl;
        }
        return impl;
    }
}
