package com.equipcare.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.common.query.GroupCount;
import com.equipcare.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT r.*
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0
            ORDER BY r.sort ASC
            """)
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    /** 各角色下的有效用户数，用于角色列表展示与删除保护 */
    @Select("""
            SELECT ur.role_id AS id, COUNT(1) AS total
            FROM sys_user_role ur
            INNER JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0
            GROUP BY ur.role_id
            """)
    List<GroupCount> countUserGroupByRole();

    /**
     * 逻辑删除角色并重命名角色编码。
     * <p>{@code uk_sys_role_code} 是普通唯一索引，逻辑删除后原编码仍占用，
     * 因此删除时追加后缀释放编码，便于日后重建同名角色。
     */
    @Update("""
            UPDATE sys_role
            SET deleted = 1, role_code = #{roleCode}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int markDeleted(@Param("id") Long id, @Param("roleCode") String roleCode);
}
