package com.equipcare.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增用户。
 */
@Data
@Schema(description = "新增用户请求")
public class UserCreateDTO {

    @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,29}$",
            message = "登录名需以字母开头，4-30 位字母/数字/下划线")
    private String username;

    @Schema(description = "初始密码：8-32 位，含字母与数字", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "初始密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "密码需 8-32 位且同时包含字母与数字")
    private String password;

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @Schema(description = "手机号")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "角色ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少选择一个角色")
    private List<Long> roleIds;

    @Schema(description = "技能标签，逗号分隔")
    @Size(max = 200, message = "技能标签过长")
    private String skillTags;
}
