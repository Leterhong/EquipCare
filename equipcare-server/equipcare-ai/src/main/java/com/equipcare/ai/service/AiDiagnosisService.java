package com.equipcare.ai.service;

import com.equipcare.ai.client.OpenAiCompatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * AI 故障诊断（流式）：工单 + 设备 + 健康分 + 轨迹 + 历史维修 →
 * 大模型逐字输出根因分析与维修步骤（Markdown）。
 */
@Service
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiDiagnosisService {

    private final AiContextService contextService;
    private final OpenAiCompatClient client;

    public AiDiagnosisService(AiContextService contextService, OpenAiCompatClient client) {
        this.contextService = contextService;
        this.client = client;
    }

    /** 流式回调集合 */
    public record Callbacks(Consumer<String> onDelta, Consumer<String> onError, Runnable onComplete) {
    }

    /**
     * 启动流式诊断。阻塞发生在后台线程（客户端实现内），本方法立即返回句柄；
     * 调用方在 SSE 连接断开时调用 {@code handle.cancel()} 停止上游请求。
     */
    public OpenAiCompatClient.StreamHandle diagnose(long workOrderId, Callbacks callbacks) {
        String context = contextService.buildDiagnosisContext(workOrderId);
        var messages = contextService.buildDiagnosisPrompt(context);
        return client.streamChat(messages,
                callbacks.onDelta(),
                callbacks.onComplete(),
                error -> callbacks.onError().accept(readable(error)));
    }

    private String readable(Throwable t) {
        if (t instanceof OpenAiCompatClient.AiCallException e) {
            return e.getMessage();
        }
        return "AI 服务暂不可用，请稍后重试";
    }
}
