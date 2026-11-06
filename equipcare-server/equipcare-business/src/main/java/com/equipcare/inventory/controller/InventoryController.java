package com.equipcare.inventory.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.query.PageQuery;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.inventory.dto.PartApplyDTO;
import com.equipcare.inventory.dto.PartQuery;
import com.equipcare.inventory.dto.PartSaveDTO;
import com.equipcare.inventory.dto.StockChangeDTO;
import com.equipcare.inventory.dto.StockTxnQuery;
import com.equipcare.inventory.service.InventoryService;
import com.equipcare.inventory.vo.PartApplyVO;
import com.equipcare.inventory.vo.PartCostStatVO;
import com.equipcare.inventory.vo.PartVO;
import com.equipcare.inventory.vo.StockTxnVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备件与库存。
 */
@Tag(name = "40. 备件库存", description = "备件档案、出入库、工单领用与退料、库存流水")
@RestController
@RequestMapping("/api/inventory")
@org.springframework.validation.annotation.Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "备件分页")
    @GetMapping("/parts")
    @PreAuthorize("hasAuthority('spare:part:view')")
    public Result<PageResult<PartVO>> pageParts(@Valid PartQuery query) {
        return Result.ok(inventoryService.pagePart(query));
    }

    @Operation(summary = "低于安全库存的备件", description = "用于首页库存预警")
    @GetMapping("/parts/below-safety")
    @PreAuthorize("hasAuthority('spare:part:view')")
    public Result<List<PartVO>> belowSafety() {
        return Result.ok(inventoryService.belowSafetyStock());
    }

    @Operation(summary = "新增备件")
    @PostMapping("/parts")
    @PreAuthorize("hasAuthority('spare:part:manage')")
    @AuditLog(module = "备件档案", action = "新增")
    public Result<Long> createPart(@Valid @RequestBody PartSaveDTO dto) {
        dto.setId(null);
        return Result.ok(inventoryService.savePart(dto));
    }

    @Operation(summary = "编辑备件", description = "库存数量不通过该接口修改")
    @PutMapping("/parts/{id}")
    @PreAuthorize("hasAuthority('spare:part:manage')")
    @AuditLog(module = "备件档案", action = "编辑")
    public Result<Void> updatePart(@PathVariable Long id, @Valid @RequestBody PartSaveDTO dto) {
        dto.setId(id);
        inventoryService.savePart(dto);
        return Result.ok();
    }

    @Operation(summary = "删除备件", description = "存在待审批领用或仍有库存时拒绝")
    @DeleteMapping("/parts/{id}")
    @PreAuthorize("hasAuthority('spare:part:manage')")
    @AuditLog(module = "备件档案", action = "删除")
    public Result<Void> deletePart(@PathVariable Long id) {
        inventoryService.deletePart(id);
        return Result.ok();
    }

    @Operation(summary = "备件入库")
    @PostMapping("/stock/in")
    @PreAuthorize("hasAuthority('spare:stock:in')")
    @AuditLog(module = "备件库存", action = "入库")
    public Result<StockTxnVO> stockIn(@Valid @RequestBody StockChangeDTO dto) {
        return Result.ok(inventoryService.stockIn(dto));
    }

    @Operation(summary = "直接出库", description = "非工单场景出库，库存不足返回 4001")
    @PostMapping("/stock/out")
    @PreAuthorize("hasAuthority('spare:issue')")
    @AuditLog(module = "备件库存", action = "出库")
    public Result<StockTxnVO> stockOut(@Valid @RequestBody StockChangeDTO dto) {
        return Result.ok(inventoryService.stockOut(dto));
    }

    @Operation(summary = "盘点调整", description = "positive=true 盘盈，false 盘亏；数量必须为正整数")
    @PostMapping("/stock/adjust")
    @PreAuthorize("hasAuthority('spare:stock:adjust')")
    @AuditLog(module = "备件库存", action = "盘点调整")
    public Result<StockTxnVO> adjust(@RequestParam Long partId,
                                     @RequestParam @jakarta.validation.constraints.Min(value = 1, message = "调整数量必须为正整数") int quantity,
                                     @RequestParam(defaultValue = "true") boolean positive,
                                     @RequestParam(required = false) String remark) {
        return Result.ok(inventoryService.adjust(partId, quantity, positive, remark));
    }

    @Operation(summary = "库存流水", description = "按备件倒序返回，默认为近 50 条")
    @GetMapping("/stock/txns")
    @PreAuthorize("hasAuthority('spare:txn:view')")
    public Result<List<StockTxnVO>> txns(@RequestParam Long partId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(inventoryService.recentTxns(partId, limit));
    }

    @Operation(summary = "库存流水分页", description = "按备件 / 类型 / 业务类型 / 时间段过滤，库存流水页使用")
    @GetMapping("/stock/txns/page")
    @PreAuthorize("hasAuthority('spare:txn:view')")
    public Result<PageResult<StockTxnVO>> pageTxns(@Valid StockTxnQuery query) {
        return Result.ok(inventoryService.pageTxns(query));
    }

    @Operation(summary = "维修备件成本统计", description = "按备件聚合工单领用出库的数量与金额，可选时间段")
    @GetMapping("/stats/cost")
    @PreAuthorize("hasAuthority('spare:txn:view')")
    public Result<List<PartCostStatVO>> costStats(
            @RequestParam(required = false) LocalDateTime timeFrom,
            @RequestParam(required = false) LocalDateTime timeTo) {
        return Result.ok(inventoryService.costStats(timeFrom, timeTo));
    }

    @Operation(summary = "领用申请分页", description = "审批视角，可按状态（APPLIED/ISSUED/REJECTED/RETURNED）与工单过滤")
    @GetMapping("/applies")
    @PreAuthorize("hasAuthority('spare:issue')")
    public Result<PageResult<PartApplyVO>> pageApplies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long workOrderId,
            @Valid PageQuery query) {
        return Result.ok(inventoryService.pageApplies(status, workOrderId, query));
    }

    @Operation(summary = "工单备件申请", description = "申请后状态为 APPLIED，不扣减库存")
    @PostMapping("/applies")
    @PreAuthorize("hasAuthority('spare:apply')")
    @AuditLog(module = "备件领用", action = "申请")
    public Result<Long> apply(@Valid @RequestBody PartApplyDTO dto) {
        return Result.ok(inventoryService.applyPart(dto));
    }

    @Operation(summary = "审批并出库", description = "原子扣减库存并写流水，返回流水ID")
    @PostMapping("/applies/{id}/issue")
    @PreAuthorize("hasAuthority('spare:issue')")
    @AuditLog(module = "备件领用", action = "审批出库")
    public Result<Long> issue(@PathVariable Long id) {
        return Result.ok(inventoryService.issuePart(id));
    }

    @Operation(summary = "驳回领用申请")
    @PostMapping("/applies/{id}/reject")
    @PreAuthorize("hasAuthority('spare:issue')")
    @AuditLog(module = "备件领用", action = "驳回")
    public Result<Void> reject(@PathVariable Long id, @RequestParam(required = false) String remark) {
        inventoryService.rejectApply(id, remark);
        return Result.ok();
    }

    @Operation(summary = "退料入库", description = "退料后库存回增，工单备件成本相应冲减")
    @PostMapping("/applies/{id}/return")
    @PreAuthorize("hasAuthority('spare:issue')")
    @AuditLog(module = "备件领用", action = "退料")
    public Result<Long> returnPart(@PathVariable Long id, @RequestParam(required = false) String remark) {
        return Result.ok(inventoryService.returnPart(id, remark));
    }
}
