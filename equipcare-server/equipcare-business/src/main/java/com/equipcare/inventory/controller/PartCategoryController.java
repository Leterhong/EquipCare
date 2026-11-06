package com.equipcare.inventory.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.Result;
import com.equipcare.inventory.dto.CategorySaveDTO;
import com.equipcare.inventory.service.PartCategoryService;
import com.equipcare.inventory.vo.PartCategoryVO;
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
 * 备件分类管理。
 */
@Tag(name = "41. 备件分类", description = "备件分类的增删改查")
@RestController
@RequestMapping("/api/inventory/categories")
public class PartCategoryController {

    private final PartCategoryService categoryService;

    public PartCategoryController(PartCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "全部分类", description = "带每类备件数量，供下拉与分类管理使用")
    @GetMapping
    @PreAuthorize("hasAuthority('spare:part:view')")
    public Result<List<PartCategoryVO>> list() {
        return Result.ok(categoryService.listAll());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    @PreAuthorize("hasAuthority('spare:part:manage')")
    @AuditLog(module = "备件分类", action = "新增")
    public Result<Long> create(@Valid @RequestBody CategorySaveDTO dto) {
        dto.setId(null);
        return Result.ok(categoryService.save(dto));
    }

    @Operation(summary = "编辑分类", description = "重命名会同步备件档案上的分类快照")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('spare:part:manage')")
    @AuditLog(module = "备件分类", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveDTO dto) {
        dto.setId(id);
        categoryService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除分类", description = "分类下仍有备件时拒绝")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('spare:part:manage')")
    @AuditLog(module = "备件分类", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
