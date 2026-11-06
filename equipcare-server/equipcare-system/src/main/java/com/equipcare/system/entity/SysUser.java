package com.equipcare.system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    /** BCrypt 哈希，禁止明文 */
    private String password;

    private String realName;

    private String phone;

    private String email;

    private Long deptId;

    private String avatar;

    /** 技能标签，逗号分隔，派单参考 */
    private String skillTags;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @TableLogic
    private Integer deleted;
}
