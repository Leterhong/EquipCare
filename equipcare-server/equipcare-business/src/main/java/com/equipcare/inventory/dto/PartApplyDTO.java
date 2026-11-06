package com.equipcare.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工单备件申请。
 */
@Data
@Schema(description = "备件申请请求")
public class PartApplyDTO {

    @Schema(description = "工单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工单不能为空")
    private Long workOrderId;

    @Schema(description = "备件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "备件不能为空")
    private Long partId;

    @Schema(description = "申请数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;

    @Schema(description = "备注")
    @Size(max = 300, message = "备注长度不能超过 300")
    private String remark;
}
