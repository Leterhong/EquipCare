package com.equipcare.analytics.controller;

import com.equipcare.analytics.service.DashboardService;
import com.equipcare.analytics.vo.DashboardChartsVO;
import com.equipcare.analytics.vo.DashboardSummaryVO;
import com.equipcare.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 运维数据驾驶舱。指标与图表全部由后端聚合，前端零计算。
 */
@Tag(name = "运维驾驶舱")
@RestController
@RequestMapping("/api/analytics/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "14 项指标总览", description = "缺省统计当月；跨度不超过 366 天")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<DashboardSummaryVO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(dashboardService.summary(startDate, endDate));
    }

    @Operation(summary = "八张图表数据", description = "缺省统计当月；跨度不超过 366 天")
    @GetMapping("/charts")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<DashboardChartsVO> charts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(dashboardService.charts(startDate, endDate));
    }
}
