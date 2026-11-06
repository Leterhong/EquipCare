package com.equipcare.ai;

import com.equipcare.ai.kb.AiVectors;
import com.equipcare.ai.kb.AiVectors.Hit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库向量工具单测：余弦相似度与 TOP-K 召回。
 */
class AiVectorsTest {

    @Test
    void 同向向量相似度为1() {
        assertEquals(1.0, AiVectors.cosine(new double[]{1, 2, 3}, new double[]{2, 4, 6}), 1e-9);
    }

    @Test
    void 正交向量相似度为0() {
        assertEquals(0.0, AiVectors.cosine(new double[]{1, 0}, new double[]{0, 1}), 1e-9);
    }

    @Test
    void 长度不符或全零判为不可比() {
        assertEquals(-2, AiVectors.cosine(new double[]{1}, new double[]{1, 2}), 1e-9);
        assertEquals(-2, AiVectors.cosine(new double[]{0, 0}, new double[]{1, 2}), 1e-9);
        assertEquals(-2, AiVectors.cosine(null, new double[]{1}), 1e-9);
    }

    @Test
    void topK按相似度降序且过滤不可比() {
        double[] query = {1, 0};
        List<double[]> vectors = List.of(
                new double[]{1, 0},    // 相似度 1
                new double[]{0, 1},    // 相似度 0
                new double[]{-1, 0},   // 相似度 -1
                new double[]{0.6, 0.8} // 相似度 0.6
        );
        List<Hit> docs = List.of(
                new Hit(1, "手册A", "内容A", 0),
                new Hit(2, "手册B", "内容B", 0),
                new Hit(3, "手册C", "内容C", 0),
                new Hit(4, "手册D", "内容D", 0)
        );
        List<Hit> hits = AiVectors.topK(query, vectors, docs, 2);
        assertEquals(2, hits.size());
        assertEquals(1, hits.get(0).documentId());
        assertEquals(4, hits.get(1).documentId());
        assertTrue(hits.get(0).score() > hits.get(1).score());
    }

    @Test
    void topK对空查询或长度不匹配返回空() {
        assertTrue(AiVectors.topK(null, List.of(new double[]{1}), List.of(new Hit(1, "t", "c", 0)), 3).isEmpty());
        assertTrue(AiVectors.topK(new double[]{1}, List.of(), List.of(), 3).isEmpty());
    }
}
