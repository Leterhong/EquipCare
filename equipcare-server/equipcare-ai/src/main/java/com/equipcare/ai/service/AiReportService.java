package com.equipcare.ai.service;

import com.equipcare.ai.client.OpenAiCompatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * AI 维修报告生成：工单全生命周期数据 → 结构化 Markdown 报告
 * （故障概况 / 处理时间线 / 备件成本 / 结论与改进建议）。
 */
@Service
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiReportService {

    private final AiContextService contextService;
    private final OpenAiCompatClient client;

    public AiReportService(AiContextService contextService, OpenAiCompatClient client) {
        this.contextService = contextService;
        this.client = client;
    }

    /** 同步生成报告 Markdown（报告页一次性渲染，无需流式） */
    public String generate(long workOrderId) {
        String context = contextService.buildReportContext(workOrderId);
        return client.chat(contextService.buildReportPrompt(context));
    }
}
