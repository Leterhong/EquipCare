package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单轨迹。只允许插入。
 *
 * <p>注意：本表的时间字段是 {@code dispatch_time}，没有通用的 {@code create_time}，
 * 因此不继承 {@link com.equipcare.common.domain.LogEntity}。
 */
@Data
@TableName("wo_dispatch_log")
public class WoDispatchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    /** ASSIGN / TRANSFER / REASSIGN / REVOKE */
    private String dispatchType;

    private Long fromUserId;

    private Long toUserId;

    private Long dispatcherId;

    private LocalDateTime dispatchTime;

    private LocalDateTime acceptTime;

    private Integer rejectFlag;

    private String rejectReason;

    private String remark;
}
