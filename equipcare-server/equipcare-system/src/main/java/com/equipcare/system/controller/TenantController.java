package com.equipcare.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.Result;
import com.equipcare.common.result.ResultCode;
import com.equipcare.system.entity.SysTenant;
import com.equipcare.system.mapper.SysTenantMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户管理（多租户第三步，仅 ADMIN；单租户模式下仍可查看平台预置租户）。
 * 配额/注册入口为后续迭代；本控制器覆盖 管理员视角的列表/开通/启停。
 */
@Tag(name = "租户管理（多租户）")
@RestController
@RequestMapping("/api/system/tenants")
@ConditionalOnProperty(name = "equipcare.multi-tenant.enabled", havingValue = "true", matchIfMissing = true)
public class TenantController {

    private final SysTenantMapper tenantMapper;

    public TenantController(SysTenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    public record TenantCreateRequest(
            @NotBlank(message = "租户编码不能为空") String tenantCode,
            @NotBlank(message = "租户名称不能为空") String tenantName,
            String plan) {
    }

    @Operation(summary = "租户列表（含平台预置租户）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysTenant>> list() {
        return Result.ok(tenantMapper.selectList(
                Wrappers.<SysTenant>lambdaQuery().orderByAsc(SysTenant::getId)));
    }

    @Operation(summary = "开通租户（默认 STANDARD 套餐、启用）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "租户管理", action = "开通租户")
    public Result<Long> create(@RequestBody TenantCreateRequest req) {
        String code = req.tenantCode() == null ? "" : req.tenantCode().trim();
        if (!code.matches("[a-z0-9-]{2,32}")) {
            throw new BizException(ResultCode.BAD_REQUEST, "租户编码仅允许小写字母/数字/连字符（2~32 位）");
        }
        Long exists = tenantMapper.selectCount(
                Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getTenantCode, code));
        if (exists > 0) {
            throw BizException.duplicate("租户编码");
        }
        SysTenant tenant = new SysTenant();
        tenant.setTenantCode(code);
        tenant.setTenantName(req.tenantName().trim());
        tenant.setPlan(req.plan() == null || req.plan().isBlank() ? "STANDARD" : req.plan().trim());
        tenant.setStatus(1);
        tenantMapper.insert(tenant);
        return Result.ok(tenant.getId());
    }

    public record QuotaRequest(String quotaJson) {
    }

    @Operation(summary = "设置配额", description = "quotaJson 例：{\"maxEquipment\":50,\"maxStorageMb\":1024}，null=不限")
    @PutMapping("/{id}/quota")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "租户管理", action = "设置配额")
    public Result<Void> setQuota(@PathVariable Long id, @RequestBody QuotaRequest req) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw BizException.notFound("租户");
        }
        // 校验 JSON 格式
        if (req.quotaJson() != null && !req.quotaJson().isBlank()) {
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(req.quotaJson());
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "quotaJson 格式非法：" + e.getMessage());
            }
        }
        SysTenant patch = new SysTenant();
        patch.setId(id);
        patch.setQuotaJson(req.quotaJson() == null || req.quotaJson().isBlank() ? null : req.quotaJson().trim());
        tenantMapper.updateById(patch);
        return Result.ok();
    }

    @Operation(summary = "启用/停用租户", description = "停用后该租户全部用户无法登录")
    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "租户管理", action = "启停租户")
    public Result<Void> toggle(@PathVariable Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw BizException.notFound("租户");
        }
        if (tenant.getId() == 0L) {
            throw BizException.forbidden("平台预置租户不允许停用");
        }
        SysTenant patch = new SysTenant();
        patch.setId(tenant.getId());
        patch.setStatus(tenant.getStatus() != null && tenant.getStatus() == 1 ? 0 : 1);
        tenantMapper.updateById(patch);
        return Result.ok();
    }
}
