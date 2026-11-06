package com.equipcare.workorder.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工单查询条件")
public class WorkOrderQuery extends PageQuery {

    @Schema(description = "关键字：工单号 / 标题 / 设备资产编号 / 设备名称")
    private String keyword;

    @Schema(description = "状态：PENDING_ASSIGN / PENDING_ACCEPT / IN_PROGRESS / PENDING_REVIEW / REJECTED / COMPLETED / CANCELLED")
    private String status;

    @Schema(description = "状态集合，用于「待办」等聚合查询")
    private List<String> statuses;

    @Schema(description = "等级：P1 / P2 / P3 / P4")
    private String level;

    @Schema(description = "维修优先级：HIGH / NORMAL / LOW")
    private String priority;

    @Schema(description = "是否按优先级排序（派单工作台用：先高优先级，再高等级）", example = "false")
    private Boolean priorityFirst;

    @Schema(description = "来源：INSPECT / ANOMALY / MANUAL / PLAN_MAINTAIN")
    private String source;

    @Schema(description = "SLA 状态：NORMAL / WARN / OVERDUE")
    private String slaStatus;

    private Long equipmentId;

    @Schema(description = "故障类型（字典编码）")
    private String faultType;

    private Long assigneeId;

    private Long reporterId;

    @Schema(description = "只看与我相关（我报修或我维修）", example = "false")
    private Boolean onlyMine;

    @Schema(description = "只看我负责维修的", example = "false")
    private Boolean onlyAssignee;

    @Schema(description = "只看已超时", example = "false")
    private Boolean overdueOnly;

    @Schema(description = "报修时间起")
    private LocalDateTime reportBegin;

    @Schema(description = "报修时间止")
    private LocalDateTime reportEnd;

    /** 由 Controller 注入当前登录用户，不接受前端传值 */
    @Schema(hidden = true)
    private Long currentUserId;
}
