package com.equipcare.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/编辑角色。
 */
@Data
@Schema(description = "角色保存请求")
public class RoleSaveDTO {

    @Schema(description = "角色编码，创建后不可修改", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,29}$", message = "角色编码需大写字母开头，3-30 位")
    private String roleCode;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称过长")
    private String roleName;

    @Schema(description = "数据范围：ALL / DEPT / SELF", requiredMode = Schema.RequiredMode.REQUIRED)
    @Pattern(regexp = "^(ALL|DEPT|SELF)$", message = "数据范围只能是 ALL / DEPT / SELF")
    private String dataScope;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 200, message = "备注过长")
    private String remark;
}
