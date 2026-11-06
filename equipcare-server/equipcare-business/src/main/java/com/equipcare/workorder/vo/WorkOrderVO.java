package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单列表项。
 */
@Data
@Schema(description = "工单列表项")
public class WorkOrderVO {

    private Long id;

    @Schema(description = "工单编号")
    private String orderNo;

    private String title;

    @Schema(description = "来源：INSPECT / ANOMALY / MANUAL / PLAN_MAINTAIN")
    private String source;

    private Long equipmentId;

    private String equipmentName;

    @Schema(description = "设备资产编号")
    private String assetNo;

    private String faultType;

    private String faultDesc;

    @Schema(description = "等级：P1 / P2 / P3 / P4（故障严重程度，决定 SLA）")
    private String level;

    @Schema(description = "维修优先级：HIGH / NORMAL / LOW（派单时的处理次序）")
    private String priority;

    @Schema(description = "优先级中文名")
    private String priorityLabel;

    private String status;

    @Schema(description = "状态中文名")
    private String statusLabel;

    private Long reporterId;

    private String reporterName;

    private Long assigneeId;

    private String assigneeName;

    private LocalDateTime reportTime;

    private LocalDateTime dispatchTime;

    private LocalDateTime acceptTime;

    private LocalDateTime responseDueTime;

    private LocalDateTime resolveDueTime;

    @Schema(description = "SLA 状态：NORMAL / WARN / OVERDUE / PAUSED")
    private String slaStatus;

    private Integer overdueFlag;

    private Integer rejectCount;

    private Integer rejectDispatchCount;

    private LocalDateTime closeTime;

    private BigDecimal spareCost;

    @Schema(description = "是否已被报修人评价（仅已完成工单可能为 true）")
    private Boolean evaluated;

    @Schema(description = "当前用户在该工单上允许执行的动作码")
    private List<String> allowedActions;
}
