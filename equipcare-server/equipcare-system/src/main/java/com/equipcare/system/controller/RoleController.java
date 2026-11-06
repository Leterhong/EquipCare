package com.equipcare.system.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.Result;
import com.equipcare.system.dto.AssignPermissionDTO;
import com.equipcare.system.dto.RoleSaveDTO;
import com.equipcare.system.service.RoleService;
import com.equipcare.system.vo.PermissionTreeVO;
import com.equipcare.system.vo.PermissionVO;
import com.equipcare.system.vo.RoleVO;
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
 * 角色与权限。
 */
@Tag(name = "06. 角色权限", description = "角色维护、权限树与权限分配")
@RestController
@RequestMapping("/api/system")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.list());
    }

    @Operation(summary = "角色详情（含已分配权限）")
    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.detail(id));
    }

    @Operation(summary = "新增角色")
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @AuditLog(module = "角色", action = "新增")
    public Result<Long> create(@Valid @RequestBody RoleSaveDTO dto) {
        return Result.ok(roleService.create(dto));
    }

    @Operation(summary = "编辑角色")
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @AuditLog(module = "角色", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        roleService.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "分配权限", description = "覆盖式分配；内置 ADMIN 角色禁止修改")
    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @AuditLog(module = "角色", action = "分配权限")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionDTO dto) {
        roleService.assignPermissions(id, dto.getPermissionIds());
        return Result.ok();
    }

    @Operation(summary = "删除角色", description = "内置角色不可删除；仍有用户占用的角色不可删除")
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @AuditLog(module = "角色", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "权限清单", description = "全量权限（菜单/按钮/接口）平铺列表，含已授权角色数，只读")
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public Result<List<PermissionVO>> permissions() {
        return Result.ok(roleService.permissions());
    }

    @Operation(summary = "权限树", description = "全量权限（菜单/按钮/接口），用于角色授权界面")
    @GetMapping("/permissions/tree")
    @PreAuthorize("hasAuthority('system:role:manage')")
    public Result<List<PermissionTreeVO>> permissionTree() {
        return Result.ok(roleService.permissionTree());
    }
}
