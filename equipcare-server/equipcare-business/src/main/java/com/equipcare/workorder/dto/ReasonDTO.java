package com.equipcare.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 带原因的动作请求：拒接、挂起、撤回、取消。
 * <p>各动作的原因最小长度由状态机规则校验，此处只做非空与长度上限约束。
 */
@Data
@Schema(description = "带原因的动作请求")
public class ReasonDTO {

    @Schema(description = "原因说明", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写原因")
    @Size(max = 300, message = "原因长度不能超过 300")
    private String reason;
}
