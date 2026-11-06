package com.equipcare.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.query.GroupCount;
import com.equipcare.common.result.ResultCode;
import com.equipcare.common.security.TokenStore;
import com.equipcare.system.dto.RoleSaveDTO;
import com.equipcare.system.entity.SysPermission;
import com.equipcare.system.entity.SysRole;
import com.equipcare.system.entity.SysRolePermission;
import com.equipcare.system.entity.SysUserRole;
import com.equipcare.system.mapper.SysPermissionMapper;
import com.equipcare.system.mapper.SysRoleMapper;
import com.equipcare.system.mapper.SysRolePermissionMapper;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.system.mapper.SysUserRoleMapper;
import com.equipcare.system.service.RoleService;
import com.equipcare.system.vo.PermissionTreeVO;
import com.equipcare.system.vo.PermissionVO;
import com.equipcare.system.vo.RoleVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色权限实现。
 *
 * <p>权限变更后清除受影响用户的会话缓存，保证权限即时生效。
 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);
    private static final String BUILTIN_ADMIN = "ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final TokenStore tokenStore;

    public RoleServiceImpl(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                           SysRolePermissionMapper rolePermissionMapper, SysUserRoleMapper userRoleMapper,
                           SysUserMapper userMapper, TokenStore tokenStore) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.tokenStore = tokenStore;
    }

    @Override
    public List<RoleVO> list() {
        List<SysRole> roles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByAsc(SysRole::getSort));
        Map<Long, Long> userCounts = new HashMap<>();
        for (GroupCount count : roleMapper.countUserGroupByRole()) {
            userCounts.put(count.getId(), count.getTotal());
        }
        List<RoleVO> result = new ArrayList<>();
        for (SysRole role : roles) {
            RoleVO vo = toVO(role, null);
            vo.setUserCount(userCounts.getOrDefault(role.getId(), 0L).intValue());
            result.add(vo);
        }
        return result;
    }

    @Override
    public RoleVO detail(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色");
        }
        return toVO(role, permissionMapper.selectPermissionIdsByRole(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleSaveDTO dto) {
        long exists = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, dto.getRoleCode()));
        if (exists > 0) {
            throw BizException.duplicate("角色编码 " + dto.getRoleCode());
        }
        SysRole role = new SysRole();
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setDataScope(dto.getDataScope());
        role.setBuiltin(0);
        role.setSort(dto.getSort() == null ? 99 : dto.getSort());
        role.setStatus(1);
        role.setRemark(dto.getRemark());
        roleMapper.insert(role);
        log.info("新增角色 id={} code={}", role.getId(), role.getRoleCode());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleSaveDTO dto) {
        SysRole exists = roleMapper.selectById(id);
        if (exists == null) {
            throw BizException.notFound("角色");
        }
        if (!exists.getRoleCode().equals(dto.getRoleCode())) {
            throw new BizException(ResultCode.OPERATION_FORBIDDEN, "角色编码不允许修改");
        }
        SysRole update = new SysRole();
        update.setId(id);
        update.setRoleName(dto.getRoleName());
        update.setDataScope(dto.getDataScope());
        update.setSort(dto.getSort());
        update.setRemark(dto.getRemark());
        roleMapper.updateById(update);
        evictUsersOfRole(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw BizException.notFound("角色");
        }
        if (BUILTIN_ADMIN.equals(role.getRoleCode())) {
            throw BizException.forbidden("内置管理员权限不允许修改");
        }
        List<Long> distinctIds = permissionIds == null ? List.of() : permissionIds.stream().distinct().toList();
        if (!distinctIds.isEmpty()) {
            long valid = permissionMapper.selectCount(Wrappers.<SysPermission>lambdaQuery()
                    .in(SysPermission::getId, distinctIds));
            if (valid != distinctIds.size()) {
                throw new BizException(ResultCode.VALIDATION_FAILED, "存在无效的权限ID");
            }
        }
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : distinctIds) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        evictUsersOfRole(roleId);
        log.info("角色权限变更 roleId={} 权限数={}", roleId, distinctIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色");
        }
        if (Integer.valueOf(1).equals(role.getBuiltin())) {
            throw BizException.forbidden("内置角色不允许删除");
        }
        int users = permissionMapper.countUsersOfRole(id);
        if (users > 0) {
            throw BizException.inUse("该角色下仍有 " + users + " 个用户，请先调整这些用户的角色");
        }
        // 先清理关联，再逻辑删除；角色编码追加后缀，释放编码以便日后重建同名角色
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, id));
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, id));
        roleMapper.markDeleted(id, role.getRoleCode() + "_DEL_" + id);
        log.info("删除角色 id={} code={}", id, role.getRoleCode());
    }

    @Override
    public List<PermissionVO> permissions() {
        return permissionMapper.selectAllWithRoleCount();
    }

    @Override
    public List<PermissionTreeVO> permissionTree() {
        List<SysPermission> permissions = permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .orderByAsc(SysPermission::getSort));
        Map<Long, PermissionTreeVO> byId = new LinkedHashMap<>();
        for (SysPermission permission : permissions) {
            PermissionTreeVO node = new PermissionTreeVO();
            node.setId(permission.getId());
            node.setParentId(permission.getParentId());
            node.setCode(permission.getPermCode());
            node.setName(permission.getPermName());
            node.setType(permission.getPermType());
            node.setPath(permission.getPath());
            node.setSort(permission.getSort());
            byId.put(permission.getId(), node);
        }
        List<PermissionTreeVO> roots = new ArrayList<>();
        for (PermissionTreeVO node : byId.values()) {
            PermissionTreeVO parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private void evictUsersOfRole(Long roleId) {
        List<SysUserRole> relations = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, roleId));
        for (SysUserRole relation : relations) {
            tokenStore.removeAllByUser(relation.getUserId());
        }
    }

    private RoleVO toVO(SysRole role, List<Long> permissionIds) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setBuiltin(role.getBuiltin());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setPermissionIds(permissionIds);
        return vo;
    }
}
