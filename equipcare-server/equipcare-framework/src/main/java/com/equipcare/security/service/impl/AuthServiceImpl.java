package com.equipcare.security.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import com.equipcare.security.JwtTokenProvider;
import com.equipcare.common.security.LoginUser;
import com.equipcare.security.SecurityProperties;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.common.security.TokenStore;
import com.equipcare.security.UserDetailsServiceImpl;
import com.equipcare.security.dto.ChangePasswordRequest;
import com.equipcare.security.dto.LoginRequest;
import com.equipcare.security.dto.ProfileUpdateRequest;
import com.equipcare.security.service.AuthService;
import com.equipcare.security.vo.LoginVO;
import com.equipcare.security.vo.MenuVO;
import com.equipcare.security.vo.UserInfoVO;
import com.equipcare.system.entity.SysDept;
import com.equipcare.system.entity.SysPermission;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysDeptMapper;
import com.equipcare.system.mapper.SysPermissionMapper;
import com.equipcare.system.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务实现。
 *
 * <p>安全要点：
 * <ul>
 *   <li>用户不存在与密码错误返回同一提示，避免账号枚举；</li>
 *   <li>连续失败 {@code loginMaxFail} 次锁定账号 {@code lockMinutes} 分钟；</li>
 *   <li>登出与改密后令牌立即失效（黑名单 + 会话清除）；</li>
 *   <li>修改密码强制下线该用户全部会话。</li>
 * </ul>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenStore tokenStore;
    private final SecurityProperties securityProperties;
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysDeptMapper deptMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsServiceImpl userDetailsService;
    /** 安全告警通知（可选组件，实现位于 notification 模块的外发通道） */
    private final org.springframework.beans.factory.ObjectProvider<com.equipcare.common.security.SecurityAlertNotifier> securityAlertNotifier;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenProvider tokenProvider,
                           TokenStore tokenStore,
                           SecurityProperties securityProperties,
                           SysUserMapper userMapper,
                           SysPermissionMapper permissionMapper,
                           SysDeptMapper deptMapper,
                           PasswordEncoder passwordEncoder,
                           UserDetailsServiceImpl userDetailsService,
                           org.springframework.beans.factory.ObjectProvider<com.equipcare.common.security.SecurityAlertNotifier> securityAlertNotifier) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.tokenStore = tokenStore;
        this.securityProperties = securityProperties;
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.deptMapper = deptMapper;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.securityAlertNotifier = securityAlertNotifier;
    }

    /** 锁定告警：尽力而为，失败只记日志（安全告警不得影响登录主流程） */
    private void notifyAccountLocked(String username, String clientIp, int maxFail) {
        com.equipcare.common.security.SecurityAlertNotifier notifier = securityAlertNotifier.getIfAvailable();
        if (notifier == null) {
            return;
        }
        try {
            notifier.onAccountLocked(username, clientIp, maxFail);
        } catch (Exception e) {
            log.warn("账号锁定安全告警发送失败 username={}", username, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request, String clientIp) {
        String username = request.getUsername().trim();
        // Redis 故障降级：失败计数暂不可用时按 0 处理（锁定策略暂失效），登录流程继续
        long failCount = tokenStore.getLoginFailSafe(username);
        if (failCount >= securityProperties.getLoginMaxFail()) {
            throw new BizException(ResultCode.AUTH_ACCOUNT_LOCKED,
                    "账号已锁定，请 " + securityProperties.getLockMinutes() + " 分钟后重试");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (DisabledException ex) {
            throw new BizException(ResultCode.AUTH_ACCOUNT_DISABLED);
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            long current = tokenStore.increaseLoginFailSafe(username,
                    Duration.ofMinutes(securityProperties.getLockMinutes()));
            long remain = Math.max(0, securityProperties.getLoginMaxFail() - current);
            log.warn("登录失败 username={} ip={} 失败次数={}", username, clientIp, current);
            if (remain == 0) {
                // 刚跨过锁定阈值：发一次安全告警（企微/邮件，实现缺席时静默）
                notifyAccountLocked(username, clientIp, securityProperties.getLoginMaxFail());
            }
            throw new BizException(ResultCode.AUTH_BAD_CREDENTIALS,
                    remain > 0 ? "用户名或密码错误，还可尝试 " + remain + " 次" : "用户名或密码错误，账号已锁定");
        } catch (AuthenticationException ex) {
            throw new BizException(ResultCode.AUTH_BAD_CREDENTIALS);
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        tokenStore.clearLoginFailSafe(username);

        JwtTokenProvider.TokenInfo access = tokenProvider.generateAccessToken(loginUser);
        JwtTokenProvider.TokenInfo refresh = tokenProvider.generateRefreshToken(loginUser);
        tokenStore.saveLoginUser(access.jti(), loginUser, Duration.ofSeconds(tokenProvider.accessTokenSeconds()));
        // 登记刷新令牌：登出 / 改密 / 禁用时按用户全量吊销（Phase 11 批三）
        tokenStore.saveRefreshToken(loginUser.getUserId(), refresh.jti(),
                Duration.ofSeconds(tokenProvider.refreshTokenSeconds()));

        SysUser update = new SysUser();
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(clientIp);
        userMapper.update(update, Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getId, loginUser.getUserId()));

        LoginVO vo = new LoginVO();
        vo.setAccessToken(access.token());
        vo.setRefreshToken(refresh.token());
        vo.setExpiresIn(tokenProvider.accessTokenSeconds());
        vo.setUser(toUserInfo(loginUser));
        log.info("用户登录成功 userId={} username={} ip={}", loginUser.getUserId(), username, clientIp);
        return vo;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        // 登出必须能真正吊销令牌：Redis 不可用时吊销动作会丢失，安全失败而非静默降级
        if (!tokenStore.requireAvailable()) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "会话服务暂不可用，请稍后重试登出");
        }
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims claims = tokenProvider.parse(accessToken);
                long remainSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
                tokenStore.blacklist(claims.getId(), Duration.ofSeconds(Math.max(remainSeconds, 1)));
            } catch (ExpiredJwtException ex) {
                // 已过期，无需处理
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("登出时访问令牌无效: {}", ex.getMessage());
            }
        }
        // 吊销该用户全部刷新令牌：即使客户端未回传 refreshToken 也一并失效（Phase 11 批三）
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Claims claims = tokenProvider.parse(refreshToken);
                if (tokenProvider.isRefreshToken(claims)) {
                    Long userId = tokenProvider.getUserId(claims);
                    if (userId != null) {
                        tokenStore.revokeAllRefreshTokens(userId);
                        return;
                    }
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("登出时刷新令牌无效，改按访问令牌用户吊销: {}", ex.getMessage());
            }
        }
        // 客户端未带 / 带了无效 refreshToken 时，按访问令牌主体吊销
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims claims = tokenProvider.parse(accessToken);
                Long userId = tokenProvider.getUserId(claims);
                if (userId != null) {
                    tokenStore.revokeAllRefreshTokens(userId);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // 两个令牌都无效：无需吊销
            }
        }
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        // 刷新令牌校验依赖黑名单与登记集合（Redis）：不可用时安全失败，不降级为无验证模式
        if (!tokenStore.requireAvailable()) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "会话服务暂不可用，请稍后重试");
        }
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (ExpiredJwtException ex) {
            throw new BizException(ResultCode.AUTH_REFRESH_INVALID, "刷新令牌已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ResultCode.AUTH_REFRESH_INVALID);
        }
        if (!tokenProvider.isRefreshToken(claims)) {
            throw new BizException(ResultCode.AUTH_REFRESH_INVALID, "令牌类型错误");
        }
        if (tokenStore.isBlacklisted(claims.getId())) {
            throw new BizException(ResultCode.AUTH_REFRESH_INVALID);
        }
        Long userId = tokenProvider.getUserId(claims);
        // 登记集合校验：登出 / 改密 / 禁用后集合被清空，旧刷新令牌即使签名合法也不能再用
        if (userId == null || !tokenStore.isRefreshTokenRegistered(userId, claims.getId())) {
            throw new BizException(ResultCode.AUTH_REFRESH_INVALID, "刷新令牌已失效，请重新登录");
        }

        LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(claims.getSubject());
        if (!loginUser.isEnabled()) {
            // 禁用用户：同步吊销其全部刷新令牌，防止换新令牌继续访问
            tokenStore.revokeAllRefreshTokens(userId);
            throw new BizException(ResultCode.AUTH_ACCOUNT_DISABLED);
        }

        // 一次性使用：旧刷新令牌立即拉黑并移出登记集合，防重放
        long remainSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        tokenStore.blacklist(claims.getId(), Duration.ofSeconds(Math.max(remainSeconds, 1)));

        JwtTokenProvider.TokenInfo access = tokenProvider.generateAccessToken(loginUser);
        JwtTokenProvider.TokenInfo newRefresh = tokenProvider.generateRefreshToken(loginUser);
        tokenStore.saveLoginUser(access.jti(), loginUser, Duration.ofSeconds(tokenProvider.accessTokenSeconds()));
        tokenStore.saveRefreshToken(userId, newRefresh.jti(),
                Duration.ofSeconds(tokenProvider.refreshTokenSeconds()));

        LoginVO vo = new LoginVO();
        vo.setAccessToken(access.token());
        vo.setRefreshToken(newRefresh.token());
        vo.setExpiresIn(tokenProvider.accessTokenSeconds());
        vo.setUser(toUserInfo(loginUser));
        return vo;
    }

    @Override
    public UserInfoVO currentUser() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        UserInfoVO vo = toUserInfo(loginUser);
        fillProfile(vo, loginUser.getUserId());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO updateProfile(ProfileUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        String phone = normalize(request.getPhone());
        if (phone != null && !phone.equals(user.getPhone()) && userMapper.countByPhone(phone) > 0) {
            throw BizException.duplicate("手机号 " + phone);
        }
        String realName = request.getRealName().trim();
        // 用 UpdateWrapper 显式 set，允许把手机号 / 邮箱清空（实体更新会忽略 null）
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .set(SysUser::getRealName, realName)
                .set(SysUser::getPhone, phone)
                .set(SysUser::getEmail, normalize(request.getEmail()))
                .set(SysUser::getAvatar, normalize(request.getAvatar()))
                .eq(SysUser::getId, userId));
        // 会话中缓存的姓名不强制刷新，避免改资料把用户踢下线；下次登录即生效
        log.info("用户修改资料 userId={} realName={}", userId, realName);
        LoginUser loginUser = SecurityUtils.getLoginUser();
        UserInfoVO vo = toUserInfo(loginUser);
        fillProfile(vo, userId);
        return vo;
    }

    /** 补齐会话里不带的资料字段（手机、邮箱、头像、部门名） */
    private void fillProfile(UserInfoVO vo, Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        vo.setDeptId(user.getDeptId());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setLastLoginTime(user.getLastLoginTime());
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            if (dept != null) {
                vo.setDeptName(dept.getDeptName());
            }
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public List<MenuVO> menus() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<SysPermission> permissions = permissionMapper.selectMenusByUserId(userId);
        return buildTree(permissions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.AUTH_OLD_PASSWORD_WRONG);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(update);
        tokenStore.removeAllByUser(userId);
        log.info("用户修改密码 userId={}，已强制下线全部会话", userId);
    }

    private UserInfoVO toUserInfo(LoginUser loginUser) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        vo.setRealName(loginUser.getRealName());
        vo.setDeptId(loginUser.getDeptId());
        vo.setRoles(loginUser.getRoles());
        vo.setPermissions(loginUser.getPermissions());
        return vo;
    }

    /** 平铺权限列表转菜单树，父节点缺失时按根节点处理（避免脏数据导致菜单丢失） */
    private List<MenuVO> buildTree(List<SysPermission> permissions) {
        Map<Long, MenuVO> byId = new LinkedHashMap<>();
        for (SysPermission permission : permissions) {
            MenuVO node = new MenuVO();
            node.setId(permission.getId());
            node.setParentId(permission.getParentId());
            node.setCode(permission.getPermCode());
            node.setName(permission.getPermName());
            node.setPath(permission.getPath());
            node.setComponent(permission.getComponent());
            node.setIcon(permission.getIcon());
            node.setSort(permission.getSort());
            byId.put(permission.getId(), node);
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : byId.values()) {
            MenuVO parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
