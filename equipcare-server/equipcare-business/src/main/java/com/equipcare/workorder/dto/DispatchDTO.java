package com.equipcare.workorder.dto;

import com.equipcare.common.validation.EnumValue;
import com.equipcare.workorder.enums.WorkOrderPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 派单 / 转派 / 强制转派请求。
 *
 * <p>派单环节可同时设定三件事：派给谁、优先级、完成时限；派单说明写入轨迹。
 * 优先级与完成时限只在派单/转派时生效，不改变故障等级决定的 SLA 基准。
 */
@Data
@Schema(description = "派单请求")
public class DispatchDTO {

    @Schema(description = "目标维修人ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择维修人")
    private Long assigneeId;

    @Schema(description = "维修优先级：HIGH / NORMAL / LOW，不传则保留原值")
    @EnumValue(target = WorkOrderPriority.class, message = "维修优先级取值非法")
    private String priority;

    @Schema(description = "完成时限（小时），1-720；不传则按故障等级默认时限")
    @Min(value = 1, message = "完成时限至少 1 小时")
    @Max(value = 720, message = "完成时限最多 720 小时（30 天）")
    private Integer resolveHours;

    @Schema(description = "派单说明 / 转派原因（强制转派时必填，不少于 5 字）")
    @Size(max = 300, message = "说明长度不能超过 300")
    private String reason;
}
