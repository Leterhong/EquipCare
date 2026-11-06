package com.equipcare.security.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录结果。
 */
@Data
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "访问令牌，放入 Authorization: Bearer {token}")
    private String accessToken;

    @Schema(description = "刷新令牌，用于换取新的访问令牌")
    private String refreshToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "访问令牌有效期（秒）")
    private long expiresIn;

    @Schema(description = "用户信息")
    private UserInfoVO user;
}
