package com.equipcare.ai.service;

import com.equipcare.ai.client.OpenAiCompatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 智能派单推荐：算法预打分（同类别修复数 ×2 + 总修复数 − 活跃负荷 ×1.5）
 * 选出 TOP 候选，再由大模型结合经验与负荷生成推荐理由，输出结构化 JSON。
 */
@Service
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AiDispatchService.class);

    /** 单个推荐项 */
    public record Suggestion(long userId, String realName, String reason,
                             int categoryRepaired, int totalRepaired, int activeLoad) {
    }

    private final AiContextService contextService;
    private final OpenAiCompatClient client;
    private final ObjectMapper objectMapper;

    public AiDispatchService(AiContextService contextService, OpenAiCompatClient client,
                             ObjectMapper objectMapper) {
        this.contextService = contextService;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成推荐。模型输出异常时降级为「纯算法 TOP1」，保证派单页永远有可用结果。
     */
    public List<Suggestion> suggest(long workOrderId) {
        AiContextService.DispatchContext ctx = contextService.buildDispatchContext(workOrderId);
        if (ctx.candidates().isEmpty()) {
            return List.of();
        }
        try {
            String raw = client.chat(contextService.buildDispatchPrompt(ctx));
            return parse(raw, ctx.candidates());
        } catch (Exception e) {
            log.warn("AI 派单推荐失败，降级为算法 TOP1: {}", e.getMessage());
            AiContextService.DispatchCandidate top = ctx.candidates().get(0);
            List<Suggestion> fallback = new ArrayList<>();
            fallback.add(new Suggestion(top.userId(), top.realName(),
                    "算法推荐：同类别修复 " + top.categoryRepaired() + " 单，当前负荷 " + top.activeLoad() + " 单",
                    top.categoryRepaired(), top.totalRepaired(), top.activeLoad()));
            return fallback;
        }
    }

    /** 解析模型 JSON 输出，并与候选集求交集（防止模型编造 userId） */
    private List<Suggestion> parse(String raw, List<AiContextService.DispatchCandidate> candidates) {
        List<Suggestion> result = new ArrayList<>();
        try {
            JsonNode arr = objectMapper.readTree(AiContextService.extractJsonArray(raw));
            for (JsonNode n : arr) {
                long userId = n.path("userId").asLong(-1);
                candidates.stream()
                        .filter(c -> c.userId() == userId)
                        .findFirst()
                        .ifPresent(c -> result.add(new Suggestion(c.userId(),
                                n.path("realName").asText(c.realName()),
                                n.path("reason").asText("综合经验与负荷推荐"),
                                c.categoryRepaired(), c.totalRepaired(), c.activeLoad())));
                if (result.size() >= 3) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("AI 派单 JSON 解析失败，返回空推荐: {}", e.getMessage());
        }
        return result;
    }
}
