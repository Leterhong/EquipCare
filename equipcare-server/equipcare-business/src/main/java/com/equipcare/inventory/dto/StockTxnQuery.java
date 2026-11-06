package com.equipcare.inventory.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 库存流水查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "库存流水查询条件")
public class StockTxnQuery extends PageQuery {

    @Schema(description = "备件ID")
    private Long partId;

    @Schema(description = "流水类型：IN/OUT/RETURN/ADJUST")
    private String txnType;

    @Schema(description = "业务类型：WORK_ORDER/PURCHASE/RETURN/CHECK/OTHER")
    private String bizType;

    @Schema(description = "发生时间起（含）")
    private LocalDateTime timeFrom;

    @Schema(description = "发生时间止（含）")
    private LocalDateTime timeTo;
}
