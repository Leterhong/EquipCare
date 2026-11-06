package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 巡检检查项（模板标准 + 已填写结果）。
 */
@Data
@Schema(description = "巡检检查项")
public class TaskItemVO {

    @Schema(description = "任务项ID；未填写时为空")
    private Long id;

    private Long templateItemId;

    private String itemName;

    @Schema(description = "NUMBER / ENUM / BOOLEAN / TEXT")
    private String itemType;

    private String unit;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    @Schema(description = "枚举可选值，JSON 数组字符串")
    private String enumOptions;

    @Schema(description = "是否项的「正常值」（0/1），执行页据此提示巡检人")
    private String normalValue;

    @Schema(description = "是否必填")
    private Integer required;

    private String checkMethod;

    @Schema(description = "检查标准")
    private String standardRef;

    @Schema(description = "异常判定规则")
    private String abnormalRule;

    @Schema(description = "异常时是否必须上传现场照片")
    private Integer photoRequired;

    private String itemValue;

    @Schema(description = "单项结果：NORMAL / ABNORMAL / SKIPPED")
    private String result;

    private Integer isAbnormal;

    private String abnormalLevel;

    private String remark;

    @Schema(description = "跳过原因")
    private String skipReason;

    private String attachmentIds;

    @Schema(description = "是否配置为异常自动转工单")
    private Integer autoWorkOrder;
}
