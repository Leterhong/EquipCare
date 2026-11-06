package com.equipcare.ai.kb;

import java.util.List;

/**
 * 向量工具（纯函数，便于单测）：余弦相似度 + JSON 向量序列化。
 */
public final class AiVectors {

    private AiVectors() {
    }

    /** 余弦相似度，范围 [-1,1]；任一向量全零或长度不符返回 -2（视为不可比） */
    public static double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return -2;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return -2;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 命中结果：文档 id + 标题 + 相似度 */
    public record Hit(long documentId, String title, String content, double score) {
    }

    /** 相似度降序取 topK */
    public static List<Hit> topK(double[] query, List<double[]> docVectors,
                                 List<Hit> documents, int topK) {
        if (query == null || docVectors.size() != documents.size()) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, documents.size())
                .mapToObj(i -> new Hit(documents.get(i).documentId(), documents.get(i).title(),
                        documents.get(i).content(), cosine(query, docVectors.get(i))))
                .filter(h -> h.score() > -1)
                .sorted((x, y) -> Double.compare(y.score(), x.score()))
                .limit(Math.max(1, topK))
                .toList();
    }
}
