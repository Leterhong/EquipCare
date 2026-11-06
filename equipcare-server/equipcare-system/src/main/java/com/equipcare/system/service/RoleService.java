package com.equipcare.system.service;

import com.equipcare.system.dto.RoleSaveDTO;
import com.equipcare.system.vo.PermissionTreeVO;
import com.equipcare.system.vo.PermissionVO;
import com.equipcare.system.vo.RoleVO;

import java.util.List;

/**
 * 角色与权限管理服务。
 */
public interface RoleService {

    List<RoleVO> list();

    RoleVO detail(Long id);

    Long create(RoleSaveDTO dto);

    void update(Long id, RoleSaveDTO dto);

    /** 覆盖式分配权限；内置 ADMIN 角色禁止修改 */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 删除角色。
     * <p>内置角色、仍有用户占用的角色不允许删除；删除时同步清理角色-权限关联。
     */
    void delete(Long id);

    /** 全量权限清单（含已授权角色数），供权限管理页面只读展示 */
    List<PermissionVO> permissions();

    /** 全量权限树 */
    List<PermissionTreeVO> permissionTree();
}
