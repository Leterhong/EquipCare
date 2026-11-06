package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 维修工单。
 *
 * <p>说明：工单表<b>没有</b> {@code deleted} 字段——工单是永久业务凭证，
 * 不允许逻辑删除，取消通过 {@code status=CANCELLED} 表达。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wo_work_order")
public class WoWorkOrder extends BaseEntity {

    private String orderNo;

    private String title;

    /** INSPECT / ANOMALY / MANUAL / PLAN_MAINTAIN */
    private String source;

    private Long sourceId;

    private Long equipmentId;

    /** 触发异常ID；唯一索引，保证一个异常只能生成一张工单 */
    private Long anomalyId;

    private String faultType;

    private String faultDesc;

    /** 故障现场照片文件ID，逗号分隔（文件在 sys_file，不复制） */
    private String faultImageIds;

    /** P1 / P2 / P3 / P4 */
    private String level;

    /** 维修优先级：HIGH / NORMAL / LOW，派单时由派单人设定 */
    private String priority;

    /** 见 WorkOrderStatus */
    private String status;

    private Long reporterId;

    private LocalDateTime reportTime;

    private Long dispatcherId;

    private LocalDateTime dispatchTime;

    private Long assigneeId;

    private LocalDateTime acceptTime;

    private LocalDateTime responseDueTime;

    private LocalDateTime resolveDueTime;

    /** 验收时限（提交验收时按 sla_policy.verify_minutes 计算） */
    private LocalDateTime verifyDueTime;

    private LocalDateTime startRepairTime;

    private LocalDateTime submitVerifyTime;

    private String repairSummary;

    private BigDecimal laborHours;

    private Long verifyUserId;

    private LocalDateTime verifyTime;

    /** PASS / REJECT */
    private String verifyResult;

    private String verifyRemark;

    private Integer qualityScore;

    private LocalDateTime actualFinishTime;

    private LocalDateTime closeTime;

    private BigDecimal spareCost;

    private Integer rejectCount;

    private Integer rejectDispatchCount;

    /** NORMAL / WARN / OVERDUE / PAUSED */
    private String slaStatus;

    private Integer overdueFlag;

    /** 创建前设备状态，取消工单时回滚用 */
    private String prevEquipmentStatus;

    private String cancelReason;

    @Version
    private Integer version;
}
