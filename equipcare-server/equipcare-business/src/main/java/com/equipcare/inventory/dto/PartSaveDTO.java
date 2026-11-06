package com.equipcare.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 备件档案保存。库存数量不允许直接修改，必须走入库 / 出库 / 盘点调整。
 */
@Data
@Schema(description = "备件保存请求")
public class PartSaveDTO {

    private Long id;

    @Schema(description = "备件编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "备件编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,40}$", message = "备件编码需为 2-40 位字母/数字/下划线/中划线")
    private String partNo;

    @Schema(description = "备件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "备件名称不能为空")
    @Size(max = 100, message = "备件名称长度不能超过 100")
    private String partName;

    @Size(max = 50, message = "分类长度不能超过 50")
    private String category;

    @Schema(description = "分类ID（分类实体，填写后以它为准并同步分类快照）")
    private Long categoryId;

    @Size(max = 120, message = "规格型号长度不能超过 120")
    private String spec;

    @Schema(description = "单位", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "单位不能为空")
    @Size(max = 20, message = "单位长度不能超过 20")
    private String unit;

    @Size(max = 60, message = "品牌长度不能超过 60")
    private String brand;

    @Schema(description = "安全库存")
    @Min(value = 0, message = "安全库存不能为负数")
    private Integer safetyStock;

    @Schema(description = "单价（元）")
    @DecimalMin(value = "0.00", message = "单价不能为负数")
    @Digits(integer = 10, fraction = 2, message = "单价最多 10 位整数、2 位小数")
    private BigDecimal unitPrice;

    @Size(max = 60, message = "库位长度不能超过 60")
    private String warehouseLocation;

    @Size(max = 100, message = "供应商长度不能超过 100")
    private String supplier;

    private Integer status;
}
