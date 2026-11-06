package com.equipcare.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.common.constant.CommonConstants;
import com.equipcare.system.entity.SysRole;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.entity.SysUserRole;
import com.equipcare.system.mapper.SysRoleMapper;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.system.mapper.SysUserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始管理员一次性创建。
 *
 * <p>启用方式（仅首次部署执行一次，密码由部署人自行设定）：
 * <pre>
 * java -jar equipcare-server.jar \
 *   --equipcare.bootstrap.enabled=true \
 *   --equipcare.bootstrap.username=admin \
 *   --equipcare.bootstrap.password=&lt;YOUR_STRONG_PASSWORD&gt;
 * </pre>
 *
 * <p>幂等：用户名已存在时直接跳过，不会覆盖既有账号与密码。
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final BootstrapProperties properties;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(BootstrapProperties properties, SysUserMapper userMapper,
                                SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        if (!properties.isEnabled()) {
            return;
        }
        String username = properties.getUsername();
        String password = properties.getPassword();
        if (username == null || username.isBlank() || password == null || password.length() < 8) {
            log.error("初始管理员创建失败：请提供用户名与至少 8 位密码");
            return;
        }
        if (userMapper.countByUsernameIncludeDeleted(username) > 0) {
            log.warn("初始管理员已存在，跳过创建：{}", username);
            return;
        }
        SysRole adminRole = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, CommonConstants.ROLE_ADMIN));
        if (adminRole == null) {
            log.error("初始管理员创建失败：数据库缺少 ADMIN 角色，请先执行 seed.sql");
            return;
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(properties.getRealName());
        user.setStatus(1);
        userMapper.insert(user);

        SysUserRole relation = new SysUserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(adminRole.getId());
        userRoleMapper.insert(relation);

        log.warn("已创建初始管理员 {}（id={}），请立即登录并修改密码；"
                + "后续启动请移除 --equipcare.bootstrap.enabled 参数", username, user.getId());
    }
}
