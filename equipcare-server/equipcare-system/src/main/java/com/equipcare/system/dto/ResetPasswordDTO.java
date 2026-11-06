package com.equipcare.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员重置用户密码。
 */
@Data
@Schema(description = "重置密码请求")
public class ResetPasswordDTO {

    @Schema(description = "新密码：8-32 位，含字母与数字", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "密码需 8-32 位且同时包含字母与数字")
    private String newPassword;
}
