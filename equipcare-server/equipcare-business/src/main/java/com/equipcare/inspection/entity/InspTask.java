package com.equipcare.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 巡检任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("insp_task")
public class InspTask extends BaseEntity {

    private String taskCode;

    private Long planId;

    private Long equipmentId;

    private Long templateId;

    private LocalDate planDate;

    private Long assigneeId;

    /** PENDING / IN_PROGRESS / SUBMITTED / EXPIRED */
    private String status;

    /** AUTO 计划生成 / MANUAL 手工发起 */
    private String source;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private LocalDateTime finishTime;

    /** NORMAL / ABNORMAL */
    private String result;

    private Integer abnormalCount;

    private LocalDateTime slaDueTime;

    private Integer overdueFlag;

    /** 是否扫码核验过设备二维码 */
    private Integer qrVerified;

    private String remark;
}
