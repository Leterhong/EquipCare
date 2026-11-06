package com.equipcare.workorder.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.query.PageQuery;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.workorder.dto.SlaPolicyUpdateDTO;
import com.equipcare.workorder.service.SlaService;
import com.equipcare.workorder.vo.SlaActiveOrderVO;
import com.equipcare.workorder.vo.SlaAlertVO;
import com.equipcare.workorder.vo.SlaPolicyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SLA 政策配置与超时预警中心。
 */
@Tag(name = "42. SLA 预警", description = "SLA 政策配置、超时预警记录与活跃工单监控")
@RestController
@RequestMapping("/api/sla")
public class SlaController {

    private final SlaService slaService;

    public SlaController(SlaService slaService) {
        this.slaService = slaService;
    }

    @Operation(summary = "SLA 政策列表", description = "按等级 P1→P4，含响应/修复/验收时限与预警阈值")
    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('sla:policy:view')")
    public Result<List<SlaPolicyVO>> policies() {
        return Result.ok(slaService.policies());
    }

    @Operation(summary = "更新 SLA 政策", description = "时限调整立即对后续建单/派单/提交验收生效，已生成的时限列不追溯")
    @PutMapping("/policies/{level}")
    @PreAuthorize("hasAuthority('sla:policy:manage')")
    @AuditLog(module = "SLA 政策", action = "更新")
    public Result<Void> updatePolicy(@PathVariable String level,
                                     @Valid @RequestBody SlaPolicyUpdateDTO dto) {
        slaService.updatePolicy(level, dto);
        return Result.ok();
    }

    @Operation(summary = "活跃工单 SLA 状态", description = "四个进行中阶段的实时窗口与百分比，后端计算")
    @GetMapping("/active-orders")
    @PreAuthorize("hasAuthority('sla:monitor:view')")
    public Result<List<SlaActiveOrderVO>> activeOrders() {
        return Result.ok(slaService.activeOrders());
    }

    @Operation(summary = "超时预警记录分页", description = "同单同阶段每天至多一条，可按阶段/等级过滤")
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('sla:monitor:view')")
    public Result<PageResult<SlaAlertVO>> pageAlerts(@Valid PageQuery query,
                                                     @RequestParam(required = false) String stage,
                                                     @RequestParam(required = false) String level) {
        return Result.ok(slaService.pageAlerts(query, stage, level));
    }

    @Operation(summary = "立即执行一轮扫描", description = "联调与验收用；定时任务每分钟自动执行，无需手动触发")
    @PostMapping("/alerts/scan")
    @PreAuthorize("hasAuthority('sla:monitor:view')")
    @AuditLog(module = "SLA 预警", action = "手动扫描")
    public Result<Integer> scan() {
        return Result.ok(slaService.scan());
    }
}
