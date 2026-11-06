package com.equipcare.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计划与设备的关联（显式指定设备范围的计划使用）。
 */
@Data
@TableName("insp_plan_equipment")
public class InspPlanEquipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long equipmentId;

    private Integer seqNo;

    private Long assigneeId;

    private LocalDateTime createTime;
}
