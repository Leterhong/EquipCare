package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.LogEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 维修过程记录。只允许插入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wo_repair_record")
public class WoRepairRecord extends LogEntity {

    private Long workOrderId;

    /** START / DIAGNOSIS / PROCESS / FINISH / OTHER */
    private String recordType;

    private String content;

    /** 维修前照片文件ID，逗号分隔 */
    private String beforeImageIds;

    /** 维修后照片文件ID，逗号分隔 */
    private String afterImageIds;

    private Integer costMinutes;

    /** 附件ID列表，逗号分隔 */
    private String attachmentIds;

    private Long operatorId;
}
