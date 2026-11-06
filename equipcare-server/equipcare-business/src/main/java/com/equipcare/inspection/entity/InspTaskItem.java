package com.equipcare.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检任务明细（逐项检查结果）。
 */
@Data
@TableName("insp_task_item")
public class InspTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long templateItemId;

    private String itemName;

    private String itemType;

    private String itemValue;

    /** NORMAL / ABNORMAL / SKIPPED */
    private String result;

    private Integer isAbnormal;

    private String abnormalLevel;

    /** 说明 / 异常原因 */
    private String remark;

    /** 跳过原因，仅 SKIPPED 填写 */
    private String skipReason;

    private String attachmentIds;

    private LocalDateTime createTime;
}
