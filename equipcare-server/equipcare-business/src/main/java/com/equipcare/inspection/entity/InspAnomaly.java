package com.equipcare.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检异常单。
 *
 * <p>异常 → 工单为强关联闭环：{@code work_order_id} 指向自动生成的工单；
 * 数据库对 {@code wo_work_order.anomaly_id} 建了唯一索引，保证一个异常只生成一张工单。
 */
@Data
@TableName("insp_anomaly")
public class InspAnomaly {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String anomalyNo;

    private Long taskId;

    private Long equipmentId;

    private Long templateItemId;

    private String itemName;

    private String level;

    private String description;

    private String attachmentIds;

    /** OPEN / WORK_ORDER / CLOSED */
    private String status;

    private Long workOrderId;

    private Long reporterId;

    private LocalDateTime reportTime;

    private LocalDateTime closeTime;

    private String closeRemark;

    private LocalDateTime createTime;
}
