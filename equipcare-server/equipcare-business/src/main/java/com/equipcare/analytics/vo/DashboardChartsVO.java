package com.equipcare.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 运维数据驾驶舱 · 八张图表数据。全部由后端按时间范围聚合，前端只负责渲染；
 * 任一图表在所选范围内无数据时对应列表为空，前端显示「暂无数据」。
 */
@Data
@Schema(description = "驾驶舱图表数据")
public class DashboardChartsVO {

    @Schema(description = "名称-数值对")
    public record NameValue(String name, BigDecimal value) {
    }

    @Schema(description = "月度值（value 维度随图表不同）")
    public record MonthValue(String month, BigDecimal total, BigDecimal part) {
    }

    /** 图 1：设备状态分布（当前快照，不受时间范围影响） */
    @Schema(description = "设备状态分布")
    private List<NameValue> equipmentStatusDist;

    /** 图 2：工单状态分布（周期内，按报告时间归属） */
    @Schema(description = "工单状态分布")
    private List<NameValue> workOrderStatusDist;

    /** 图 3：月度故障趋势（周期内非取消工单按月计数） */
    @Schema(description = "月度故障趋势")
    private List<MonthValue> monthlyFaultTrend;

    /** 图 4：巡检完成趋势（total=应执行任务数，part=已提交任务数） */
    @Schema(description = "巡检完成趋势")
    private List<MonthValue> monthlyInspectionTrend;

    /** 图 5：维修成本趋势（周期内完成工单备件费按月合计） */
    @Schema(description = "维修成本趋势")
    private List<MonthValue> monthlyCostTrend;

    /** 图 6：故障类型排行（Top 10） */
    @Schema(description = "故障类型排行")
    private List<NameValue> faultTypeRank;

    /** 图 7：设备故障排行（Top 10） */
    @Schema(description = "设备故障排行")
    private List<NameValue> equipmentFaultRank;

    /** 图 8：维修人员工作量排行（周期内完成工单数，Top 10） */
    @Schema(description = "维修人员工作量排行")
    private List<NameValue> repairmanRank;

    /** 图 9：备件消耗排行（周期内出库金额 Top 10） */
    private List<NameValue> sparePartRank;
}
