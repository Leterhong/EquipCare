package com.equipcare.asset.controller;

import com.equipcare.asset.dto.LocationSaveDTO;
import com.equipcare.asset.service.LocationService;
import com.equipcare.asset.vo.LocationTreeVO;
import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.Result;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 位置管理。
 */
@Tag(name = "12. 位置管理", description = "园区 / 楼栋 / 楼层 / 房间四级位置树")
@RestController
@RequestMapping("/api/asset/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "位置树", description = "含每个位置下的设备数量")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<List<LocationTreeVO>> tree() {
        return Result.ok(locationService.tree());
    }

    @Operation(summary = "新增位置", description = "全路径由后端依据父级自动拼接，无需前端传入")
    @PostMapping
    @PreAuthorize("hasAuthority('asset:location:manage')")
    @AuditLog(module = "位置", action = "新增")
    public Result<Long> create(@Valid @RequestBody LocationSaveDTO dto) {
        dto.setId(null);
        return Result.ok(locationService.save(dto));
    }

    @Operation(summary = "编辑位置", description = "修改名称或父级后，子孙节点全路径自动刷新")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:location:manage')")
    @AuditLog(module = "位置", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LocationSaveDTO dto) {
        dto.setId(id);
        locationService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除位置", description = "存在子位置或已被设备引用时不允许删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:location:manage')")
    @AuditLog(module = "位置", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return Result.ok();
    }
}
