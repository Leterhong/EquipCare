package com.equipcare.config;

import com.equipcare.common.tenant.TenantContext;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户感知基础设施单元测试（第十期第一步）。
 * 行级 SQL 注入的端到端验证由集成测试（需 MySQL）覆盖，
 * 此处验证上下文语义与处理器决策逻辑。
 */
class TenantInfraTest {

    private final MybatisPlusConfig config = new MybatisPlusConfig();
    private final com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler handler = config.tenantLineHandler();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("无上下文（调度线程）→ 兜底租户 0，与全部既有数据一致")
    void 默认兜底租户0() {
        assertNull(TenantContext.get());
        assertEquals(0L, ((LongValue) handler.getTenantId()).getValue());
    }

    @Test
    @DisplayName("set 后取值一致，clear 复位")
    void setAndGet() {
        TenantContext.set(42L);
        assertEquals(42L, ((LongValue) handler.getTenantId()).getValue());
        TenantContext.clear();
        assertEquals(0L, ((LongValue) handler.getTenantId()).getValue());
    }

    @Test
    @DisplayName("runAs 执行期间生效，结束恢复原上下文（含异常路径）")
    void runAs恢复语义() {
        TenantContext.set(1L);
        AtomicReference<Long> seen = new AtomicReference<>();
        TenantContext.runAs(7L, () -> seen.set(TenantContext.get()));
        assertEquals(7L, seen.get());
        assertEquals(1L, TenantContext.get());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> TenantContext.runAs(7L, () -> { throw new IllegalStateException("boom"); }));
        assertEquals(1L, TenantContext.get(), "异常后必须恢复原上下文");
    }

    @Test
    @DisplayName("runAs 无原上下文时结束清除（线程复用不残留）")
    void runAs清除残留() {
        TenantContext.runAs(9L, () -> { });
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("共享表清单：权限/角色分配/字典/调度锁不隔离")
    void 共享表不隔离() {
        for (String table : new String[]{"sys_permission", "sys_role_permission", "sys_dict", "sys_dict_item", "sys_job_lock"}) {
            assertTrue(handler.ignoreTable(table), table + " 应在共享清单中");
        }
        assertTrue(!handler.ignoreTable("wo_work_order"));
        assertTrue(!handler.ignoreTable("eq_equipment"));
        // 大小写不敏感（表名大小写在不同数据库配置下不一致）
        assertTrue(handler.ignoreTable("SYS_DICT"));
    }

    @Test
    @DisplayName("租户列名固定为 tenant_id")
    void 列名() {
        assertEquals("tenant_id", handler.getTenantIdColumn());
    }

    @Test
    @DisplayName("共享清单与迁移脚本一致（防两处清单漂移）")
    void 共享清单防漂移() {
        // 10-multi-tenant-step1.sql 的表清单不含任何共享表
        Set<String> shared = MybatisPlusConfig.TENANT_SHARED_TABLES;
        assertEquals(5, shared.size());
    }
}
