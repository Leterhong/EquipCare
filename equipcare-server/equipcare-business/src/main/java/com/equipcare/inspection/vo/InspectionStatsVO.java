package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 巡检统计。
 *
 * <p>统计口径：完成率 = 已提交 / 应执行；异常率 = 异常任务数 / 已提交任务数。
 * 分母为 0 时返回 0，避免前端出现 NaN。
 */
@Data
@Schema(description = "巡检统计")
public class InspectionStatsVO {

    @Schema(description = "统计区间（天）")
    private Integer days;

    private LocalDate from;

    private LocalDate to;

    private Integer totalTasks;

    private Integer pendingTasks;

    private Integer submittedTasks;

    private Integer expiredTasks;

    private Integer abnormalTasks;

    @Schema(description = "完成率百分比，保留一位小数")
    private Double completionRate;

    @Schema(description = "异常率百分比，保留一位小数")
    private Double abnormalRate;

    private Integer anomalyTotal;

    private Integer anomalyOpen;

    private Integer anomalyConverted;

    private Integer anomalyClosed;

    @Schema(description = "按任务状态分布")
    private List<StatusCount> byStatus = new ArrayList<>();

    @Schema(description = "按计划统计（按任务量倒序，最多 10 条）")
    private List<PlanStat> byPlan = new ArrayList<>();

    @Schema(description = "按天趋势")
    private List<DailyStat> daily = new ArrayList<>();

    @Schema(description = "异常项目 Top 10")
    private List<ItemStat> topAnomalyItems = new ArrayList<>();

    /**
     * 状态分布。
     */
    @Data
    @Schema(description = "状态分布")
    public static class StatusCount {

        private String code;

        private String label;

        private Integer count;

        public StatusCount() {
        }

        public StatusCount(String code, String label, Integer count) {
            this.code = code;
            this.label = label;
            this.count = count;
        }
    }

    /**
     * 计划维度统计。
     */
    @Data
    @Schema(description = "计划维度统计")
    public static class PlanStat {

        private Long planId;

        private String planName;

        private Integer total;

        private Integer submitted;

        private Integer abnormal;

        private Double completionRate;
    }

    /**
     * 单日趋势。
     */
    @Data
    @Schema(description = "单日趋势")
    public static class DailyStat {

        private LocalDate date;

        private Integer total;

        private Integer submitted;

        private Integer abnormal;
    }

    /**
     * 异常项目排行。
     */
    @Data
    @Schema(description = "异常项目排行")
    public static class ItemStat {

        private String itemName;

        private Integer count;
    }
}
