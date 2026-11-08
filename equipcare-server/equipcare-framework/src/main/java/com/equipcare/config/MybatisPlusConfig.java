package com.equipcare.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.equipcare.common.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * MyBatis-Plus 插件配置。
 *
 * <ul>
 *   <li>租户行级隔离（第十期第一步，零功能变化）：TenantLineInnerInterceptor
 *       自动为 SQL 注入 tenant_id 条件/补列——业务代码零感知。上下文缺省（调度线程）
 *       兜底租户 0（平台预置），与全部既有数据一致；共享表（权限/字典等）不隔离；
 *   <li>分页插件：方言 MySQL，单页上限 500，防止恶意大分页</li>
 *   <li>乐观锁插件：配合实体 version 字段，保证工单/设备/备件并发安全</li>
 *   <li>防全表更新删除插件：拦截没有 where 条件的 update/delete</li>
 * </ul>
 *
 * <p>插件顺序：租户拦截器必须最先（分页前），否则分页 count 与行过滤不一致。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 全局共享表：不做租户过滤（权限码/数据字典/角色权限分配/调度锁）。
     * 与 database/10-multi-tenant-step1.sql 中「不加列清单」一一对应，改一处必须同步另一处。
     */
    public static final Set<String> TENANT_SHARED_TABLES = Set.of(
            "sys_permission", "sys_role_permission", "sys_dict", "sys_dict_item", "sys_job_lock",
            // 租户主表自身：行级过滤对租户表无意义，且表无 tenant_id 列（第二步建表）
            "sys_tenant");

    @Bean
    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tid = TenantContext.get();
                return new LongValue(tid == null ? 0L : tid);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return TENANT_SHARED_TABLES.contains(tableName.toLowerCase());
            }
        };
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineHandler tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 租户行级隔离必须先于分页/乐观锁注册
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
