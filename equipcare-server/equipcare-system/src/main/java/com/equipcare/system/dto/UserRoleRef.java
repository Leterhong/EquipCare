package com.equipcare.system.dto;

import lombok.Data;

/**
 * 用户-角色关联投影，用于批量回填角色，避免 N+1 查询。
 */
@Data
public class UserRoleRef {

    private Long userId;

    private Long roleId;

    private String roleName;
}
