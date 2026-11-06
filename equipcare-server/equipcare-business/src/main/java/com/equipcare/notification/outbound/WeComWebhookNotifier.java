package com.equipcare.notification.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 企业微信群机器人通知器（webhook，群播）。
 *
 * <p>群设置 → 群机器人 → 添加 → 复制 webhook 地址填入
 * {@code EQUIPCARE_NOTIFY_WECOM_WEBHOOK_URL} 即启用。
 * 使用 markdown 消息类型；失败仅记日志，绝不向调用方抛出（外发是尽力而为）。
 */
@Service
public class WeComWebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(WeComWebhookNotifier.class);

    private final OutboundNotifyProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public WeComWebhookNotifier(OutboundNotifyProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** 是否已配置企微通道 */
    public boolean enabled() {
        return props.getWecom().enabled();
    }

    /**
     * 发送 markdown 消息到群。content 支持 <b>/<font> 等企微 markdown 子集。
     *
     * @return true=投递成功（上游 2xx 且 errcode==0）
     */
    public boolean sendMarkdown(String content) {
        if (!enabled() || content == null || content.isBlank()) {
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("msgtype", "markdown", "markdown", Map.of("content", content)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getWecom().getWebhookUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String respBody = resp.body() == null ? "" : resp.body();
            boolean ok = resp.statusCode() == 200 && respBody.contains("\"errcode\":0");
            if (!ok) {
                log.warn("企微通知投递异常 status={} resp={}", resp.statusCode(),
                        respBody.length() > 200 ? respBody.substring(0, 200) : respBody);
            }
            return ok;
        } catch (Exception e) {
            log.warn("企微通知发送失败: {}", e.getMessage());
            return false;
        }
    }
}
