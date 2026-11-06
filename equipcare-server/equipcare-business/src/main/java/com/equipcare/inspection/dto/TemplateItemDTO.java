package com.equipcare.inspection.dto;

import com.equipcare.common.validation.EnumValue;
import com.equipcare.inspection.enums.AnomalyLevel;
import com.equipcare.inspection.enums.InspItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 巡检模板检查项。
 *
 * <p>数值型靠 min/max 做机器判定，枚举/布尔型靠 normalValue 与人工判定，
 * abnormalRule 是给人看的判定口径说明（会打印在执行页与档案上）。
 */
@Data
@Schema(description = "巡检模板检查项")
public class TemplateItemDTO {

    @Schema(description = "检查项名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "检查设备外壳温度")
    @NotBlank(message = "检查项名称不能为空")
    @Size(max = 100, message = "检查项名称不能超过 100 字")
    private String itemName;

    @Schema(description = "类型：NUMBER/ENUM/BOOLEAN/TEXT", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "检查项类型不能为空")
    @EnumValue(target = InspItemType.class, message = "检查项类型取值非法")
    private String itemType;

    @Schema(description = "单位，如 ℃ / MPa")
    @Size(max = 20, message = "单位过长")
    private String unit;

    @Schema(description = "数值下限（NUMBER 类型）")
    @DecimalMin(value = "-999999999", message = "数值下限超出范围")
    @DecimalMax(value = "999999999", message = "数值下限超出范围")
    private BigDecimal minValue;

    @Schema(description = "数值上限（NUMBER 类型）")
    @DecimalMin(value = "-999999999", message = "数值上限超出范围")
    @DecimalMax(value = "999999999", message = "数值上限超出范围")
    private BigDecimal maxValue;

    @Schema(description = "正常值（ENUM/BOOLEAN/文本型参考值）")
    @Size(max = 50, message = "正常值过长")
    private String normalValue;

    @Schema(description = "枚举选项 JSON，如 [\"正常\",\"异响\"]")
    @Size(max = 500, message = "枚举选项过长")
    private String enumOptions;

    @Schema(description = "是否必检；必检项不允许跳过", example = "1")
    @Min(value = 0, message = "必填标记取值非法")
    @Max(value = 1, message = "必填标记取值非法")
    private Integer required = 1;

    @Schema(description = "检查方法")
    @Size(max = 200, message = "检查方法过长")
    private String checkMethod;

    @Schema(description = "检查标准")
    @Size(max = 200, message = "检查标准过长")
    private String standardRef;

    @Schema(description = "异常判定规则")
    @Size(max = 200, message = "异常判定规则过长")
    private String abnormalRule;

    @Schema(description = "异常时是否自动转工单")
    @Min(value = 0, message = "自动转单标记取值非法")
    @Max(value = 1, message = "自动转单标记取值非法")
    private Integer autoWorkOrder = 0;

    @Schema(description = "异常时是否必须上传现场照片")
    @Min(value = 0, message = "现场照片标记取值非法")
    @Max(value = 1, message = "现场照片标记取值非法")
    private Integer photoRequired = 0;

    @Schema(description = "异常等级：CRITICAL/HIGH/MEDIUM/LOW")
    @EnumValue(target = AnomalyLevel.class, message = "异常等级取值非法")
    private String abnormalLevel;

    @Schema(description = "排序号；不传按提交顺序生成")
    @Min(value = 1, message = "排序号不能小于 1")
    @Max(value = 999, message = "排序号不能大于 999")
    private Integer seqNo;
}
