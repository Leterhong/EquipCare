package com.equipcare.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存变动（入库 / 出库 / 盘点调整）。
 */
@Data
@Schema(description = "库存变动请求")
public class StockChangeDTO {

    @Schema(description = "备件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "备件不能为空")
    private Long partId;

    @Schema(description = "数量，必须大于 0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;

    @Schema(description = "单价快照，为空时取备件档案单价")
    @Min(value = 0, message = "单价不能为负数")
    private BigDecimal unitPrice;

    @Schema(description = "备注")
    @Size(max = 300, message = "备注长度不能超过 300")
    private String remark;
}
