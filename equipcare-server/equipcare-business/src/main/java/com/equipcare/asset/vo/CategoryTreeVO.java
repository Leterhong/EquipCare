package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备类别树节点。
 */
@Data
@Schema(description = "设备类别树节点")
public class CategoryTreeVO {

    private Long id;

    private Long parentId;

    @Schema(description = "类别编码，同时作为资产编号前缀")
    private String categoryCode;

    private String categoryName;

    @Schema(description = "默认巡检模板ID")
    private Long defaultTemplateId;

    @Schema(description = "建议保养周期（天）")
    private Integer maintainCycleDays;

    private Integer sort;

    @Schema(description = "状态：1 启用 0 停用")
    private Integer status;

    @Schema(description = "该类别下设备数量")
    private Integer equipmentCount;

    private List<CategoryTreeVO> children = new ArrayList<>();
}
