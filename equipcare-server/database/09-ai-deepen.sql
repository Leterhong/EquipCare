-- =====================================================================
-- 巡维云 EquipCare · AI 深化增量脚本（第九期）
-- 文件：database/09-ai-deepen.sql
-- 说明：维修知识库（RAG）文档表。幂等可重复执行；不写死库名。
-- 设计：轻量 RAG —— 文档向量以 JSON 存于行内，检索时全量载入内存
--       计算余弦相似度（知识库规模为百级文档，无需向量引擎）。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_kb_document (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识文档主键',
    title       VARCHAR(191)    NOT NULL COMMENT '标题（如：主轴异响排查手册）',
    content     TEXT            NOT NULL COMMENT '正文（建议单篇 200~4000 字，过长自动截断）',
    embedding   JSON            NULL COMMENT '标题+正文的嵌入向量（JSON 数组，检索用）',
    enabled     TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用（停用不参与检索）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_kb_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'AI 维修知识库（RAG 文档 + 行内向量）';
