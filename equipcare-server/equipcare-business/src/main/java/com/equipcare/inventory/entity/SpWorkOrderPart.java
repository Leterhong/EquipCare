package com.equipcare.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单备件领用。
 *
 * <p>注意：本表没有 {@code create_by/create_time/update_by/update_time} 四列——
 * 领用单只认 {@code apply_*} 与 {@code audit_*} 两组业务时间，
 * 因此实体不继承 {@code BaseEntity}（继承会让 MyBatis-Plus 拼出四个不存在的列，
 * 插入直接报 {@code Unknown column 'create_by'}）。
 */
@Data
@TableName("sp_work_order_part")
@Schema(description = "工单备件领用")
public class SpWorkOrderPart implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    private Long partId;

    /** 备件名称快照，避免备件改名后历史单据失真 */
    private String partName;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private Long applyUserId;

    private LocalDateTime applyTime;

    private Long auditUserId;

    private LocalDateTime auditTime;

    /** APPLIED / ISSUED / REJECTED / RETURNED */
    private String issueStatus;

    private LocalDateTime issueTime;

    private Long txnId;

    private String remark;
}
