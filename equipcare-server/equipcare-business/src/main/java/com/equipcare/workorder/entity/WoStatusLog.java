package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.LogEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单状态流转日志。只允许插入，不允许修改与删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wo_status_log")
public class WoStatusLog extends LogEntity {

    private Long workOrderId;

    private String fromStatus;

    private String toStatus;

    private String action;

    private Long operatorId;

    /** 操作人角色快照，便于事后追溯当时的权限 */
    private String operatorRole;

    private String reason;

    /** 上一状态停留分钟数 */
    private Integer costMinutes;
}
