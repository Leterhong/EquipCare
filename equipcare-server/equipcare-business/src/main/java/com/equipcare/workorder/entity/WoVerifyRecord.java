package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 验收记录。只允许插入。
 *
 * <p>本表以 {@code verify_time} 记录发生时间，没有 {@code create_time}，
 * 因此不继承 {@link com.equipcare.common.domain.LogEntity}。
 */
@Data
@TableName("wo_verify_record")
public class WoVerifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    private Long verifyUserId;

    /** PASS / REJECT */
    private String verifyResult;

    private Integer qualityScore;

    private String remark;

    private String attachmentIds;

    private LocalDateTime verifyTime;
}
