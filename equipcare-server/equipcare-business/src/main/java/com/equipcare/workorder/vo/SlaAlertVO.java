package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SLA 超时预警记录（预警中心列表）。
 */
@Data
@Schema(description = "SLA 超时预警")
public class SlaAlertVO {

    private Long id;

    private Long workOrderId;

    @Schema(description = "工单编号")
    private String orderNo;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "设备名称")
    private String equipmentName;

    @Schema(description = "超时阶段：PENDING_ASSIGN/PENDING_ACCEPT/IN_PROGRESS/PENDING_REVIEW")
    private String stage;

    @Schema(description = "阶段名称（待派单 / 待接单 / 维修中 / 待验收）")
    private String stageLabel;

    @Schema(description = "工单等级")
    private String orderLevel;

    @Schema(description = "等级名称")
    private String levelLabel;

    @Schema(description = "超时分钟数")
    private Integer overdueMinutes;

    @Schema(description = "当日累计提醒次数")
    private Integer alertCount;

    @Schema(description = "最近一次提醒时间")
    private LocalDateTime lastAlertTime;

    @Schema(description = "首次预警时间")
    private LocalDateTime createTime;
}
