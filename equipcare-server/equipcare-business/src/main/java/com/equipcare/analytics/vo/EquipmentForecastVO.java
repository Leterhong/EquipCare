package com.equipcare.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 预测性维护视图（单台设备）。
 */
@Data
@Schema(description = "设备预测性维护视图：MTBF/MTTR/健康趋势外推")
public class EquipmentForecastVO {

    @Schema(description = "设备 id")
    private Long equipmentId;

    @Schema(description = "设备名称")
    private String equipmentName;

    @Schema(description = "资产编号")
    private String assetNo;

    @Schema(description = "设备状态")
    private String status;

    @Schema(description = "最新健康分（无快照为 null）")
    private Double latestScore;

    @Schema(description = "健康分趋势（最近 ≤8 个快照，按日期升序）")
    private List<TrendPoint> trend;

    @Schema(description = "线性回归斜率（分/天），快照不足 3 个为 null")
    private Double slopePerDay;

    @Schema(description = "7 天预测健康分（截断到 0~100），无法外推为 null")
    private Double predictedScore7d;

    @Schema(description = "近 90 天非取消工单数（近似故障次数）")
    private Integer failureCount90d;

    @Schema(description = "MTTR 平均修复时长（小时），窗口内无完成单为 null")
    private Double mttrHours;

    @Schema(description = "MTBF 平均故障间隔（小时），窗口内零故障为 null")
    private Double mtbfHours;

    @Schema(description = "风险等级：HIGH / WATCH / LOW")
    private String riskLevel;

    @Schema(description = "分级依据（人话）")
    private String riskReason;

    /** 趋势点 */
    public record TrendPoint(LocalDate date, Double score) {
        public static TrendPoint of(LocalDate date, Double score) {
            return new TrendPoint(date, score);
        }
    }
}
