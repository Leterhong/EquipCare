package com.equipcare.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.equipcare.system.dto.UserQuery;
import com.equipcare.system.dto.UserRoleRef;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.vo.UserOptionVO;
import com.equipcare.system.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 查询用户的角色编码（仅启用角色） */
    @Select("""
            SELECT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 1
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /** 查询用户的有效权限码集合 */
    @Select("""
            SELECT DISTINCT p.perm_code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND p.deleted = 0 AND p.status = 1
              AND r.deleted = 0 AND r.status = 1
            """)
    List<String> selectPermCodes(@Param("userId") Long userId);

    /** 用户名是否已存在（包含已逻辑删除的账号，用于规避唯一索引冲突） */
    @Select("SELECT COUNT(1) FROM sys_user WHERE username = #{username}")
    int countByUsernameIncludeDeleted(@Param("username") String username);

    /** 手机号是否已被使用 */
    @Select("SELECT COUNT(1) FROM sys_user WHERE phone = #{phone} AND deleted = 0")
    int countByPhone(@Param("phone") String phone);

    /** 按角色编码查询启用用户（库存预警 / SLA 预警的收件人解析） */
    @Select("""
            SELECT u.id
              FROM sys_user u
             WHERE u.status = 1 AND u.deleted = 0
               AND EXISTS (SELECT 1 FROM sys_user_role ur
                             JOIN sys_role r ON r.id = ur.role_id
                            WHERE ur.user_id = u.id
                              AND r.role_code = #{roleCode}
                              AND r.deleted = 0 AND r.status = 1)
            """)
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /** 用户分页（含部门名称） */
    @Select("""
            <script>
            SELECT u.id, u.username, u.real_name, u.phone, u.email, u.dept_id, d.dept_name,
                   u.skill_tags, u.status, u.last_login_time, u.create_time
            FROM sys_user u
            LEFT JOIN sys_dept d ON d.id = u.dept_id
            WHERE u.deleted = 0
            <if test="q.keyword != null and q.keyword != ''">
              AND (u.username LIKE CONCAT('%', #{q.keyword}, '%')
                   OR u.real_name LIKE CONCAT('%', #{q.keyword}, '%')
                   OR u.phone LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.deptId != null"> AND u.dept_id = #{q.deptId} </if>
            <if test="q.status != null"> AND u.status = #{q.status} </if>
            ORDER BY u.id DESC
            </script>
            """)
    IPage<UserVO> selectUserPage(IPage<UserVO> page, @Param("q") UserQuery query);

    /** 批量查询用户的角色，避免 N+1 */
    @Select("""
            <script>
            SELECT ur.user_id, r.id AS role_id, r.role_name
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id IN
            <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            ORDER BY r.sort ASC
            </script>
            """)
    List<UserRoleRef> selectRolesByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 责任人候选项：仅返回 id / 姓名 / 部门名，最多 50 条。
     * <p>用于设备台账选择责任人等场景，避免为此开放完整的用户管理权限。
     */
    @Select("""
            <script>
            SELECT u.id, u.real_name, d.dept_name
            FROM sys_user u
            LEFT JOIN sys_dept d ON d.id = u.dept_id
            WHERE u.deleted = 0 AND u.status = 1
            <if test="keyword != null and keyword != ''">
              AND u.real_name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY u.id ASC
            LIMIT 50
            </script>
            """)
    List<UserOptionVO> selectOptions(@Param("keyword") String keyword);
}
