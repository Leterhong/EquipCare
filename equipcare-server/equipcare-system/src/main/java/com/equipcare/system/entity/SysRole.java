package com.equipcare.system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;

    private String roleName;

    /** ALL / DEPT / SELF */
    private String dataScope;

    /** 1 内置角色，不可删除 */
    private Integer builtin;

    private Integer sort;

    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;
}
