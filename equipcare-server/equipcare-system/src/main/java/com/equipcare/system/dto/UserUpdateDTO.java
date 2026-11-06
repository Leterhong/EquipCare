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
 * 编辑用户。登录名不可修改。
 */
@Data
@Schema(description = "编辑用户请求")
public class UserUpdateDTO {

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

    @Schema(description = "技能标签")
    @Size(max = 200, message = "技能标签过长")
    private String skillTags;
}
