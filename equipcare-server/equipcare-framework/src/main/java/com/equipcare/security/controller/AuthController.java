package com.equipcare.security.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.constant.CommonConstants;
import com.equipcare.common.result.Result;
import com.equipcare.common.util.IpUtil;
import com.equipcare.security.dto.ChangePasswordRequest;
import com.equipcare.security.dto.LoginRequest;
import com.equipcare.security.dto.ProfileUpdateRequest;
import com.equipcare.security.dto.RefreshTokenRequest;
import com.equipcare.security.service.AuthService;
import com.equipcare.security.vo.LoginVO;
import com.equipcare.security.vo.MenuVO;
import com.equipcare.security.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口。
 */
@Tag(name = "01. 认证与账号", description = "登录、登出、令牌刷新、当前用户与菜单")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "登录", description = "账号密码登录，返回 accessToken 与 refreshToken。连续失败 5 次锁定 10 分钟。")
    @PostMapping("/login")
    @AuditLog(module = "认证", action = "登录")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request, IpUtil.getClientIp(httpRequest)));
    }

    @Operation(summary = "登出",
            description = "拉黑访问令牌、清除会话并吊销该用户全部刷新令牌；请求体可携带 refreshToken 加速定向吊销，缺省时按访问令牌主体全量吊销")
    @PostMapping("/logout")
    @AuditLog(module = "认证", action = "登出")
    public Result<Void> logout(HttpServletRequest httpRequest,
                               @RequestBody(required = false) RefreshTokenRequest body) {
        authService.logout(resolveToken(httpRequest), body == null ? null : body.getRefreshToken());
        return Result.ok();
    }

    @Operation(summary = "刷新令牌", description = "使用 refreshToken 换取新的令牌对，旧 refreshToken 立即失效")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.ok(authService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "当前用户信息", description = "返回当前登录用户、角色与权限码集合")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.currentUser());
    }

    @Operation(summary = "当前用户菜单", description = "返回前端动态路由所需的菜单树")
    @GetMapping("/menus")
    public Result<List<MenuVO>> menus() {
        return Result.ok(authService.menus());
    }

    @Operation(summary = "修改本人密码", description = "修改成功后强制下线全部会话，需重新登录")
    @PutMapping("/password")
    @AuditLog(module = "认证", action = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }

    @Operation(summary = "修改本人资料", description = "可改姓名、手机号、邮箱、头像；登录名与角色不可自行修改")
    @PutMapping("/profile")
    @AuditLog(module = "认证", action = "修改资料")
    public Result<UserInfoVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Result.ok(authService.updateProfile(request));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(CommonConstants.HEADER_AUTHORIZATION);
        if (header != null && header.startsWith(CommonConstants.BEARER_PREFIX)) {
            return header.substring(CommonConstants.BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
