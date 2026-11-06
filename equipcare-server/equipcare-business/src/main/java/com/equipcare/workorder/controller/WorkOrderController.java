package com.equipcare.workorder.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.workorder.dto.DispatchDTO;
import com.equipcare.workorder.dto.EvaluateDTO;
import com.equipcare.workorder.dto.ReasonDTO;
import com.equipcare.workorder.dto.RepairRecordDTO;
import com.equipcare.workorder.dto.StartRepairDTO;
import com.equipcare.workorder.dto.SubmitVerifyDTO;
import com.equipcare.workorder.dto.VerifyDTO;
import com.equipcare.workorder.dto.WorkOrderCreateDTO;
import com.equipcare.workorder.dto.WorkOrderQuery;
import com.equipcare.workorder.service.WorkOrderService;
import com.equipcare.workorder.vo.StatusCountVO;
import com.equipcare.workorder.vo.WorkOrderAssigneeVO;
import com.equipcare.workorder.vo.WorkOrderDetailVO;
import com.equipcare.workorder.vo.WorkOrderPartVO;
import com.equipcare.workorder.vo.WorkOrderTimelineItemVO;
import com.equipcare.workorder.vo.WorkOrderVO;
import com.equipcare.workorder.vo.WorkOrderWorkbenchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 维修工单。
 *
 * <p>注意：所有状态变更接口都是「动作」路径（/dispatch、/accept、/verify…），
 * 不存在 {@code PUT /status} 这类可直接指定目标状态的接口。
 */
@Tag(name = "20. 维修工单", description = "工单查询、报修、派单、接单、维修、验收全流程")
@RestController
@RequestMapping("/api/workorder")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @Operation(summary = "工单分页查询", description = "返回结果中 allowedActions 表示当前用户可执行的动作")
    @GetMapping
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    public Result<PageResult<WorkOrderVO>> page(@Valid WorkOrderQuery query) {
        return Result.ok(workOrderService.page(query));
    }

    @Operation(summary = "工单详情", description = "含流转轨迹、维修记录（前后照片）、验收记录、派单轨迹、备件用料、评价与全量时间线")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    public Result<WorkOrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(workOrderService.detail(id));
    }

    @Operation(summary = "工单操作时间线", description = "状态流转 / 维修记录 / 派单 / 验收 / 备件 / 评价 六类归并，每条都关联真实操作人")
    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    public Result<List<WorkOrderTimelineItemVO>> timeline(@PathVariable Long id) {
        return Result.ok(workOrderService.timeline(id));
    }

    @Operation(summary = "工单备件使用记录", description = "验收人核对用料，只读联查库存台账")
    @GetMapping("/{id}/parts")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    public Result<List<WorkOrderPartVO>> parts(@PathVariable Long id) {
        return Result.ok(workOrderService.parts(id));
    }

    @Operation(summary = "可选维修人", description = "具备维修人角色的启用账号，按在办工单数升序，派单下拉用")
    @GetMapping("/assignees")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    public Result<List<WorkOrderAssigneeVO>> assignees() {
        return Result.ok(workOrderService.assignees());
    }

    @Operation(summary = "工单工作台", description = "一次返回待派单 / 已驳回 / 待我接单 / 我维修中 / 待我验收 / 我已完成")
    @GetMapping("/workbench")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    public Result<WorkOrderWorkbenchVO> workbench(@RequestParam(required = false) Integer limit) {
        return Result.ok(workOrderService.workbench(limit));
    }

    @Operation(summary = "工单状态数量统计", description = "assigneeId 为空时统计全部，用于看板与待办角标")
    @GetMapping("/status-count")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    public Result<List<StatusCountVO>> statusCount(@RequestParam(required = false) Long assigneeId) {
        return Result.ok(workOrderService.statusCounts(assigneeId));
    }

    @Operation(summary = "手工报修建单", description = "建单后状态为 PENDING_ASSIGN（待派单），可带故障现场照片")
    @PostMapping
    @PreAuthorize("hasAuthority('wo:workorder:create')")
    @AuditLog(module = "维修工单", action = "手工报修")
    public Result<Long> create(@Valid @RequestBody WorkOrderCreateDTO dto) {
        return Result.ok(workOrderService.createManual(dto));
    }

    @Operation(summary = "派单", description = "可同时设定维修优先级与完成时限；已驳回的工单走同一接口重新派单")
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('wo:workorder:dispatch')")
    @AuditLog(module = "维修工单", action = "派单")
    public Result<Void> dispatch(@PathVariable Long id, @Valid @RequestBody DispatchDTO dto) {
        workOrderService.dispatch(id, dto);
        return Result.ok();
    }

    @Operation(summary = "接单", description = "仅当前工单维修人本人可操作")
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('wo:workorder:accept')")
    @AuditLog(module = "维修工单", action = "接单")
    public Result<Void> accept(@PathVariable Long id) {
        workOrderService.accept(id);
        return Result.ok();
    }

    @Operation(summary = "拒接", description = "原因不少于 5 字，拒接后进入「已驳回」，由派单人重新派单")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('wo:workorder:reject')")
    @AuditLog(module = "维修工单", action = "拒接")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody ReasonDTO dto) {
        workOrderService.rejectDispatch(id, dto);
        return Result.ok();
    }

    @Operation(summary = "转派", description = "维修人未接单前可换人")
    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('wo:workorder:dispatch')")
    @AuditLog(module = "维修工单", action = "转派")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody DispatchDTO dto) {
        workOrderService.transfer(id, dto);
        return Result.ok();
    }

    @Operation(summary = "撤回派单")
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('wo:workorder:dispatch')")
    @AuditLog(module = "维修工单", action = "撤回派单")
    public Result<Void> revoke(@PathVariable Long id, @Valid @RequestBody ReasonDTO dto) {
        workOrderService.revoke(id, dto);
        return Result.ok();
    }

    @Operation(summary = "开始维修", description = "记录维修原因、故障诊断、维修前照片并起算维修时长；重复调用返回 409")
    @PostMapping("/{id}/start-repair")
    @PreAuthorize("hasAuthority('wo:workorder:repair')")
    @AuditLog(module = "维修工单", action = "开始维修")
    public Result<Void> startRepair(@PathVariable Long id, @Valid @RequestBody StartRepairDTO dto) {
        workOrderService.startRepair(id, dto);
        return Result.ok();
    }

    @Operation(summary = "新增维修记录")
    @PostMapping("/{id}/records")
    @PreAuthorize("hasAuthority('wo:workorder:repair')")
    @AuditLog(module = "维修工单", action = "新增维修记录")
    public Result<Long> addRecord(@PathVariable Long id, @Valid @RequestBody RepairRecordDTO dto) {
        return Result.ok(workOrderService.addRepairRecord(id, dto));
    }

    @Operation(summary = "提交验收", description = "需满足：已开始维修、FINISH 记录、维修后照片、无待审批备件、维修总结")
    @PostMapping("/{id}/submit-verify")
    @PreAuthorize("hasAuthority('wo:workorder:repair')")
    @AuditLog(module = "维修工单", action = "提交验收")
    public Result<Void> submitVerify(@PathVariable Long id, @Valid @RequestBody SubmitVerifyDTO dto) {
        workOrderService.submitVerify(id, dto);
        return Result.ok();
    }

    @Operation(summary = "验收通过", description = "禁止维修人自验收，需给出质量评分")
    @PostMapping("/{id}/verify-pass")
    @PreAuthorize("hasAuthority('wo:workorder:verify')")
    @AuditLog(module = "维修工单", action = "验收通过")
    public Result<Void> verifyPass(@PathVariable Long id, @Valid @RequestBody VerifyDTO dto) {
        workOrderService.verifyPass(id, dto);
        return Result.ok();
    }

    @Operation(summary = "验收不通过", description = "原因不少于 10 字，退回维修中且不重置 SLA")
    @PostMapping("/{id}/verify-reject")
    @PreAuthorize("hasAuthority('wo:workorder:verify')")
    @AuditLog(module = "维修工单", action = "验收不通过")
    public Result<Void> verifyReject(@PathVariable Long id, @Valid @RequestBody VerifyDTO dto) {
        workOrderService.verifyReject(id, dto);
        return Result.ok();
    }

    @Operation(summary = "取消工单", description = "仅管理角色，待派单 / 已驳回 / 待接单 / 维修中可取消，原因不少于 5 字")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('wo:workorder:cancel')")
    @AuditLog(module = "维修工单", action = "取消")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody ReasonDTO dto) {
        workOrderService.cancel(id, dto);
        return Result.ok();
    }

    @Operation(summary = "强制转派", description = "仅 ADMIN，维修中换人，保留原维修人记录")
    @PostMapping("/{id}/force-transfer")
    @PreAuthorize("hasAuthority('wo:workorder:force-transfer')")
    @AuditLog(module = "维修工单", action = "强制转派")
    public Result<Void> forceTransfer(@PathVariable Long id, @Valid @RequestBody DispatchDTO dto) {
        workOrderService.forceTransfer(id, dto);
        return Result.ok();
    }

    @Operation(summary = "服务评价", description = "仅已完成工单，报修人或管理员可评，一张工单一次，三档评分 + 文字")
    @PostMapping("/{id}/evaluate")
    @PreAuthorize("hasAuthority('wo:workorder:evaluate')")
    @AuditLog(module = "维修工单", action = "服务评价")
    public Result<Long> evaluate(@PathVariable Long id, @Valid @RequestBody EvaluateDTO dto) {
        return Result.ok(workOrderService.evaluate(id, dto));
    }
}
