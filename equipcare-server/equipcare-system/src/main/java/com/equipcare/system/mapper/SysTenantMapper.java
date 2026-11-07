package com.equipcare.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.system.entity.SysTenant;

/** 租户主表 Mapper（sys_tenant 为全局共享表，不受行级租户过滤） */
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
