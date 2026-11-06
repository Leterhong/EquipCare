package com.equipcare.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备健康分，含各扣分项明细，便于向用户解释分数构成。
 */
@Data
@Schema(description = "设备健康分")
public class HealthVO {

    private Long equipmentId;

    private String equipmentName;

    private String assetNo;

    private LocalDate statDate;

    @Schema(description = "健康分 0-100")
    private BigDecimal healthScore;

    @Schema(description = "等级：HEALTHY / SUB_HEALTHY / WARNING / DANGER")
    private String level;

    private String levelLabel;

    @Schema(description = "故障扣分")
    private BigDecimal faultScore;

    @Schema(description = "超时扣分")
    private BigDecimal timeoutScore;

    @Schema(description = "异常扣分")
    private BigDecimal anomalyScore;

    @Schema(description = "巡检逾期扣分")
    private BigDecimal overdueScore;

    @Schema(description = "巡检加分")
    private BigDecimal bonusScore;

    private Integer workOrderCount;

    private Integer anomalyCount;

    @Schema(description = "未完成维修工单数（v2 因素）")
    private Integer openWoCount;

    @Schema(description = "距最近一次完成维修的天数；从未维修为 null（v2 因素）")
    private Integer idleDays;

    @Schema(description = "逐项扣分明细：每个扣分因素的原因与建议（v2）")
    private List<RiskItem> riskItems = new ArrayList<>();

    @Schema(description = "建议处理措施（按扣分项聚合去重）")
    private List<String> suggestions = new ArrayList<>();

    @Schema(description = "近 30 天走势（按日期倒序）")
    private List<TrendPoint> trend = new ArrayList<>();

    /**
     * 逐项扣分明细（v2）：解释「为什么扣分、该做什么」。
     */
    @Data
    @Schema(description = "健康分扣分明细")
    public static class RiskItem {

        @Schema(description = "因素标识：FAULT/TIMEOUT/ANOMALY/OVERDUE/OPEN_ORDER/IDLE/STATUS/BONUS")
        private String factor;

        @Schema(description = "因素名称")
        private String label;

        @Schema(description = "该项扣分（正数），加分项为负数")
        private BigDecimal deduction;

        @Schema(description = "扣分原因（含原始计数与规则）")
        private String detail;

        @Schema(description = "建议处理措施")
        private String suggestion;
    }

    /**
     * 走势点。
     */
    @Data
    @Schema(description = "健康分走势点")
    public static class TrendPoint {

        private LocalDate statDate;

        private BigDecimal healthScore;

        private String level;
    }
}
