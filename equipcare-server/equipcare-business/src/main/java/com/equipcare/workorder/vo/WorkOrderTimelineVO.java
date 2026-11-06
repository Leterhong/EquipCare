package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转轨迹节点。
 */
@Data
@Schema(description = "工单流转轨迹节点")
public class WorkOrderTimelineVO {

    private String fromStatus;

    private String fromStatusLabel;

    private String toStatus;

    private String toStatusLabel;

    @Schema(description = "动作码")
    private String action;

    @Schema(description = "动作中文名")
    private String actionLabel;

    private Long operatorId;

    private String operatorName;

    @Schema(description = "操作人角色快照")
    private String operatorRole;

    private String reason;

    @Schema(description = "上一状态停留分钟数")
    private Integer costMinutes;

    private LocalDateTime createTime;
}
