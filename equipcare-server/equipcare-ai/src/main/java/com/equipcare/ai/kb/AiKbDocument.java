package com.equipcare.ai.kb;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 维修知识库文档（RAG 语料）。
 * 向量以 JSON 数组存于行内；检索规模为百级文档，内存余弦即可，
 * 未来规模增长可平滑替换为向量引擎（接口不变）。
 */
@Data
@TableName("ai_kb_document")
public class AiKbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题（如：主轴异响排查手册） */
    private String title;

    /** 正文（建议 200~4000 字，超长截断） */
    private String content;

    /** 标题+正文的嵌入向量（JSON 数组） */
    private String embedding;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
