package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单操作时间线的一条记录。
 *
 * <p>时间线不是单独一张表，而是把六类真实发生的动作按时间归并：
 * 状态流转、维修记录、派单轨迹、验收记录、备件领用、服务评价。
 * 每一类都有独立的原始表，这里只做只读聚合，因此不需要「再存一份」，
 * 任何一条都能回溯到源记录（{@link #sourceType} + {@link #sourceId}）。
 */
@Data
@Schema(description = "工单操作时间线节点")
public class WorkOrderTimelineItemVO {

    @Schema(description = "事件类型：CREATE / STATUS / REPAIR / DISPATCH / VERIFY / PART / EVALUATION")
    private String eventType;

    @Schema(description = "事件标题，如「派单」「提交验收」")
    private String title;

    @Schema(description = "事件说明，如派单说明、维修内容、驳回原因")
    private String detail;

    @Schema(description = "原始记录所属表：wo_status_log / wo_repair_record / ...")
    private String sourceType;

    @Schema(description = "原始记录主键")
    private Long sourceId;

    private Long operatorId;

    @Schema(description = "操作人姓名（每条都必须关联真实用户）")
    private String operatorName;

    @Schema(description = "操作人角色快照")
    private String operatorRole;

    @Schema(description = "状态类事件的来源状态")
    private String fromStatus;

    private String fromStatusLabel;

    @Schema(description = "状态类事件的目标状态")
    private String toStatus;

    private String toStatusLabel;

    @Schema(description = "上一状态停留分钟数")
    private Integer costMinutes;

    @Schema(description = "事件时间")
    private LocalDateTime eventTime;

    @Schema(description = "附件（维修记录照片等）")
    private List<SourceAttachmentVO> attachments = new ArrayList<>();
}
