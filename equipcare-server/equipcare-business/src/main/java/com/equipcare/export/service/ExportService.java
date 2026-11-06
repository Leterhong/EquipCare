package com.equipcare.export.service;

import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;

/**
 * Excel 导出服务（Apache POI）。
 *
 * <p>权限模型：每个导出接口要求 {@code analytics:export}（报表导出 BUTTON，
 * 仅管理角色持有）+ 对应业务域的查看权限，普通角色不可导出；
 * 行数上限 5000，超出提示缩小时间范围。
 */
public interface ExportService {

    /** 设备清单 */
    void devices(HttpServletResponse response);

    /** 巡检记录（可选时间范围，按计划日期） */
    void inspections(HttpServletResponse response, LocalDate startDate, LocalDate endDate);

    /** 维修工单（可选时间范围，按报告时间） */
    void workOrders(HttpServletResponse response, LocalDate startDate, LocalDate endDate);

    /** 备件库存 */
    void inventory(HttpServletResponse response);

    /** 维修成本统计（可选时间范围，按完成时间） */
    void costStats(HttpServletResponse response, LocalDate startDate, LocalDate endDate);
}
