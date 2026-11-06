package com.equipcare.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 巡检模板检查项。异常项可配置「自动转工单」与异常等级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("insp_template_item")
public class InspTemplateItem extends BaseEntity {

    private Long templateId;

    private Integer seqNo;

    private String itemName;

    /** NUMBER / ENUM / BOOLEAN / TEXT */
    private String itemType;

    private String unit;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    private String normalValue;

    private String enumOptions;

    private Integer required;

    private String checkMethod;

    /** 检查标准/标准依据 */
    private String standardRef;

    /** 异常判定规则说明：数值区间之外的人工判定口径 */
    private String abnormalRule;

    /** 判为异常时是否自动生成工单 */
    private Integer autoWorkOrder;

    /** 判为异常时是否必须上传现场照片 */
    private Integer photoRequired;

    /** HIGH / CRITICAL / MEDIUM / LOW */
    private String abnormalLevel;
}
