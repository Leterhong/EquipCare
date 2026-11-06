package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模板检查项（模板详情 / 执行页共用）。
 */
@Data
@Schema(description = "模板检查项")
public class TemplateItemVO {

    private Long id;

    private Integer seqNo;

    private String itemName;

    @Schema(description = "NUMBER / ENUM / BOOLEAN / TEXT")
    private String itemType;

    private String unit;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    private String normalValue;

    @Schema(description = "枚举选项 JSON 字符串")
    private String enumOptions;

    private Integer required;

    private String checkMethod;

    private String standardRef;

    private String abnormalRule;

    private Integer autoWorkOrder;

    private Integer photoRequired;

    @Schema(description = "CRITICAL / HIGH / MEDIUM / LOW")
    private String abnormalLevel;

    private LocalDateTime updateTime;
}
