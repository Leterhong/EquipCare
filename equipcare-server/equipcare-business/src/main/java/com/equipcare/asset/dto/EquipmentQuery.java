package com.equipcare.asset.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 设备台账查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备台账查询条件")
public class EquipmentQuery extends PageQuery {

    @Schema(description = "关键字：资产编号 / 设备名称 / 出厂序列号")
    private String keyword;

    @Schema(description = "设备类别ID")
    private Long categoryId;

    @Schema(description = "所在位置ID")
    private Long locationId;

    @Schema(description = "状态：IN_USE / IDLE / REPAIRING / SCRAPPED")
    private String status;

    @Schema(description = "归属部门ID")
    private Long deptId;

    @Schema(description = "健康分下限")
    private BigDecimal minHealth;

    @Schema(description = "健康分上限")
    private BigDecimal maxHealth;
}
