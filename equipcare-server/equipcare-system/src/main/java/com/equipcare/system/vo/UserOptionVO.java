package com.equipcare.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 责任人下拉候选项：只暴露选择必需的最小字段，不含手机号、角色等敏感或无关信息。
 */
@Schema(description = "人员候选项")
public class UserOptionVO {

    private Long id;

    private String realName;

    private String deptName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }
}
