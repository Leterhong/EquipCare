package com.equipcare.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色权限分配（覆盖式）。
 */
@Data
@Schema(description = "角色权限分配请求")
public class AssignPermissionDTO {

    @Schema(description = "权限ID列表，传空数组表示清空", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "权限列表不能为空")
    private List<Long> permissionIds;
}
