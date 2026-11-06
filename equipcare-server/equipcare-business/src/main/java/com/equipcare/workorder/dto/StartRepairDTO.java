package com.equipcare.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开始维修请求。
 *
 * <p>「开始维修」不改变工单状态（接单后即处于维修中），它承担三件事：
 * 记录维修原因（为什么坏）、故障诊断（判断依据）、维修前照片，并正式起算维修时长。
 * 重复调用会被拒绝，避免同一张工单反复「开始」把维修时长刷成 0。
 */
@Data
@Schema(description = "开始维修请求")
public class StartRepairDTO {

    @Schema(description = "维修原因（故障成因简述）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写维修原因")
    @Size(min = 5, max = 500, message = "维修原因不少于 5 个字，且不超过 500")
    private String reason;

    @Schema(description = "故障诊断（判断依据与检测数据）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写故障诊断")
    @Size(min = 5, max = 2000, message = "故障诊断不少于 5 个字，且不超过 2000")
    private String diagnosis;

    @Schema(description = "维修前照片文件ID，逗号分隔")
    @Size(max = 500, message = "维修前照片标识过长")
    private String beforeImageIds;

    @Schema(description = "预计耗时（分钟）")
    @Min(value = 0, message = "耗时不能为负数")
    @Max(value = 100000, message = "耗时超出合理范围")
    private Integer estimateMinutes;
}
