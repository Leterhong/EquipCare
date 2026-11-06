package com.equipcare.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SLA 政策更新。等级是主键维度，不允许改；时限与阈值均可在线调整，
 * 立即对后续建单 / 派单 / 提交验收生效（已生成的时限列不追溯）。
 */
@Data
@Schema(description = "SLA 政策更新请求")
public class SlaPolicyUpdateDTO {

    @Size(max = 60, message = "政策名称长度不能超过 60")
    private String policyName;

    @Schema(description = "响应时限（分钟）")
    @Min(value = 1, message = "响应时限至少 1 分钟")
    private Integer responseMinutes;

    @Schema(description = "修复时限（分钟）")
    @Min(value = 1, message = "修复时限至少 1 分钟")
    private Integer resolveMinutes;

    @Schema(description = "验收时限（分钟）")
    @Min(value = 1, message = "验收时限至少 1 分钟")
    private Integer verifyMinutes;

    @Schema(description = "预警阈值百分比")
    @Min(value = 1, message = "预警阈值至少 1%")
    @Max(value = 100, message = "预警阈值至多 100%")
    private Integer warnPercent;

    @Schema(description = "超时升级通知角色编码")
    @Size(max = 50, message = "角色编码长度不能超过 50")
    private String escalateRoleCode;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Size(max = 300, message = "说明长度不能超过 300")
    private String remark;
}
