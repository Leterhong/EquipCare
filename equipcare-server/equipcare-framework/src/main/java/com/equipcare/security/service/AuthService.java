package com.equipcare.security.service;

import com.equipcare.security.dto.ChangePasswordRequest;
import com.equipcare.security.dto.LoginRequest;
import com.equipcare.security.dto.ProfileUpdateRequest;
import com.equipcare.security.vo.LoginVO;
import com.equipcare.security.vo.MenuVO;
import com.equipcare.security.vo.UserInfoVO;

import java.util.List;

/**
 * 认证服务：登录、登出、令牌刷新、当前用户与菜单。
 */
public interface AuthService {

    /**
     * 账号密码登录。
     *
     * @param request  登录请求
     * @param clientIp 客户端 IP，用于审计与最近登录记录
     */
    LoginVO login(LoginRequest request, String clientIp);

    /**
     * 登出：拉黑访问令牌、清除会话，并吊销该用户全部刷新令牌。
     * Redis 不可用时安全失败（无法保证吊销完整性），由调用方提示稍后重试。
     *
     * @param accessToken  请求头中的访问令牌，可为空
     * @param refreshToken 客户端回传的刷新令牌，可为空（为空时按访问令牌主体吊销）
     */
    void logout(String accessToken, String refreshToken);

    /** 使用刷新令牌换取新的令牌对 */
    LoginVO refresh(String refreshToken);

    /** 当前登录用户信息 */
    UserInfoVO currentUser();

    /** 当前用户可见菜单树 */
    List<MenuVO> menus();

    /** 修改本人密码，成功后强制下线全部会话 */
    void changePassword(ChangePasswordRequest request);

    /** 修改本人资料（姓名、手机、邮箱、头像），返回最新用户信息 */
    UserInfoVO updateProfile(ProfileUpdateRequest request);
}
