package com.equipcare.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 备件分类。
 */
@Data
@Schema(description = "备件分类")
public class PartCategoryVO {

    private Long id;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "说明")
    private String remark;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "分类下的备件数量")
    private Long partCount;
}
