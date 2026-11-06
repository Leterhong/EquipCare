package com.equipcare.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提交验收。提交前系统会校验四项前置条件，此处只收集维修总结与工时。
 */
@Data
@Schema(description = "提交验收请求")
public class SubmitVerifyDTO {

    @Schema(description = "维修总结", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "维修总结不能为空")
    @Size(max = 1000, message = "维修总结长度不能超过 1000")
    private String repairSummary;

    @Schema(description = "工时（小时）")
    @DecimalMin(value = "0.0", message = "工时不能为负数")
    private BigDecimal laborHours;
}
