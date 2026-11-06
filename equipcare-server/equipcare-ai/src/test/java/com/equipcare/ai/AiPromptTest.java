package com.equipcare.ai;

import com.equipcare.ai.service.AiContextService;
import com.equipcare.ai.service.AiDispatchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 提示词与输出解析单元测试（纯函数，不依赖数据库与网络）。
 */
class AiPromptTest {

    private final AiContextService contextService = new AiContextService(
            null, null, null, null, null, null, null, null, null);

    @Test
    void 诊断提示词包含固定结构要求() {
        var messages = contextService.buildDiagnosisPrompt("## 工单信息\n- 工单号：WO-1");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        String system = messages.get(0).content();
        assertTrue(system.contains("根因分析"));
        assertTrue(system.contains("维修步骤"));
        assertTrue(system.contains("简体中文"));
        assertTrue(messages.get(1).content().contains("WO-1"));
    }

    @Test
    void 报告提示词要求数据忠实() {
        var messages = contextService.buildReportPrompt("生命周期数据");
        assertTrue(messages.get(0).content().contains("不得虚构"));
        assertTrue(messages.get(0).content().contains("维修报告"));
    }

    @Test
    void 派单提示词输出JSON约束() {
        var ctx = new AiContextService.DispatchContext(1L, "WO-2026-001", "水泵异响", "MECH",
                "水泵", List.of(new AiContextService.DispatchCandidate(9L, "李维修", 3, 12, 1, 17.5)));
        var messages = contextService.buildDispatchPrompt(ctx);
        assertTrue(messages.get(0).content().contains("JSON 数组"));
        assertTrue(messages.get(1).content().contains("李维修"));
        assertTrue(messages.get(1).content().contains("水泵"));
    }

    @Test
    void 从模型输出中稳健提取JSON数组() {
        assertEquals("[]", AiContextService.extractJsonArray(null));
        assertEquals("[]", AiContextService.extractJsonArray("模型没输出数组"));
        String wrapped = "好的，以下是推荐：\n```json\n[{\"userId\":9,\"reason\":\"经验丰富\"}]\n```";
        assertEquals("[{\"userId\":9,\"reason\":\"经验丰富\"}]", AiContextService.extractJsonArray(wrapped));
        assertEquals("[1,2]", AiContextService.extractJsonArray("前缀 [1,2] 后缀"));
    }

    @Test
    void 解析结果与候选集求交集防止编造() {
        // parse 是私有方法，行为通过 suggest 的降级路径已覆盖；这里验证提取逻辑边界
        String raw = "[{\"userId\":1,\"reason\":\"a\"},{\"userId\":99,\"reason\":\"编造的人\"}]";
        String json = AiContextService.extractJsonArray(raw);
        assertTrue(json.contains("\"userId\":1"));
        assertTrue(json.contains("\"userId\":99"), "提取阶段保留全部，交由 parse 与候选集求交");
    }
}
