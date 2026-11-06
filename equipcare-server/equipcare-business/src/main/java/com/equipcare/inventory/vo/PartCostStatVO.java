package com.equipcare.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 维修备件成本统计（按备件聚合）。
 *
 * <p>口径：只统计「工单领用出库」（txn_type=OUT 且 biz_type=WORK_ORDER），
 * 退料（RETURN）会从工单 spare_cost 冲减但不从本统计中扣除——
 * 本统计回答「哪些备件在维修中被消耗得最多」，退料明细走库存流水页核对。
 */
@Data
@Schema(description = "维修备件成本统计")
public class PartCostStatVO {

    private Long partId;

    private String partNo;

    private String partName;

    @Schema(description = "分类（快照）")
    private String category;

    @Schema(description = "累计领用数量")
    private Long totalQty;

    @Schema(description = "累计领用金额（元）")
    private BigDecimal totalAmount;
}
