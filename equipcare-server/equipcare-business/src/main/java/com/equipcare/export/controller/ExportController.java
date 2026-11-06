package com.equipcare.export.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Excel 导出（第五大项）。
 *
 * <p>权限校验：{@code analytics:export} 为所有导出的总开关（仅管理角色持有），
 * 叠加对应业务域查看权限；接口不返回 JSON，直接流式写出 xlsx。
 */
@Tag(name = "Excel 导出")
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @Operation(summary = "导出设备清单")
    @AuditLog(module = "导出", action = "导出设备清单")
    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('analytics:export') and hasAuthority('asset:equipment:view')")
    public void devices(HttpServletResponse response) {
        exportService.devices(response);
    }

    @Operation(summary = "导出巡检记录")
    @AuditLog(module = "导出", action = "导出巡检记录")
    @GetMapping("/inspections")
    @PreAuthorize("hasAuthority('analytics:export') and hasAuthority('inspect:task:view')")
    public void inspections(HttpServletResponse response,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        exportService.inspections(response, startDate, endDate);
    }

    @Operation(summary = "导出维修工单")
    @AuditLog(module = "导出", action = "导出维修工单")
    @GetMapping("/work-orders")
    @PreAuthorize("hasAuthority('analytics:export') and hasAuthority('wo:workorder:view')")
    public void workOrders(HttpServletResponse response,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        exportService.workOrders(response, startDate, endDate);
    }

    @Operation(summary = "导出备件库存")
    @AuditLog(module = "导出", action = "导出备件库存")
    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('analytics:export') and hasAuthority('spare:part:view')")
    public void inventory(HttpServletResponse response) {
        exportService.inventory(response);
    }

    @Operation(summary = "导出维修成本统计")
    @AuditLog(module = "导出", action = "导出维修成本统计")
    @GetMapping("/cost-stats")
    @PreAuthorize("hasAuthority('analytics:export') and hasAuthority('analytics:view')")
    public void costStats(HttpServletResponse response,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        exportService.costStats(response, startDate, endDate);
    }
}
