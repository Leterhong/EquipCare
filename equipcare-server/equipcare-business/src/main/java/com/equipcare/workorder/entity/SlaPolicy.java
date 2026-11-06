package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SLA 政策：按工单等级配置响应 / 修复 / 验收时限。
 *
 * <p>第六阶段起时限一律以本表为准（表缺行或停用时才兜底 {@code WorkOrderLevel} 枚举值），
 * 此前的枚举硬编码仅作为兜底，不再是配置入口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "SLA 政策")
@TableName("sla_policy")
public class SlaPolicy extends BaseEntity {

    @Schema(description = "政策名称")
    private String policyName;

    @Schema(description = "适用等级：P1/P2/P3/P4")
    private String level;

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

    @Schema(description = "是否启用：1启用 0停用")
    private Integer enabled;

    @Schema(description = "说明")
    private String remark;

    @Schema(description = "逻辑删除")
    @TableLogic
    private Integer deleted;
}
