package com.equipcare.analytics.service.impl;

import com.equipcare.analytics.mapper.DashboardMapper;
import com.equipcare.analytics.service.DashboardService;
import com.equipcare.analytics.vo.DashboardChartsVO;
import com.equipcare.analytics.vo.DashboardSummaryVO;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 驾驶舱实现：三次部分聚合 + 备件消耗单独一查，合并为最终 VO。
 *
 * <p>时间范围约束：缺省统计当月、起不晚于止、跨度不超过 366 天——
 * 防止一次全表聚合把驾驶舱拖垮。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 最长统计跨度（天）：月度趋势图在一年跨度内才有可读性 */
    private static final long MAX_RANGE_DAYS = 366;

    private final DashboardMapper dashboardMapper;

    @Override
    public DashboardSummaryVO summary(LocalDate startDate, LocalDate endDate) {
        LocalDate[] defaults = withMonthlyDefault(startDate, endDate);
        startDate = defaults[0];
        endDate = defaults[1];
        LocalDateTime[] range = rangeOf(startDate, endDate);
        LocalDateTime start = range[0];
        LocalDateTime end = range[1];
        double periodHours = Math.max(1, ChronoUnit.HOURS.between(start, end));

        DashboardSummaryVO vo = new DashboardSummaryVO();

        DashboardSummaryVO equipment = dashboardMapper.selectEquipmentCounts();
        vo.setEquipmentTotal(equipment.getEquipmentTotal());
        vo.setEquipmentNormal(equipment.getEquipmentNormal());
        vo.setEquipmentFault(equipment.getEquipmentFault());
        vo.setEquipmentDisabled(equipment.getEquipmentDisabled());
        if (vo.getEquipmentTotal() != null && vo.getEquipmentTotal() > 0) {
            vo.setEquipmentHealthRate(percentage(vo.getEquipmentNormal(), vo.getEquipmentTotal()));
        }

        DashboardSummaryVO inspection = dashboardMapper.selectInspectionMetrics(
                start, end, startDate.toString(), endDate.plusDays(1).toString());
        vo.setInspectionCompletionRate(inspection.getInspectionCompletionRate());
        vo.setAnomalyCount(inspection.getAnomalyCount());

        DashboardSummaryVO workOrder = dashboardMapper.selectWorkOrderMetrics(
                start, end, periodHours, vo.getEquipmentNormal() == null ? 0 : vo.getEquipmentNormal());
        vo.setWorkOrderPending(workOrder.getWorkOrderPending());
        vo.setWorkOrderCompleted(workOrder.getWorkOrderCompleted());
        vo.setWorkOrderOverdue(workOrder.getWorkOrderOverdue());
        vo.setMttrMinutes(workOrder.getMttrMinutes());
        vo.setMtbfHours(workOrder.getMtbfHours());
        vo.setMaintenanceCost(workOrder.getMaintenanceCost());

        vo.setSpareConsumptionAmount(dashboardMapper.selectSpareConsumption(start, end));
        return vo;
    }

    @Override
    public DashboardChartsVO charts(LocalDate startDate, LocalDate endDate) {
        LocalDate[] defaults = withMonthlyDefault(startDate, endDate);
        startDate = defaults[0];
        endDate = defaults[1];
        LocalDateTime[] range = rangeOf(startDate, endDate);
        LocalDateTime start = range[0];
        LocalDateTime end = range[1];
        String startDateText = startDate.toString();
        String endDateExclusive = endDate.plusDays(1).toString();

        DashboardChartsVO vo = new DashboardChartsVO();
        vo.setEquipmentStatusDist(dashboardMapper.selectEquipmentStatusDist());
        vo.setWorkOrderStatusDist(dashboardMapper.selectWorkOrderStatusDist(start, end));
        vo.setMonthlyFaultTrend(dashboardMapper.selectMonthlyFaultTrend(start, end));
        vo.setMonthlyInspectionTrend(dashboardMapper.selectMonthlyInspectionTrend(startDateText, endDateExclusive));
        vo.setMonthlyCostTrend(dashboardMapper.selectMonthlyCostTrend(start, end));
        vo.setFaultTypeRank(dashboardMapper.selectFaultTypeRank(start, end));
        vo.setEquipmentFaultRank(dashboardMapper.selectEquipmentFaultRank(start, end));
        vo.setRepairmanRank(dashboardMapper.selectRepairmanRank(start, end));
        vo.setSparePartRank(dashboardMapper.selectSparePartRank(start, end));
        return vo;
    }

    /** 未传参时默认统计当月（1 号 ~ 今天），便于前端首次进入直接出数 */
    private LocalDate[] withMonthlyDefault(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            LocalDate today = LocalDate.now();
            return new LocalDate[]{today.withDayOfMonth(1), today};
        }
        return new LocalDate[]{startDate, endDate};
    }

    private LocalDateTime[] rangeOf(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "统计起止日期必填");
        }
        if (startDate.isAfter(endDate)) {
            throw new BizException(ResultCode.BAD_REQUEST, "起始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_RANGE_DAYS) {
            throw new BizException(ResultCode.BAD_REQUEST, "统计跨度不能超过 366 天");
        }
        return new LocalDateTime[]{startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()};
    }

    private BigDecimal percentage(Long part, Long total) {
        return BigDecimal.valueOf(part * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP);
    }
}
