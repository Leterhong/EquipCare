package com.equipcare.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.LogEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单服务评价。一张工单只允许一条（唯一索引 {@code uk_wo_eval_order}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wo_evaluation")
public class WoEvaluation extends LogEntity {

    private Long workOrderId;

    private Long evaluatorId;

    /** 服务态度 1-5 */
    private Integer attitudeScore;

    /** 维修质量 1-5 */
    private Integer qualityScore;

    /** 响应速度 1-5 */
    private Integer speedScore;

    private String content;
}
