package com.equipcare.analytics.service;

import com.equipcare.analytics.vo.DashboardChartsVO;
import com.equipcare.analytics.vo.DashboardSummaryVO;

import java.time.LocalDate;

/**
 * 运维数据驾驶舱。所有指标与图表均由后端实时聚合，接口层不做任何写死数据。
 */
public interface DashboardService {

    /**
     * 14 项指标总览。
     *
     * @param startDate 起始日（含）
     * @param endDate   结束日（含）
     */
    DashboardSummaryVO summary(LocalDate startDate, LocalDate endDate);

    /**
     * 八张图表数据。
     *
     * @param startDate 起始日（含）
     * @param endDate   结束日（含）
     */
    DashboardChartsVO charts(LocalDate startDate, LocalDate endDate);
}
