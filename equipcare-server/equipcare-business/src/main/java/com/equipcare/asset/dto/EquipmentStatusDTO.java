package com.equipcare.asset.dto;

import com.equipcare.asset.enums.EquipmentStatus;
import com.equipcare.common.validation.EnumValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备状态变更。
 * <p>
 * 只允许人工在 IN_USE / IDLE / SCRAPPED 之间切换；REPAIRING 由工单闭环自动驱动，
 * 通过 {@link EquipmentStatus#manualChangeable()} 校验拦截。
 */
@Data
@Schema(description = "设备状态变更请求")
public class EquipmentStatusDTO {

    @Schema(description = "目标状态：IN_USE / IDLE / SCRAPPED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "目标状态不能为空")
    @EnumValue(target = EquipmentStatus.class, message = "设备状态取值非法")
    private String status;

    @Schema(description = "变更原因，报废时必填")
    @Size(max = 200, message = "变更原因长度不能超过 200")
    private String reason;
}
