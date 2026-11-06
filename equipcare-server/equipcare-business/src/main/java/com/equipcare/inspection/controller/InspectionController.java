package com.equipcare.inspection.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.common.result.ResultCode;
import com.equipcare.inspection.dto.AnomalyQuery;
import com.equipcare.inspection.dto.AnomalyWorkOrderDTO;
import com.equipcare.inspection.dto.TaskCreateDTO;
import com.equipcare.inspection.dto.TaskQuery;
import com.equipcare.inspection.dto.TaskStartDTO;
import com.equipcare.inspection.dto.TaskSubmitDTO;
import com.equipcare.inspection.service.InspectionTaskService;
import com.equipcare.inspection.vo.AnomalyVO;
import com.equipcare.inspection.vo.CalendarDayVO;
import com.equipcare.inspection.vo.InspectionStatsVO;
import com.equipcare.inspection.vo.TaskDetailVO;
import com.equipcare.inspection.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 巡检执行、异常处理、日历与统计。
 */
@Tag(name = "30. 巡检执行", description = "巡检任务执行、提交、异常转工单、日历与统计")
@RestController
@RequestMapping("/api/inspection")
public class InspectionController {

    private final InspectionTaskService inspectionTaskService;

    public InspectionController(InspectionTaskService inspectionTaskService) {
        this.inspectionTaskService = inspectionTaskService;
    }

    @Operation(summary = "巡检任务分页", description = "assigneeId 传 0 表示只看我的任务")
    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('inspect:task:view')")
    public Result<PageResult<TaskVO>> pageTasks(@Valid TaskQuery query) {
        return Result.ok(inspectionTaskService.page(query));
    }

    @Operation(summary = "手工发起巡检", description = "扫码巡检或临时补检；模板为空时取设备类别默认模板")
    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('inspect:task:execute')")
    @AuditLog(module = "巡检任务", action = "手工发起")
    public Result<Long> createTask(@Valid @RequestBody TaskCreateDTO dto) {
        return Result.ok(inspectionTaskService.createManual(dto));
    }

    @Operation(summary = "巡检任务详情", description = "含设备摘要、检查项标准、已填结果与异常现场照片")
    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('inspect:task:view')")
    public Result<TaskDetailVO> taskDetail(@PathVariable Long id) {
        return Result.ok(inspectionTaskService.detail(id));
    }

    @Operation(summary = "开始巡检", description = "传入二维码令牌可校验是否现场扫码，防止远程代签")
    @PostMapping("/tasks/{id}/start")
    @PreAuthorize("hasAuthority('inspect:task:execute')")
    @AuditLog(module = "巡检任务", action = "开始执行")
    public Result<Void> start(@PathVariable Long id, @RequestBody(required = false) TaskStartDTO dto) {
        inspectionTaskService.start(id, dto);
        return Result.ok();
    }

    @Operation(summary = "提交巡检", description = "逐项三态判定；必检项不可跳过，异常须填原因与证据")
    @PostMapping("/tasks/{id}/submit")
    @PreAuthorize("hasAuthority('inspect:task:execute')")
    @AuditLog(module = "巡检任务", action = "提交")
    public Result<Integer> submit(@PathVariable Long id, @Valid @RequestBody TaskSubmitDTO dto) {
        return Result.ok(inspectionTaskService.submit(id, dto));
    }

    @Operation(summary = "任务设备二维码", description = "按任务权限展示设备二维码，巡检人无需设备二维码权限")
    @GetMapping(value = "/tasks/{id}/equipment-qrcode.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('inspect:task:view')")
    public ResponseEntity<byte[]> equipmentQr(@PathVariable Long id,
                                              @RequestParam(defaultValue = "320") int size,
                                              @RequestParam(defaultValue = "1") int margin) {
        byte[] png = inspectionTaskService.equipmentQrPng(id, size, margin);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(png);
    }

    @Operation(summary = "异常单分页")
    @GetMapping("/anomalies")
    @PreAuthorize("hasAuthority('inspect:anomaly:view')")
    public Result<PageResult<AnomalyVO>> pageAnomalies(@Valid AnomalyQuery query) {
        return Result.ok(inspectionTaskService.pageAnomaly(query));
    }

    @Operation(summary = "关闭异常单", description = "已转工单的异常不能直接关闭，需随工单闭环")
    @PostMapping("/anomalies/{id}/close")
    @PreAuthorize("hasAuthority('inspect:anomaly:close')")
    @AuditLog(module = "异常单", action = "关闭")
    public Result<Void> closeAnomaly(@PathVariable Long id,
                                     @RequestParam(required = false) String remark) {
        inspectionTaskService.closeAnomaly(id, remark);
        return Result.ok();
    }

    @Operation(summary = "异常转维修工单", description = "工单只记录关联，设备/任务/检查项/照片由异常单联查")
    @PostMapping("/anomalies/{id}/work-order")
    @PreAuthorize("hasAuthority('inspect:anomaly:convert')")
    @AuditLog(module = "异常单", action = "转工单")
    public Result<Long> convertAnomaly(@PathVariable Long id,
                                       @RequestBody(required = false) AnomalyWorkOrderDTO dto) {
        return Result.ok(inspectionTaskService.convertAnomaly(id, dto == null ? new AnomalyWorkOrderDTO() : dto));
    }

    @Operation(summary = "巡检日历", description = "month 格式 yyyy-MM，默认当月；按计划日汇总任务状态")
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('inspect:calendar:view')")
    public Result<List<CalendarDayVO>> calendar(@RequestParam(required = false) String month,
                                                @RequestParam(required = false) Long assigneeId) {
        return Result.ok(inspectionTaskService.calendar(parseMonth(month), assigneeId));
    }

    @Operation(summary = "巡检统计", description = "完成率、异常率、状态分布、计划维度与每日趋势")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('inspect:stats:view')")
    public Result<InspectionStatsVO> stats(@RequestParam(defaultValue = "30") int days,
                                           @RequestParam(required = false) Long assigneeId) {
        return Result.ok(inspectionTaskService.stats(days, assigneeId));
    }

    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "月份格式应为 yyyy-MM，例如 2026-10");
        }
    }
}
