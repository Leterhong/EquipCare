package com.equipcare.inspection.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.inspection.dto.PlanQuery;
import com.equipcare.inspection.dto.PlanSaveDTO;
import com.equipcare.inspection.service.InspectionPlanService;
import com.equipcare.inspection.vo.PlanDetailVO;
import com.equipcare.inspection.vo.PlanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 巡检计划管理。
 */
@Tag(name = "32. 巡检计划", description = "计划增删改查、启停与立即生成任务")
@RestController
@RequestMapping("/api/inspection/plans")
public class InspectionPlanController {

    private final InspectionPlanService planService;

    public InspectionPlanController(InspectionPlanService planService) {
        this.planService = planService;
    }

    @Operation(summary = "计划分页")
    @GetMapping
    @PreAuthorize("hasAuthority('inspect:plan:view')")
    public Result<PageResult<PlanVO>> page(@Valid PlanQuery query) {
        return Result.ok(planService.page(query));
    }

    @Operation(summary = "计划详情", description = "含作用域设备快照与最近生成的任务")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inspect:plan:view')")
    public Result<PlanDetailVO> detail(@PathVariable Long id) {
        return Result.ok(planService.detail(id));
    }

    @Operation(summary = "新增计划", description = "保存后默认暂停，确认无误再启用")
    @PostMapping
    @PreAuthorize("hasAuthority('inspect:plan:manage')")
    @AuditLog(module = "巡检计划", action = "新增")
    public Result<Long> create(@Valid @RequestBody PlanSaveDTO dto) {
        return Result.ok(planService.create(dto));
    }

    @Operation(summary = "编辑计划")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inspect:plan:manage')")
    @AuditLog(module = "巡检计划", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlanSaveDTO dto) {
        planService.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除计划", description = "启用中的计划需先暂停")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inspect:plan:manage')")
    @AuditLog(module = "巡检计划", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "启用/暂停计划", description = "status=ENABLED|DISABLED")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('inspect:plan:manage')")
    @AuditLog(module = "巡检计划", action = "变更状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        planService.changeStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "立即生成任务", description = "不等定时器，按计划的下一次执行时刻生成，返回新建任务数")
    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority('inspect:plan:generate')")
    @AuditLog(module = "巡检计划", action = "立即生成")
    public Result<Integer> generate(@PathVariable Long id) {
        return Result.ok(planService.generateNow(id));
    }
}
