package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 可选维修人（派单下拉用）。
 *
 * <p>带出「在办工单数」，让派单人能按负载分配，而不是盲派。
 */
@Data
@Schema(description = "可选维修人")
public class WorkOrderAssigneeVO {

    private Long id;

    private String username;

    private String realName;

    private String deptName;

    @Schema(description = "技能标签，便于按专业派单")
    private String skillTags;

    @Schema(description = "在办工单数（未完结）")
    private Integer pendingCount;
}
