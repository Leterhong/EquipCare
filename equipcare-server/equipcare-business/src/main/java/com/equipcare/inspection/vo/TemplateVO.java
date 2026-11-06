package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检模板列表项。
 */
@Data
@Schema(description = "巡检模板列表项")
public class TemplateVO {

    private Long id;

    private String templateCode;

    private String templateName;

    private Long categoryId;

    private String categoryName;

    private Integer version;

    @Schema(description = "状态：DRAFT / PUBLISHED / DISABLED")
    private String status;

    private Integer itemCount;

    private String description;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    @Schema(description = "被多少个计划引用")
    private Integer planCount;
}
