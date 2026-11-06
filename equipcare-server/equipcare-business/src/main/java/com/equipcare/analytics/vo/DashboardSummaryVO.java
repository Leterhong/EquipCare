package com.equipcare.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 运维数据驾驶舱 · 指标总览（14 项指标，全部后端实时聚合）。
 *
 * <p>口径说明（与前端约定，不得在前端另行计算）：
 * <ul>
 *   <li>设备正常 = IN_USE + IDLE；故障 = REPAIRING；停用 = SCRAPPED（当前快照）；</li>
 *   <li>设备健康率 = 正常数 / 设备总数 × 100（总数为 0 时返回 null，前端显示「暂无数据」）；</li>
 *   <li>巡检完成率 = 周期内已提交任务 / 周期内全部任务 × 100（含逾期未提交，
 *       「应执行而未执行」就是未完成；分母为 0 返回 null）；</li>
 *   <li>工单超时数 = 周期内报告且 overdue_flag = 1 的工单；</li>
 *   <li>MTTR（平均维修时长，分钟）= 周期内完成工单的
 *       actual_finish_time - start_repair_time 的平均值（仅统计两者非空的完成单；
 *       从维修工实际开始修算起，报修等待与验收时间不计入）；</li>
 *   <li>MTBF（平均故障间隔，小时）= 周期小时数 × 当前正常设备数 / 周期内故障工单数
 *       （报告口径的近似：以当前可用设备数近似周期内平均可用设备；
 *       故障工单数为 0 时返回 null——没有故障就没有 MTBF，不编造）；</li>
 *   <li>维修成本 = 周期内完成工单的 spare_cost（备件费）合计，按完成时间归属；</li>
 *   <li>备件消耗金额 = 周期内出库流水（txn_type = OUT）金额合计，按发生时间归属。
 *       与维修成本口径不同：前者只算已闭环工单，后者是全部出库（含未闭环工单领料）。</li>
 * </ul>
 */
@Data
@Schema(description = "驾驶舱指标总览")
public class DashboardSummaryVO {

    @Schema(description = "设备总数")
    private Long equipmentTotal;

    @Schema(description = "正常设备数（在用 + 闲置）")
    private Long equipmentNormal;

    @Schema(description = "故障设备数（维修中）")
    private Long equipmentFault;

    @Schema(description = "停用设备数（已报废）")
    private Long equipmentDisabled;

    @Schema(description = "设备健康率（%），总数为 0 时为 null")
    private BigDecimal equipmentHealthRate;

    @Schema(description = "巡检完成率（%），周期内无任务时为 null")
    private BigDecimal inspectionCompletionRate;

    @Schema(description = "周期内异常发现数量")
    private Long anomalyCount;

    @Schema(description = "待处理工单数量（未完结且未取消）")
    private Long workOrderPending;

    @Schema(description = "周期内已完成工单数量（按完成时间）")
    private Long workOrderCompleted;

    @Schema(description = "周期内超时工单数量")
    private Long workOrderOverdue;

    @Schema(description = "平均维修时长 MTTR（分钟），无可用样本时为 null")
    private BigDecimal mttrMinutes;

    @Schema(description = "平均故障间隔 MTBF（小时），周期内无故障工单时为 null")
    private BigDecimal mtbfHours;

    @Schema(description = "维修成本（元）= 周期内完成工单备件费合计")
    private BigDecimal maintenanceCost;

    @Schema(description = "备件消耗金额（元）= 周期内出库流水金额合计")
    private BigDecimal spareConsumptionAmount;
}
