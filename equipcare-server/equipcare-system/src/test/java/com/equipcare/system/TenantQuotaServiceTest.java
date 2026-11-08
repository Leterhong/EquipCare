package com.equipcare.system;

import com.equipcare.common.exception.BizException;
import com.equipcare.system.entity.SysTenant;
import com.equipcare.system.mapper.SysFileMapper;
import com.equipcare.system.mapper.SysTenantMapper;
import com.equipcare.system.service.TenantQuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 租户配额校验单测（第十期第三步）：设备数/存储维度 + 容错语义。
 */
class TenantQuotaServiceTest {

    private SysTenantMapper tenantMapper;
    private SysFileMapper fileMapper;
    private TenantQuotaService service;

    @BeforeEach
    void setUp() {
        tenantMapper = Mockito.mock(SysTenantMapper.class);
        fileMapper = Mockito.mock(SysFileMapper.class);
        service = new TenantQuotaService(tenantMapper, new ObjectMapper());
        service.setFileMapper(fileMapper);
    }

    private void mockTenant(Long id, String quotaJson) {
        SysTenant tenant = new SysTenant();
        tenant.setId(id);
        tenant.setTenantCode("acme");
        tenant.setQuotaJson(quotaJson);
        when(tenantMapper.selectById(id)).thenReturn(tenant);
    }

    @Test
    @DisplayName("设备数配额：超限抛 403")
    void equipmentQuotaExceeded() {
        mockTenant(1L, "{\"maxEquipment\": 2}");
        BizException e = assertThrows(BizException.class,
                () -> service.checkEquipmentQuota(1L, 2));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("配额"));
    }

    @Test
    @DisplayName("设备数未超限通过")
    void equipmentQuotaOk() {
        mockTenant(1L, "{\"maxEquipment\": 2}");
        assertDoesNotThrow(() -> service.checkEquipmentQuota(1L, 1));
    }

    @Test
    @DisplayName("平台预置租户（id=0）不受配额约束")
    void platformTenantUnlimited() {
        mockTenant(0L, "{\"maxEquipment\": 2}");
        assertDoesNotThrow(() -> service.checkEquipmentQuota(0L, 9999));
    }

    @Test
    @DisplayName("quota 未配置不限制")
    void noQuotaConfigured() {
        Mockito.when(tenantMapper.selectById(2L)).thenReturn(new SysTenant());
        assertDoesNotThrow(() -> service.checkEquipmentQuota(2L, 9999));
    }

    @Test
    @DisplayName("quota JSON 脏数据容错（不限制）")
    void dirtyQuotaJson() {
        mockTenant(3L, "{not valid json!!");
        assertDoesNotThrow(() -> service.checkEquipmentQuota(3L, 9999));
    }

    @Test
    @DisplayName("存储配额：已用+新增超限抛 403")
    void storageQuotaExceeded() {
        mockTenant(1L, "{\"maxStorageMb\": 1}");
        Mockito.when(fileMapper.sumFileSize()).thenReturn(900L * 1024 * 1024);
        long incoming = 200L * 1024 * 1024;
        BizException e = assertThrows(BizException.class,
                () -> service.checkStorageQuota(1L, incoming));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("存储"));
    }

    @Test
    @DisplayName("存储配额：未超限通过")
    void storageQuotaOk() {
        mockTenant(1L, "{\"maxStorageMb\": 1024}");
        Mockito.when(fileMapper.sumFileSize()).thenReturn(100L * 1024 * 1024);
        assertDoesNotThrow(() -> service.checkStorageQuota(1L, 50L * 1024 * 1024));
    }

    @Test
    @DisplayName("quotaValue 未配置返回 null（不限制语义）")
    void quotaValueNull() {
        Mockito.when(tenantMapper.selectById(9L)).thenReturn(new SysTenant());
        org.junit.jupiter.api.Assertions.assertNull(service.quotaValue(9L, "maxEquipment"));
    }
}
