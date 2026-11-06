package com.equipcare.asset.controller;

import com.equipcare.asset.dto.EquipmentFileBindDTO;
import com.equipcare.asset.dto.EquipmentQuery;
import com.equipcare.asset.dto.EquipmentSaveDTO;
import com.equipcare.asset.dto.EquipmentStatusDTO;
import com.equipcare.asset.service.EquipmentFileService;
import com.equipcare.asset.service.EquipmentHistoryService;
import com.equipcare.asset.service.EquipmentService;
import com.equipcare.asset.vo.EquipmentDetailVO;
import com.equipcare.asset.vo.EquipmentFileVO;
import com.equipcare.asset.vo.EquipmentInspectHistoryVO;
import com.equipcare.asset.vo.EquipmentRepairHistoryVO;
import com.equipcare.asset.vo.EquipmentScanVO;
import com.equipcare.asset.vo.EquipmentVO;
import com.equipcare.asset.vo.QrCodeVO;
import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.util.List;

/**
 * 设备台账管理。
 *
 * <p>权限说明：所有接口都由后端 {@code @PreAuthorize} 校验权限码，
 * 前端隐藏按钮只是体验优化，直接调用接口同样会被拒绝。
 */
@Tag(name = "10. 设备台账", description = "设备台账增删改查、状态变更、图片附件、历史记录与二维码")
@RestController
@RequestMapping("/api/asset/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentFileService fileService;
    private final EquipmentHistoryService historyService;

    public EquipmentController(EquipmentService equipmentService, EquipmentFileService fileService,
                               EquipmentHistoryService historyService) {
        this.equipmentService = equipmentService;
        this.fileService = fileService;
        this.historyService = historyService;
    }

    @Operation(summary = "设备分页查询", description = "支持按编号/名称关键字、分类、位置、状态、部门、健康分区间筛选")
    @GetMapping
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<PageResult<EquipmentVO>> page(@Valid EquipmentQuery query) {
        return Result.ok(equipmentService.page(query));
    }

    @Operation(summary = "设备详情", description = "含图片附件、最近巡检与维修历史")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<EquipmentDetailVO> detail(@PathVariable Long id) {
        return Result.ok(equipmentService.detail(id));
    }

    @Operation(summary = "新增设备", description = "资产编号留空时按「类别编码-年月-流水」自动生成")
    @PostMapping
    @PreAuthorize("hasAuthority('asset:equipment:create')")
    @AuditLog(module = "设备台账", action = "新增")
    public Result<Long> create(@Valid @RequestBody EquipmentSaveDTO dto) {
        dto.setId(null);
        return Result.ok(equipmentService.save(dto));
    }

    @Operation(summary = "编辑设备", description = "不支持通过该接口修改状态")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:equipment:update')")
    @AuditLog(module = "设备台账", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody EquipmentSaveDTO dto) {
        dto.setId(id);
        equipmentService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "设备状态变更", description = "维修中由工单驱动；报废需填写原因，且报废后不可恢复")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('asset:equipment:status')")
    @AuditLog(module = "设备台账", action = "变更状态")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody EquipmentStatusDTO dto) {
        equipmentService.changeStatus(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除设备", description = "逻辑删除；维修中的设备不允许删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:equipment:delete')")
    @AuditLog(module = "设备台账", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return Result.ok();
    }

    // ------------------------------------------------------------------
    // 设备图片与附件
    // ------------------------------------------------------------------

    @Operation(summary = "设备图片与附件列表", description = "图片在前、附件在后，首张图片即封面")
    @GetMapping("/{id}/files")
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<List<EquipmentFileVO>> files(@PathVariable Long id) {
        return Result.ok(fileService.list(id));
    }

    @Operation(summary = "绑定图片/附件", description = "先调用 /api/files/upload 上传，再用 fileId 绑定")
    @PostMapping("/{id}/files")
    @PreAuthorize("hasAuthority('asset:equipment:update')")
    @AuditLog(module = "设备台账", action = "绑定附件")
    public Result<EquipmentFileVO> bindFile(@PathVariable Long id, @Valid @RequestBody EquipmentFileBindDTO dto) {
        return Result.ok(fileService.bind(id, dto));
    }

    @Operation(summary = "解绑图片/附件", description = "只删除关联，不删除物理文件")
    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("hasAuthority('asset:equipment:update')")
    @AuditLog(module = "设备台账", action = "解绑附件")
    public Result<Void> unbindFile(@PathVariable Long id, @PathVariable Long fileId) {
        fileService.unbind(id, fileId);
        return Result.ok();
    }

    // ------------------------------------------------------------------
    // 设备档案历史
    // ------------------------------------------------------------------

    @Operation(summary = "设备维修历史", description = "按报修时间倒序，最多 100 条")
    @GetMapping("/{id}/repair-history")
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<List<EquipmentRepairHistoryVO>> repairHistory(@PathVariable Long id,
                                                               @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(historyService.repairHistory(id, limit));
    }

    @Operation(summary = "设备巡检历史", description = "按计划日期倒序，最多 100 条")
    @GetMapping("/{id}/inspect-history")
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<List<EquipmentInspectHistoryVO>> inspectHistory(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(historyService.inspectHistory(id, limit));
    }

    // ------------------------------------------------------------------
    // 二维码
    // ------------------------------------------------------------------

    @Operation(summary = "获取设备二维码内容")
    @GetMapping("/{id}/qrcode")
    @PreAuthorize("hasAuthority('asset:equipment:qrcode')")
    public Result<QrCodeVO> qrCode(@PathVariable Long id) {
        return Result.ok(equipmentService.qrCode(id));
    }

    @Operation(summary = "设备二维码图片（PNG）", description = "服务端 ZXing 渲染，便于统一打印尺寸")
    @GetMapping(value = "/{id}/qrcode.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('asset:equipment:qrcode')")
    public ResponseEntity<byte[]> qrCodeImage(@PathVariable Long id,
                                              @RequestParam(defaultValue = "320") int size,
                                              @RequestParam(defaultValue = "2") int margin) {
        byte[] png = equipmentService.qrCodePng(id, size, margin);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"equipment-" + id + ".png\"")
                .body(png);
    }

    @Operation(summary = "重新生成设备二维码", description = "旧二维码立即失效，需重新打印张贴")
    @PostMapping("/{id}/qrcode/regenerate")
    @PreAuthorize("hasAuthority('asset:equipment:qrcode')")
    @AuditLog(module = "设备台账", action = "重置二维码")
    public Result<QrCodeVO> regenerateQr(@PathVariable Long id) {
        return Result.ok(equipmentService.regenerateQr(id));
    }

    @Operation(summary = "扫码查询设备",
            description = "需登录且具备 asset:equipment:view；返回设备档案、当前状态、最近巡检与历史维修")
    @GetMapping("/scan")
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<EquipmentScanVO> scan(@RequestParam(required = false) String token,
                                        @RequestParam(required = false) String assetNo) {
        return Result.ok(equipmentService.scan(token, assetNo));
    }
}
