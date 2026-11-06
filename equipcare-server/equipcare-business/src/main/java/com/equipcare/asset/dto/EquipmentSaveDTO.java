package com.equipcare.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增 / 编辑设备台账。
 */
@Data
@Schema(description = "设备台账保存请求")
public class EquipmentSaveDTO {

    @Schema(description = "设备ID；为空表示新增，非空表示编辑")
    private Long id;

    @Schema(description = "资产编号；留空则由类别编码 + 序列号自动生成", requiredMode = Schema.RequiredMode.REQUIRED)
    @Pattern(regexp = "^$|^[A-Za-z0-9_-]{4,40}$", message = "资产编号需为 4-40 位字母/数字/下划线/中划线")
    private String assetNo;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称长度不能超过 100")
    private String equipmentName;

    @Schema(description = "设备类别ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备类别不能为空")
    private Long categoryId;

    @Schema(description = "所在位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所在位置不能为空")
    private Long locationId;

    @Schema(description = "归属部门ID")
    private Long deptId;

    @Size(max = 60, message = "品牌长度不能超过 60")
    private String brand;

    @Size(max = 80, message = "型号长度不能超过 80")
    private String model;

    @Size(max = 120, message = "规格参数长度不能超过 120")
    private String spec;

    @Size(max = 80, message = "出厂序列号长度不能超过 80")
    private String serialNo;

    @Size(max = 100, message = "制造商长度不能超过 100")
    private String manufacturer;

    @Size(max = 100, message = "供应商长度不能超过 100")
    private String supplier;

    private LocalDate purchaseDate;

    private LocalDate installDate;

    private LocalDate warrantyEndDate;

    @Schema(description = "设备原值（元）")
    @DecimalMin(value = "0.00", message = "设备原值不能为负数")
    @Digits(integer = 10, fraction = 2, message = "设备原值最多 10 位整数、2 位小数")
    private BigDecimal originalValue;

    @Schema(description = "设备责任人ID")
    private Long responsibleUserId;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
