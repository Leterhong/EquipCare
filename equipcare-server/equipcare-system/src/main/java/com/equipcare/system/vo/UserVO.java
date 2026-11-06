package com.equipcare.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户展示对象。
 */
@Data
@Schema(description = "用户信息")
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String email;

    private Long deptId;

    private String deptName;

    private String skillTags;

    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private List<Long> roleIds;

    private List<String> roleNames;
}
