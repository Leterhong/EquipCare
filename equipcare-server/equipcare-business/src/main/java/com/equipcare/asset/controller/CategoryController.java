package com.equipcare.asset.controller;

import com.equipcare.asset.dto.CategorySaveDTO;
import com.equipcare.asset.service.CategoryService;
import com.equipcare.asset.vo.CategoryTreeVO;
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
 * 设备类别管理。
 */
@Tag(name = "11. 设备类别", description = "设备类别树维护，类别编码作为资产编号前缀")
@RestController
@RequestMapping("/api/asset/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "设备类别树", description = "含每个类别下的设备数量")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('asset:equipment:view')")
    public Result<List<CategoryTreeVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    @Operation(summary = "新增设备类别")
    @PostMapping
    @PreAuthorize("hasAuthority('asset:category:manage')")
    @AuditLog(module = "设备类别", action = "新增")
    public Result<Long> create(@Valid @RequestBody CategorySaveDTO dto) {
        dto.setId(null);
        return Result.ok(categoryService.save(dto));
    }

    @Operation(summary = "编辑设备类别", description = "类别编码为资产编号前缀，修改会影响后续新生成的编号")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:category:manage')")
    @AuditLog(module = "设备类别", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveDTO dto) {
        dto.setId(id);
        categoryService.save(dto);
        return Result.ok();
    }

    @Operation(summary = "删除设备类别", description = "存在子类别或已被设备引用时不允许删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:category:manage')")
    @AuditLog(module = "设备类别", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
