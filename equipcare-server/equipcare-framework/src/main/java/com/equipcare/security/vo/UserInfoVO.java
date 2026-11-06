package com.equipcare.security.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息。
 */
@Data
@Schema(description = "当前用户信息")
public class UserInfoVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "最近登录时间")
    private java.time.LocalDateTime lastLoginTime;

    @Schema(description = "角色编码集合")
    private List<String> roles;

    @Schema(description = "权限码集合")
    private List<String> permissions;
}
