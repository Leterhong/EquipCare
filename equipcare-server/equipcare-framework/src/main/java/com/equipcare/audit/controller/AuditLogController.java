package com.equipcare.audit.controller;

import com.equipcare.audit.dto.AuditLogQuery;
import com.equipcare.audit.entity.SysAuditLog;
import com.equipcare.audit.service.AuditLogService;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计查询。审计数据只读，不提供任何修改与删除接口。
 */
@Tag(name = "02. 操作审计", description = "审计日志查询（只读）")
@RestController
@RequestMapping("/api/system/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "审计日志分页查询")
    @GetMapping
    @PreAuthorize("hasAuthority('system:audit:view')")
    public Result<PageResult<SysAuditLog>> page(@Valid AuditLogQuery query) {
        return Result.ok(auditLogService.page(query));
    }
}
