package com.equipcare.inspection.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.inspection.dto.TemplateQuery;
import com.equipcare.inspection.dto.TemplateSaveDTO;
import com.equipcare.inspection.service.InspectionTemplateService;
import com.equipcare.inspection.vo.TemplateDetailVO;
import com.equipcare.inspection.vo.TemplateOptionVO;
import com.equipcare.inspection.vo.TemplateVO;
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
 * 巡检模板管理。
 */
@Tag(name = "31. 巡检模板", description = "模板与检查项的增删改查、发布与停用")
@RestController
@RequestMapping("/api/inspection/templates")
public class InspectionTemplateController {

    private final InspectionTemplateService templateService;

    public InspectionTemplateController(InspectionTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "模板分页")
    @GetMapping
    @PreAuthorize("hasAuthority('inspect:template:view')")
    public Result<PageResult<TemplateVO>> page(@Valid TemplateQuery query) {
        return Result.ok(templateService.page(query));
    }

    @Operation(summary = "模板选项", description = "计划表单使用，仅返回已发布模板")
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('inspect:template:view')")
    public Result<List<TemplateOptionVO>> options() {
        return Result.ok(templateService.options());
    }

    @Operation(summary = "模板详情", description = "含有序检查项、检查标准与异常判定规则")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inspect:template:view')")
    public Result<TemplateDetailVO> detail(@PathVariable Long id) {
        return Result.ok(templateService.detail(id));
    }

    @Operation(summary = "新增模板")
    @PostMapping
    @PreAuthorize("hasAuthority('inspect:template:manage')")
    @AuditLog(module = "巡检模板", action = "新增")
    public Result<Long> create(@Valid @RequestBody TemplateSaveDTO dto) {
        return Result.ok(templateService.create(dto));
    }

    @Operation(summary = "编辑模板", description = "检查项整组替换；已发布模板版本号 +1")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inspect:template:manage')")
    @AuditLog(module = "巡检模板", action = "编辑")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TemplateSaveDTO dto) {
        templateService.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除模板", description = "被计划引用时拒绝删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inspect:template:manage')")
    @AuditLog(module = "巡检模板", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "发布模板", description = "发布后才能被巡检计划引用")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('inspect:template:manage')")
    @AuditLog(module = "巡检模板", action = "发布")
    public Result<Void> publish(@PathVariable Long id) {
        templateService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "停用模板")
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('inspect:template:manage')")
    @AuditLog(module = "巡检模板", action = "停用")
    public Result<Void> disable(@PathVariable Long id) {
        templateService.disable(id);
        return Result.ok();
    }
}
