package com.equipcare.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 副驾配置（OpenAI 兼容协议，Provider 无关）。
 *
 * <p>支持任何兼容 /chat/completions 的服务：火山方舟豆包
 * （https://ark.cn-beijing.volces.com/api/v3）、智谱 GLM、DeepSeek、
 * 自建 vLLM/Ollama 等，只需换 base-url / api-key / model 三个值。
 *
 * <p><b>红线约束</b>：enabled 默认 false —— 未配置 API Key 时系统所有功能
 * 完整可用，仅 AI 入口隐藏（见 docs/09 的「不依赖付费 AI 也能运行」）。
 */
@Component
@ConfigurationProperties(prefix = "equipcare.ai")
public class AiProperties {

    /** 总开关：false 时 AI 端点返回未启用提示，前端隐藏 AI 入口 */
    private boolean enabled = false;

    /** OpenAI 兼容服务根地址（不带末尾斜杠） */
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    /** API Key（环境变量 EQUIPCARE_AI_API_KEY 注入，绝不写入代码库） */
    private String apiKey = "";

    /** 模型 ID（如 doubao-seed-1-6-251015 / glm-4.7 / deepseek-chat） */
    private String model = "";

    /** 采样温度 */
    private double temperature = 0.3;

    /** 单次回复最大 token */
    private int maxTokens = 2048;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5_000;

    /** 单次请求总超时（毫秒），流式诊断建议 120s 以上 */
    private int requestTimeoutMs = 180_000;

    /** 全局最大并发 AI 流式请求数（保护上游配额） */
    private int maxConcurrentStreams = 4;

    /** 视觉模型（巡检拍照缺陷识别）：不配则回退 model（需为多模态模型） */
    private String visionModel = "";

    /** 嵌入模型（维修知识库 RAG）：不配则知识库整体不启用 */
    private String embeddingModel = "";

    /** 知识库开关（embedding-model 非空时由 yml 派生为 true） */
    private boolean kbEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public boolean isKbEnabled() {
        return kbEnabled;
    }

    public void setKbEnabled(boolean kbEnabled) {
        this.kbEnabled = kbEnabled;
    }
}
