package com.equipcare.security;
import com.equipcare.common.security.LoginUser;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.system.entity.SysTenant;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysTenantMapper;
import com.equipcare.system.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从数据库加载用户、角色与权限，构建登录主体。
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;

    public UserDetailsServiceImpl(SysUserMapper userMapper, SysTenantMapper tenantMapper) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        List<String> permissions = userMapper.selectPermCodes(user.getId());
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getPassword(), user.getRealName(),
                user.getDeptId(), user.getStatus(), roles, permissions);
        // 多租户（第十期第三步）：装载用户所属租户；租户被停用则拒绝登录
        loginUser.setTenantId(user.getTenantId());
        if (user.getTenantId() != null && user.getTenantId() != 0) {
            SysTenant tenant = tenantMapper.selectById(user.getTenantId());
            if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
                throw new UsernameNotFoundException("所属租户已停用: " + username);
            }
        }
        return loginUser;
    }
}
