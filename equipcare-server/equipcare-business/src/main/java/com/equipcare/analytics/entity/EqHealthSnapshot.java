package com.equipcare.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备健康快照。每天每台设备一条，支持按时间回溯健康分变化。
 */
@Data
@TableName("eq_health_snapshot")
public class EqHealthSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    private LocalDate statDate;

    private BigDecimal healthScore;

    /** 故障扣分 */
    private BigDecimal faultScore;

    /** 超时扣分 */
    private BigDecimal timeoutScore;

    /** 异常扣分 */
    private BigDecimal anomalyScore;

    /** 巡检逾期扣分 */
    private BigDecimal overdueScore;

    /** 巡检加分 */
    private BigDecimal bonusScore;

    private Integer workOrderCount;

    private Integer anomalyCount;

    /** 未完成维修工单数（待派单/待接单/维修中/待验收），第六阶段 v2 因素 */
    private Integer openWoCount;

    /** 距最近一次完成维修的天数（从未维修为 NULL），第六阶段 v2 因素 */
    private Integer idleDays;

    /** 逐项扣分明细 JSON：factor/deduction/detail/suggestion，第六阶段 v2 */
    private String riskDetail;

    /** HEALTHY / SUB_HEALTHY / WARNING / DANGER */
    private String level;

    private LocalDateTime createTime;
}
