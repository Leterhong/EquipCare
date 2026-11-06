package com.equipcare.ai.controller;

import com.equipcare.ai.kb.AiVectors;
import com.equipcare.ai.service.AiKnowledgeBaseService;
import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 维修知识库（轻量 RAG）端点。
 * 仅在 equipcare.ai.enabled=true 且 equipcare.ai.kb-enabled=true 时装载。
 */
@Tag(name = "AI 副驾 · 维修知识库", description = "RAG 文档管理与检索（诊断自动引用命中知识）")
@RestController
@RequestMapping("/api/ai/kb")
@ConditionalOnProperty(name = "equipcare.ai.kb-enabled", havingValue = "true")
public class AiKbController {

    private final AiKnowledgeBaseService kbService;

    public AiKbController(AiKnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    public record KbSaveRequest(String title, String content) {
    }

    @Operation(summary = "新增知识文档（即时向量化入库）")
    @PostMapping("/documents")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "AI知识库", action = "新增文档")
    public Result<Map<String, Object>> save(@RequestBody KbSaveRequest req) {
        long id = kbService.save(req.title(), req.content());
        return Result.ok(Map.of("id", id));
    }

    @Operation(summary = "知识检索（余弦相似度 TOP-K）")
    @GetMapping("/search")
    public Result<List<AiVectors.Hit>> search(@RequestParam("q") String q,
                                              @RequestParam(value = "topK", defaultValue = "5") int topK) {
        return Result.ok(kbService.search(q, topK));
    }
}
