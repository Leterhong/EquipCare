package com.equipcare.inventory.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 备件查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "备件查询条件")
public class PartQuery extends PageQuery {

    @Schema(description = "关键字：备件编码 / 名称 / 规格")
    private String keyword;

    private String category;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "状态：1 启用 0 停用")
    private Integer status;

    @Schema(description = "只看低于安全库存的备件")
    private Boolean belowSafety;
}
