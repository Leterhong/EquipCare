package com.equipcare.common.security;

import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文工具。业务代码通过本类获取用户，禁止直接读 SecurityContextHolder。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static LoginUser getLoginUser() {
        LoginUser loginUser = getLoginUserOrNull();
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long getCurrentUserIdOrNull() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? null : loginUser.getUserId();
    }

    public static Long getCurrentUserId() {
        return getLoginUser().getUserId();
    }

    public static String getCurrentUsername() {
        return getLoginUser().getUsername();
    }

    /** 用于数据权限判断：是否拥有指定角色 */
    public static boolean hasRole(String roleCode) {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser != null && loginUser.hasRole(roleCode);
    }

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
