package com.equipcare.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.system.entity.SysPermission;
import com.equipcare.system.vo.PermissionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 用户可访问的菜单（去重、按排序号升序） */
    @Select("""
            SELECT DISTINCT p.*
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND p.perm_type = 'MENU'
              AND p.deleted = 0 AND p.status = 1 AND p.visible = 1
              AND r.deleted = 0 AND r.status = 1
            ORDER BY p.sort ASC, p.id ASC
            """)
    List<SysPermission> selectMenusByUserId(@Param("userId") Long userId);

    /** 角色已分配的权限ID */
    @Select("""
            SELECT rp.permission_id
            FROM sys_role_permission rp
            WHERE rp.role_id = #{roleId}
            """)
    List<Long> selectPermissionIdsByRole(@Param("roleId") Long roleId);

    /** 全部权限 + 已授权角色数，供权限管理页面展示 */
    @Select("""
            SELECT p.id, p.parent_id, p.perm_code AS code, p.perm_name AS name, p.perm_type AS type,
                   p.path, p.component, p.icon, p.sort, p.visible, p.status,
                   (SELECT COUNT(1) FROM sys_role_permission rp
                      INNER JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0
                     WHERE rp.permission_id = p.id) AS role_count
            FROM sys_permission p
            WHERE p.deleted = 0
            ORDER BY p.sort ASC, p.id ASC
            """)
    List<PermissionVO> selectAllWithRoleCount();

    /** 某角色下的用户数，用于角色删除保护 */
    @Select("""
            SELECT COUNT(1)
            FROM sys_user_role ur
            INNER JOIN sys_user u ON u.id = ur.user_id
            WHERE ur.role_id = #{roleId} AND u.deleted = 0
            """)
    int countUsersOfRole(@Param("roleId") Long roleId);
}
