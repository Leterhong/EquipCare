package com.equipcare.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 健康分计算所需的原始指标。
 *
 * <p>同一个类承载三类统计结果，各查询只填充自己的字段，未涉及的保持 0。
 */
@Data
@Schema(description = "健康分原始指标")
public class HealthMetrics {

    @Schema(description = "窗口内工单数")
    private Integer workOrderCount = 0;

    @Schema(description = "窗口内紧急工单数（P1/P2）")
    private Integer urgentWorkOrderCount = 0;

    @Schema(description = "窗口内超时工单数")
    private Integer overdueWorkOrderCount = 0;

    @Schema(description = "窗口内异常数")
    private Integer anomalyCount = 0;

    @Schema(description = "窗口内严重异常数")
    private Integer highAnomalyCount = 0;

    @Schema(description = "窗口内逾期巡检任务数")
    private Integer overdueInspectCount = 0;

    @Schema(description = "窗口内正常完成的巡检任务数")
    private Integer normalInspectCount = 0;

    @Schema(description = "未完成维修工单数（不限窗口，在途即负担）")
    private Integer openWorkOrderCount = 0;

    @Schema(description = "距最近一次完成维修的天数；从未维修为 null（新设备不扣分）")
    private Integer idleDays;

    @Schema(description = "设备运行状态：IN_USE/IDLE/REPAIRING/SCRAPPED")
    private String equipmentStatus;

    /** 合并三类统计结果 */
    public HealthMetrics merge(HealthMetrics other) {
        if (other == null) {
            return this;
        }
        if (other.workOrderCount != null && other.workOrderCount > 0) {
            this.workOrderCount = other.workOrderCount;
            this.urgentWorkOrderCount = other.urgentWorkOrderCount;
            this.overdueWorkOrderCount = other.overdueWorkOrderCount;
        }
        if (other.anomalyCount != null && other.anomalyCount > 0) {
            this.anomalyCount = other.anomalyCount;
            this.highAnomalyCount = other.highAnomalyCount;
        }
        if ((other.overdueInspectCount != null && other.overdueInspectCount > 0)
                || (other.normalInspectCount != null && other.normalInspectCount > 0)) {
            this.overdueInspectCount = other.overdueInspectCount;
            this.normalInspectCount = other.normalInspectCount;
        }
        return this;
    }
}
