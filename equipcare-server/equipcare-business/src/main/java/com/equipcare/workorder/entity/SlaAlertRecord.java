package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SLA 超时预警记录。
 *
 * <p>唯一键 (work_order_id, stage, alert_date)：同一工单同一阶段每天至多一条，
 * 由数据库唯一键保证定时任务无论跑多密都不会重复通知；
 * {@code alert_count} 以「当日重复扫描次数」累加，首次插入才发通知。
 * 跨天产生新行——工单还在超时，不提醒才是事故。
 */
@Data
@Schema(description = "SLA 超时预警记录")
@TableName("sla_alert_record")
public class SlaAlertRecord implements Serializable {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "工单ID")
    private Long workOrderId;

    @Schema(description = "超时阶段：PENDING_ASSIGN/PENDING_ACCEPT/IN_PROGRESS/PENDING_REVIEW")
    private String stage;

    @Schema(description = "预警日期（去重维度之一）")
    private LocalDate alertDate;

    @Schema(description = "工单等级快照")
    private String orderLevel;

    @Schema(description = "超时分钟数")
    private Integer overdueMinutes;

    @Schema(description = "当日累计提醒次数")
    private Integer alertCount;

    @Schema(description = "最近一次提醒时间")
    private LocalDateTime lastAlertTime;

    @Schema(description = "首次预警时间")
    private LocalDateTime createTime;
}
