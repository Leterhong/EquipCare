package com.equipcare.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水。
 */
@Data
@Schema(description = "库存流水")
public class StockTxnVO {

    private Long id;

    private String txnNo;

    private Long partId;

    private String partName;

    @Schema(description = "IN / OUT / RETURN / ADJUST")
    private String txnType;

    private String txnTypeLabel;

    private String bizType;

    private Long bizId;

    private Integer quantity;

    @Schema(description = "变动前库存")
    private Integer beforeStock;

    @Schema(description = "变动后库存")
    private Integer afterStock;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private Long operatorId;

    private LocalDateTime txnTime;

    private String remark;
}
