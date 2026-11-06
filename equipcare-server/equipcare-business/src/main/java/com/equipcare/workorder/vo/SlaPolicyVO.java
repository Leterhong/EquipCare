package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SLA 政策。
 */
@Data
@Schema(description = "SLA 政策")
public class SlaPolicyVO {

    private Long id;

    private String policyName;

    private String level;

    @Schema(description = "等级名称（紧急 / 高 / 中 / 低）")
    private String levelLabel;

    @Schema(description = "响应时限（分钟）")
    private Integer responseMinutes;

    @Schema(description = "修复时限（分钟）")
    private Integer resolveMinutes;

    @Schema(description = "验收时限（分钟）")
    private Integer verifyMinutes;

    @Schema(description = "预警阈值百分比")
    private Integer warnPercent;

    @Schema(description = "超时升级通知角色")
    private String escalateRoleCode;

    private Boolean enabled;

    private String remark;
}
