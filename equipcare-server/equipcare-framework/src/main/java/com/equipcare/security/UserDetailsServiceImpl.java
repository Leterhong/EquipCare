package com.equipcare.security;
import com.equipcare.common.security.LoginUser;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.system.entity.SysUser;
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

    public UserDetailsServiceImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
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
        // 多租户（第十期）：装载用户所属租户（所有既有用户 tenant_id=0，零功能变化）
        loginUser.setTenantId(user.getTenantId());
        return loginUser;
    }
}
