package com.equipcare.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 备件列表项。
 */
@Data
@Schema(description = "备件列表项")
public class PartVO {

    private Long id;

    private String partNo;

    private String partName;

    private String category;
    private Long categoryId;

    private String spec;

    private String unit;

    private String brand;

    private Integer safetyStock;

    private Integer currentStock;

    private BigDecimal unitPrice;

    private String warehouseLocation;

    private String supplier;

    private Integer status;

    @Schema(description = "是否低于安全库存")
    private Boolean belowSafety;
}
