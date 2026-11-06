package com.equipcare.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.common.security.TokenStore;
import com.equipcare.system.dto.UserCreateDTO;
import com.equipcare.system.dto.UserQuery;
import com.equipcare.system.dto.UserRoleRef;
import com.equipcare.system.dto.UserUpdateDTO;
import com.equipcare.system.entity.SysRole;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.entity.SysUserRole;
import com.equipcare.system.mapper.SysRoleMapper;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.system.mapper.SysUserRoleMapper;
import com.equipcare.system.service.UserService;
import com.equipcare.system.vo.UserOptionVO;
import com.equipcare.system.vo.UserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理实现。
 *
 * <p>关键规则：
 * <ul>
 *   <li>登录名唯一（含已删除账号占位），删除时把登录名改为 {@code 原名_del_主键} 释放占用；</li>
 *   <li>禁止删除或禁用自己，避免把系统锁死；</li>
 *   <li>禁用与重置密码都会强制下线该用户全部会话。</li>
 * </ul>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public UserServiceImpl(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                           SysRoleMapper roleMapper, PasswordEncoder passwordEncoder, TokenStore tokenStore) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        IPage<UserVO> page = userMapper.selectUserPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        List<UserVO> records = page.getRecords();
        if (!records.isEmpty()) {
            List<Long> ids = records.stream().map(UserVO::getId).toList();
            List<UserRoleRef> refs = userMapper.selectRolesByUserIds(ids);
            Map<Long, List<UserRoleRef>> grouped = refs.stream()
                    .collect(Collectors.groupingBy(UserRoleRef::getUserId));
            for (UserVO vo : records) {
                List<UserRoleRef> userRoles = grouped.getOrDefault(vo.getId(), List.of());
                vo.setRoleIds(userRoles.stream().map(UserRoleRef::getRoleId).toList());
                vo.setRoleNames(userRoles.stream().map(UserRoleRef::getRoleName).toList());
            }
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public UserVO detail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户");
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setSkillTags(user.getSkillTags());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        List<SysUserRole> relations = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, id));
        vo.setRoleIds(relations.stream().map(SysUserRole::getRoleId).toList());
        if (!relations.isEmpty()) {
            List<SysRole> roles = roleMapper.selectBatchIds(vo.getRoleIds());
            vo.setRoleNames(roles.stream().map(SysRole::getRoleName).toList());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateDTO dto) {
        String username = dto.getUsername().trim();
        if (userMapper.countByUsernameIncludeDeleted(username) > 0) {
            throw BizException.duplicate("登录名 " + username);
        }
        String phone = normalizeBlank(dto.getPhone());
        if (phone != null && userMapper.countByPhone(phone) > 0) {
            throw BizException.duplicate("手机号 " + phone);
        }
        validateRoles(dto.getRoleIds());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName().trim());
        user.setPhone(phone);
        user.setEmail(normalizeBlank(dto.getEmail()));
        user.setDeptId(dto.getDeptId());
        user.setSkillTags(normalizeBlank(dto.getSkillTags()));
        user.setStatus(1);
        userMapper.insert(user);
        replaceRoles(user.getId(), dto.getRoleIds());
        log.info("新增用户 id={} username={}", user.getId(), username);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpdateDTO dto) {
        SysUser exists = userMapper.selectById(id);
        if (exists == null) {
            throw BizException.notFound("用户");
        }
        String phone = normalizeBlank(dto.getPhone());
        if (phone != null && !phone.equals(exists.getPhone()) && userMapper.countByPhone(phone) > 0) {
            throw BizException.duplicate("手机号 " + phone);
        }
        validateRoles(dto.getRoleIds());

        SysUser update = new SysUser();
        update.setId(id);
        update.setRealName(dto.getRealName().trim());
        update.setPhone(phone);
        update.setEmail(normalizeBlank(dto.getEmail()));
        update.setDeptId(dto.getDeptId());
        update.setSkillTags(normalizeBlank(dto.getSkillTags()));
        userMapper.updateById(update);
        replaceRoles(id, dto.getRoleIds());
        // 角色变更后刷新该用户权限缓存，使其下次请求生效
        tokenStore.removeAllByUser(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.BAD_REQUEST, "状态只能是 0 或 1");
        }
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw BizException.forbidden("不能修改自己的账号状态");
        }
        SysUser exists = userMapper.selectById(id);
        if (exists == null) {
            throw BizException.notFound("用户");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        if (status == 0) {
            tokenStore.removeAllByUser(id);
            log.info("用户被禁用并强制下线 id={}", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        SysUser exists = userMapper.selectById(id);
        if (exists == null) {
            throw BizException.notFound("用户");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        tokenStore.removeAllByUser(id);
        log.info("管理员重置用户密码 id={}，已强制下线", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw BizException.forbidden("不能删除当前登录账号");
        }
        SysUser exists = userMapper.selectById(id);
        if (exists == null) {
            throw BizException.notFound("用户");
        }
        // 先改名释放登录名唯一约束，再逻辑删除
        SysUser rename = new SysUser();
        rename.setId(id);
        rename.setUsername(exists.getUsername() + "_del_" + id);
        userMapper.updateById(rename);
        userMapper.deleteById(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        tokenStore.removeAllByUser(id);
        log.info("删除用户 id={} username={}", id, exists.getUsername());
    }

    @Override
    public List<UserOptionVO> options(String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        return userMapper.selectOptions(kw == null || kw.isEmpty() ? null : kw);
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        List<SysUserRole> relations = new ArrayList<>();
        for (Long roleId : roleIds.stream().distinct().toList()) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relations.add(relation);
        }
        for (SysUserRole relation : relations) {
            userRoleMapper.insert(relation);
        }
    }

    private void validateRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BizException(ResultCode.VALIDATION_FAILED, "至少选择一个角色");
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds.stream().distinct().toList());
        if (roles.size() != roleIds.stream().distinct().count()) {
            throw new BizException(ResultCode.VALIDATION_FAILED, "存在无效的角色ID");
        }
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
