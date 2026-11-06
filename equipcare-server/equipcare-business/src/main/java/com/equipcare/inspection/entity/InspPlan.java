package com.equipcare.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 巡检计划。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("insp_plan")
public class InspPlan extends BaseEntity {

    private String planCode;

    private String planName;

    private Long templateId;

    /** CATEGORY / LOCATION / EQUIPMENT */
    private String scopeType;

    private String scopeConfig;

    /** FIXED 固定人员 / ROTATE 轮值 / DEPARTMENT 按部门 */
    private String executorStrategy;

    private String executorIds;

    /** DAILY / WEEKLY / MONTHLY / CRON */
    private String cycleType;

    private String cycleConfig;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer slaMinutes;

    private Integer generateAheadHours;

    /** ENABLED / DISABLED */
    private String status;

    private LocalDateTime nextGenerateTime;

    private LocalDateTime lastGenerateTime;

    private Long creatorId;

    @TableLogic
    private Integer deleted;
}
