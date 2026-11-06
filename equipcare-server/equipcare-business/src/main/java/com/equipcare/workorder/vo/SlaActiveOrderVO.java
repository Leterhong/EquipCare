package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活跃工单的 SLA 实时状态（预警中心「当前进行中」列表）。
 *
 * <p>由后端按政策实时计算，前端不持有任何时限数字。
 */
@Data
@Schema(description = "活跃工单 SLA 状态")
public class SlaActiveOrderVO {

    private Long workOrderId;

    private String orderNo;

    private String title;

    private String equipmentName;

    private String level;

    @Schema(description = "等级名称")
    private String levelLabel;

    @Schema(description = "当前所处阶段：PENDING_ASSIGN/PENDING_ACCEPT/IN_PROGRESS/PENDING_REVIEW")
    private String stage;

    @Schema(description = "阶段名称")
    private String stageLabel;

    @Schema(description = "SLA 状态：NORMAL/WARN/OVERDUE")
    private String slaStatus;

    @Schema(description = "当前阶段截止时间")
    private LocalDateTime dueTime;

    @Schema(description = "已耗用分钟数")
    private Long elapsedMinutes;

    @Schema(description = "阶段总时限（分钟）")
    private Long totalMinutes;

    @Schema(description = "耗用百分比（超过 100 表示已超时）")
    private Integer percent;
}
