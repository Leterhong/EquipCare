package com.equipcare.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户主表（多租户第二步第三步：管理页/登录校验/配额基础）。
 * id=0 为平台预置租户，与全部既有数据 tenant_id=0 对应。
 */
@Data
@TableName("sys_tenant")
public class SysTenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户编码（子域名/登录路由标识） */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 套餐：STANDARD/PRO/PRIVATE */
    private String plan;

    /** 1 启用 / 0 停用（停用后该租户登录被拒） */
    private Integer status;

    /** 配额 JSON（设备数/存储/月 AI token 上限） */
    private String quotaJson;

    private LocalDateTime createdAt;
}
