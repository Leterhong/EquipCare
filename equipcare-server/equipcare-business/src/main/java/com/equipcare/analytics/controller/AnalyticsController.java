package com.equipcare.analytics.controller;

import com.equipcare.analytics.service.ForecastService;
import com.equipcare.analytics.service.HealthService;
import com.equipcare.analytics.vo.EquipmentForecastVO;
import com.equipcare.analytics.vo.HealthVO;
import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备健康分与风险预警。
 */
@Tag(name = "50. 健康分析", description = "设备健康分构成、走势与风险预警")
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final HealthService healthService;
    private final ForecastService forecastService;

    public AnalyticsController(HealthService healthService, ForecastService forecastService) {
        this.healthService = healthService;
        this.forecastService = forecastService;
    }

    @Operation(summary = "预测性维护总览", description = "MTBF/MTTR（90 天窗口）+ 健康分线性外推 7 天，按预测分升序（最危险在前）")
    @GetMapping("/predictive")
    @PreAuthorize("hasAuthority('health:view')")
    public Result<List<EquipmentForecastVO>> predictive() {
        return Result.ok(forecastService.forecastAll());
    }

    @Operation(summary = "设备健康分", description = "返回总分、五项扣分/加分明细与近 30 天走势")
    @GetMapping("/health/{equipmentId}")
    @PreAuthorize("hasAuthority('health:view')")
    public Result<HealthVO> health(@PathVariable Long equipmentId) {
        return Result.ok(healthService.current(equipmentId));
    }

    @Operation(summary = "重算设备健康分", description = "计算并写入当日快照，同时回写设备台账冗余健康分")
    @PostMapping("/health/{equipmentId}/recalculate")
    @PreAuthorize("hasAuthority('health:view') and hasAnyRole('ADMIN','EQUIP_ADMIN')")
    @AuditLog(module = "健康分析", action = "重算健康分")
    public Result<HealthVO> recalculate(@PathVariable Long equipmentId) {
        return Result.ok(healthService.recalculate(equipmentId));
    }

    @Operation(summary = "批量重算全部设备健康分")
    @PostMapping("/health/recalculate-all")
    @PreAuthorize("hasAuthority('health:view') and hasAnyRole('ADMIN','EQUIP_ADMIN')")
    @AuditLog(module = "健康分析", action = "批量重算")
    public Result<Integer> recalculateAll() {
        return Result.ok(healthService.recalculateAll());
    }

    @Operation(summary = "健康风险设备列表", description = "默认阈值 75 分，与第一阶段定义的亚健康线一致")
    @GetMapping("/health/at-risk")
    @PreAuthorize("hasAuthority('health:view')")
    public Result<List<HealthVO>> atRisk(@RequestParam(defaultValue = "75") int threshold,
                                         @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(healthService.atRisk(threshold, limit));
    }
}
