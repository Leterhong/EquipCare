package com.equipcare.system.controller;

import com.equipcare.common.result.Result;
import com.equipcare.system.service.DeptService;
import com.equipcare.system.vo.DeptTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门树。登录用户均可读取，用于表单选择。
 */
@Tag(name = "07. 组织架构", description = "部门树查询")
@RestController
@RequestMapping("/api/system/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @Operation(summary = "部门树")
    @GetMapping("/tree")
    public Result<List<DeptTreeVO>> tree() {
        return Result.ok(deptService.tree());
    }
}
