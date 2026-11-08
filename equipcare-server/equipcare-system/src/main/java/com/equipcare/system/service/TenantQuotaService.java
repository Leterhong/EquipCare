package com.equipcare.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.common.exception.BizException;
import com.equipcare.system.entity.SysTenant;
import com.equipcare.system.mapper.SysTenantMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 租户配额校验（多租户第三步）。
 *
 * <p>配额来自 sys_tenant.quota_json，形如：
 * <pre>{ "maxEquipment": 50, "maxStorageMb": 1024, "maxAiCallsPerMonth": 500 }</pre>
 * 字段缺省 = 不限制；平台预置租户（id=0）不受配额约束。
 * 当前落地：设备数上限（建设备时拦截）；存储/AI 配额为后续迭代挂点。
 */
@Service
public class TenantQuotaService {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaService.class);

    public static final String KEY_MAX_EQUIPMENT = "maxEquipment";
    public static final String KEY_MAX_STORAGE_MB = "maxStorageMb";

    private final SysTenantMapper tenantMapper;
    private final ObjectMapper objectMapper;

    public TenantQuotaService(SysTenantMapper tenantMapper, ObjectMapper objectMapper) {
        this.tenantMapper = tenantMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 设备创建配额校验。
     *
     * @param tenantId   设备所属租户（0=平台预置，不受限）
     * @param currentCount 该租户当前设备数
     * @throws BizException 超出配额时 403
     */
    public void checkEquipmentQuota(Long tenantId, long currentCount) {
        Long max = quotaValue(tenantId, KEY_MAX_EQUIPMENT);
        if (max == null || max <= 0) {
            return;
        }
        if (currentCount >= max) {
            throw BizException.forbidden("已达租户设备数配额上限（" + max + " 台），请联系平台方扩容");
        }
    }

    /** 读取配额字段；租户缺失/停用/quota 未配置/解析失败 → null（不限制） */
    public Long quotaValue(Long tenantId, String key) {
        if (tenantId == null || tenantId == 0L) {
            return null;
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getQuotaJson() == null || tenant.getQuotaJson().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(tenant.getQuotaJson()).path(key);
            return node.isMissingNode() || !node.canConvertToLong() ? null : node.asLong();
        } catch (Exception e) {
            log.warn("租户配额解析失败 tenantId={} key={}", tenantId, key, e);
            return null;
        }
    }

    private com.equipcare.system.mapper.SysFileMapper fileMapper;

    public void setFileMapper(com.equipcare.system.mapper.SysFileMapper fileMapper) {
        this.fileMapper = fileMapper;
    }

    /**
     * 存储配额校验：租户已用字节 + 新文件大小 ≤ maxStorageMb（MB）。
     * 上传前调用；未配置配额或平台预置租户不限制。
     *
     * @param incomingBytes 本次上传的总字节数
     */
    public void checkStorageQuota(Long tenantId, long incomingBytes) {
        Long maxMb = quotaValue(tenantId, KEY_MAX_STORAGE_MB);
        if (maxMb == null || maxMb <= 0 || fileMapper == null) {
            return;
        }
        long usedBytes = fileMapper.sumFileSize();
        long limitBytes = maxMb * 1024L * 1024L;
        if (usedBytes + incomingBytes > limitBytes) {
            throw BizException.forbidden(String.format(
                    "已达租户存储配额上限（已用 %.1f MB / 上限 %d MB），请联系平台方扩容",
                    usedBytes / 1024.0 / 1024.0, maxMb));
        }
    }

    /** 配额配置是否存在（供管理页展示） */
    public boolean hasQuota(Long tenantId) {
        if (tenantId == null || tenantId == 0L) {
            return false;
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        return tenant != null && tenant.getQuotaJson() != null && !tenant.getQuotaJson().isBlank();
    }

    /** 供管理页展示的配额快照（只读） */
    public Map<String, Object> quotaSnapshot(Long tenantId) {
        if (tenantId == null || tenantId == 0L || !hasQuota(tenantId)) {
            return Map.of("configured", false);
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        try {
            JsonNode node = objectMapper.readTree(tenant.getQuotaJson());
            return Map.of("configured", true, "quota", objectMapper.convertValue(node, Map.class));
        } catch (Exception e) {
            return Map.of("configured", false);
        }
    }
}
