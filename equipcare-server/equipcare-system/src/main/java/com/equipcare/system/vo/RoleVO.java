package com.equipcare.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 角色展示对象。
 */
@Data
@Schema(description = "角色信息")
public class RoleVO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String dataScope;

    private Integer builtin;

    private Integer sort;

    private Integer status;

    private String remark;

    @Schema(description = "已分配权限ID（仅详情返回）")
    private List<Long> permissionIds;

    @Schema(description = "该角色下的用户数")
    private Integer userCount;
}
