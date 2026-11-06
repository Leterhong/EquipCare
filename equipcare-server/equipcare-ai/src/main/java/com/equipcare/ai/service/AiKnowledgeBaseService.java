package com.equipcare.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.ai.client.OpenAiCompatClient;
import com.equipcare.ai.config.AiProperties;
import com.equipcare.ai.kb.AiKbDocument;
import com.equipcare.ai.kb.AiVectors;
import com.equipcare.ai.mapper.AiKbDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 维修知识库（轻量 RAG）。
 *
 * <p>写入：文档标题+正文经 /embeddings 向量化后随行存储；
 * 检索：查询向量化 → 全量载入启用文档 → 内存余弦 → TOP-K。
 * 知识库规模为百级文档，内存计算足够；未来替换向量引擎时对外接口不变。
 *
 * <p>启用条件：equipcare.ai.enabled=true 且 equipcare.ai.embedding-model 非空
 * （由 yml 的 kb-enabled 派生，见 {@link AiProperties}）。
 */
@Service
@ConditionalOnProperty(name = "equipcare.ai.kb-enabled", havingValue = "true")
public class AiKnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeBaseService.class);

    /** 单篇正文上限（字符） */
    private static final int MAX_CONTENT_CHARS = 4000;
    /** 诊断时召回的知识条数 */
    private static final int DIAGNOSE_TOP_K = 3;

    private final AiKbDocumentMapper kbMapper;
    private final OpenAiCompatClient client;
    private final AiProperties props;
    private final ObjectMapper objectMapper;

    public AiKnowledgeBaseService(AiKbDocumentMapper kbMapper, OpenAiCompatClient client,
                                  AiProperties props, ObjectMapper objectMapper) {
        this.kbMapper = kbMapper;
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** 新增/更新一篇知识文档并即时向量化。 */
    public long save(String title, String content) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        String text = content == null ? "" : content;
        if (text.length() > MAX_CONTENT_CHARS) {
            text = text.substring(0, MAX_CONTENT_CHARS);
        }
        double[] vector = embedOne(title + "\n" + text);
        AiKbDocument doc = new AiKbDocument();
        doc.setTitle(title.trim());
        doc.setContent(text);
        doc.setEmbedding(toJson(vector));
        doc.setEnabled(1);
        doc.setCreatedAt(LocalDateTime.now());
        kbMapper.insert(doc);
        log.info("知识库文档已入库 id={} title={} 向量维度={}", doc.getId(), title, vector.length);
        return doc.getId();
    }

    /** 检索与查询最相关的 TOP-K 知识片段。 */
    public List<AiVectors.Hit> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        double[] q = embedOne(query);
        List<AiKbDocument> docs = kbMapper.selectList(Wrappers.<AiKbDocument>lambdaQuery()
                .eq(AiKbDocument::getEnabled, 1));
        List<double[]> vectors = new ArrayList<>();
        List<AiVectors.Hit> hits = new ArrayList<>();
        for (AiKbDocument doc : docs) {
            double[] v = fromJson(doc.getEmbedding());
            if (v == null) {
                continue; // 向量缺失（如外部导入）跳过，不阻塞检索
            }
            vectors.add(v);
            hits.add(new AiVectors.Hit(doc.getId(), doc.getTitle(), doc.getContent(), 0));
        }
        return AiVectors.topK(q, vectors, hits, topK <= 0 ? DIAGNOSE_TOP_K : topK);
    }

    /**
     * 为诊断生成知识参考段（无命中或知识库为空返回空串，调用方直接拼接或忽略）。
     */
    public String buildKnowledgeSection(String diagnosisQuery) {
        List<AiVectors.Hit> hits = search(diagnosisQuery, DIAGNOSE_TOP_K);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n## 维修知识库参考（摘录，供比对）\n");
        for (AiVectors.Hit hit : hits) {
            String excerpt = hit.content().length() > 500
                    ? hit.content().substring(0, 500) + "…" : hit.content();
            sb.append("- 【").append(hit.title()).append("】").append(excerpt).append('\n');
        }
        return sb.toString();
    }

    private double[] embedOne(String text) {
        return client.embeddings(props.getEmbeddingModel(), List.of(text)).get(0);
    }

    private String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            throw new IllegalStateException("向量序列化失败", e);
        }
    }

    private double[] fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception e) {
            return null;
        }
    }
}
