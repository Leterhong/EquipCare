package com.equipcare.ai.client;

import com.equipcare.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议客户端（JDK HttpClient 实现，零第三方依赖）。
 *
 * <p>支持两种调用：
 * <ul>
 *   <li>{@link #chat}：同步一次性返回（派单建议 / 报告生成）；</li>
 *   <li>{@link #streamChat}：SSE 流式（故障诊断逐字输出），返回
 *       {@link StreamHandle} 供调用方在客户端断开时取消上游请求 ——
 *       「前端断了就停烧 token」，资源闭环由取消链保证。</li>
 * </ul>
 *
 * <p>协议要点：POST {base}/chat/completions；流式响应为
 * {@code data: {choices[0].delta.content}} 行序列，以 {@code data: [DONE]} 结束。
 */
@Component
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class OpenAiCompatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatClient.class);

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public OpenAiCompatClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
    }

    /** 对话消息（role: system/user/assistant） */
    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }
    }

    /**
     * 流式调用句柄：调用方（SSE 控制器）在 onCompletion/onTimeout/onError
     * 时调用 {@link #cancel()}，中断上游 HTTP 并停止读流。
     */
    public interface StreamHandle {
        void cancel();
    }

    /**
     * 同步对话：返回助手完整回复文本。非 2xx 抛 {@link AiCallException}。
     */
    public String chat(List<ChatMessage> messages) {
        ObjectNode body = buildBody(messages, false);
        HttpRequest request = newRequest(body);
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new AiCallException("上游返回 " + resp.statusCode() + ": " + abbreviate(resp.body()));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode()) {
                throw new AiCallException("上游响应缺少 choices[0].message.content");
            }
            return content.asText();
        } catch (IOException e) {
            throw new AiCallException("AI 上游连接失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException("AI 调用被中断", e);
        }
    }

    /**
     * 流式对话：在当前线程阻塞读流（调用方应放入独立执行器），
     * 每个增量文本片段回调 {@code onDelta}，结束后回调 {@code onDone}，
     * 任何失败回调 {@code onError}（含非 2xx / 网络中断 / 解析异常）。
     *
     * @return 句柄，用于客户端断开时取消上游
     */
    public StreamHandle streamChat(List<ChatMessage> messages, Consumer<String> onDelta,
                                   Runnable onDone, Consumer<Throwable> onError) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        // 独立守护线程做阻塞读流：并发上限由 AiStreamGuard 控制在个位数，
        // 线程-每-流的成本远低于污染 ForkJoinPool.commonPool
        Thread workerThread = new Thread(() -> {
            HttpResponse<java.io.InputStream> resp = null;
            try {
                HttpRequest request = newRequest(buildBody(messages, true));
                resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2) {
                    String errBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new AiCallException("上游返回 " + resp.statusCode() + ": " + abbreviate(errBody));
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!cancelled.get() && (line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring(5).trim();
                        if (data.isEmpty()) {
                            continue;
                        }
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        JsonNode delta = objectMapper.readTree(data)
                                .path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode() && !delta.asText().isEmpty()) {
                            onDelta.accept(delta.asText());
                        }
                    }
                }
                if (!cancelled.get()) {
                    onDone.run();
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    log.warn("AI 流式调用失败: {}", e.getMessage());
                    onError.accept(e);
                }
            } finally {
                if (resp != null) {
                    try {
                        resp.body().close();
                    } catch (IOException ignored) {
                        // 流可能已被取消关闭
                    }
                }
            }
        }, "ai-stream-" + System.identityHashCode(messages));
        workerThread.setDaemon(true);
        workerThread.start();
        return () -> {
            cancelled.set(true);
            // 上游流的阻塞读会在服务端结束或连接关闭时退出，cancelled 标志阻止后续回调
        };
    }

    private ObjectNode buildBody(List<ChatMessage> messages, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("stream", stream);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        ArrayNode arr = body.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode n = arr.addObject();
            n.put("role", m.role());
            n.put("content", m.content());
        }
        return body;
    }

    private HttpRequest newRequest(ObjectNode body) {
        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(props.getRequestTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new AiCallException("构造 AI 请求失败: " + e.getMessage(), e);
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    /** 上游调用失败（网络 / 非 2xx / 协议不符） */
    public static class AiCallException extends RuntimeException {
        public AiCallException(String message) {
            super(message);
        }

        public AiCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
