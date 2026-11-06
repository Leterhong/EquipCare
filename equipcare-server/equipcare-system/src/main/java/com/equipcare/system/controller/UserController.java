package com.equipcare.system.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.system.dto.ResetPasswordDTO;
import com.equipcare.system.dto.UserCreateDTO;
import com.equipcare.system.dto.UserQuery;
import com.equipcare.system.dto.UserUpdateDTO;
import com.equipcare.system.service.UserService;
import com.equipcare.system.vo.UserOptionVO;
import com.equipcare.system.vo.UserVO;
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

import java.util.List;

/**
 * 用户管理。
 */
@Tag(name = "05. 用户管理", description = "用户增删改查、启停与重置密码")
@RestController
@RequestMapping("/api/system/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户分页查询")
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:view')")
    public Result<PageResult<UserVO>> page(@Valid UserQuery query) {
        return Result.ok(userService.page(query));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:view')")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:manage')")
    @AuditLog(module = "用户", action = "新增")
    public Result<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.ok(userService.create(dto));
    }

    @Operation(summary = "编辑用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:manage')")
    @AuditLog(module = "用户", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        userService.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "启用/禁用用户", description = "禁用后该用户全部会话立即失效")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:manage')")
    @AuditLog(module = "用户", action = "变更状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "重置用户密码", description = "重置后强制下线该用户全部会话")
    @PutMapping("/{id}/password/reset")
    @PreAuthorize("hasAuthority('system:user:manage')")
    @AuditLog(module = "用户", action = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(id, dto.getNewPassword());
        return Result.ok();
    }

    @Operation(summary = "人员候选项", description = "仅返回姓名与部门，供选择设备责任人等场景使用")
    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public Result<List<UserOptionVO>> options(@RequestParam(required = false) String keyword) {
        return Result.ok(userService.options(keyword));
    }

    @Operation(summary = "删除用户", description = "逻辑删除，同时释放登录名占用")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:manage')")
    @AuditLog(module = "用户", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
