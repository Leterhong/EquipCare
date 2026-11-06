package com.equipcare.export.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * Excel 导出聚合查询。
 *
 * <p>统一返回 {@code Map<String, Object>}（key 为 SQL 别名驼峰形式），
 * 列顺序由 {@code ExportServiceImpl} 的显式列定义表保证，与 Map 内部顺序无关；
 * 行数上限 5000——导出是给人看的报表，超量请缩小时间范围而不是导出全库。
 */
@Mapper
public interface ExportMapper {

    /** 设备清单（当前快照） */
    @Select("""
            SELECT e.asset_no AS assetNo, e.equipment_name AS equipmentName,
                   c.category_name AS categoryName, l.full_path AS locationPath,
                   e.brand AS brand, e.model AS model,
                   e.status AS status, u.real_name AS ownerName,
                   e.last_inspect_time AS lastInspectTime, e.next_inspect_time AS nextInspectTime
            FROM eq_equipment e
            LEFT JOIN eq_category c ON c.id = e.category_id
            LEFT JOIN eq_location l ON l.id = e.location_id
            LEFT JOIN sys_user u ON u.id = e.responsible_user_id
            WHERE e.deleted = 0
            ORDER BY e.asset_no
            LIMIT 5000
            """)
    java.util.List<Map<String, Object>> selectDevices();

    /** 巡检记录（周期内，按计划日期） */
    @Select("""
            SELECT t.task_code AS taskCode, e.equipment_name AS equipmentName,
                   u.real_name AS assigneeName, t.plan_date AS planDate,
                   t.status AS status, t.result AS result,
                   t.abnormal_count AS abnormalCount, t.submit_time AS submitTime,
                   t.overdue_flag AS overdueFlag
            FROM insp_task t
            JOIN eq_equipment e ON e.id = t.equipment_id
            LEFT JOIN sys_user u ON u.id = t.assignee_id
            WHERE (#{startDate} IS NULL OR t.plan_date >= #{startDate})
              AND (#{endDateExclusive} IS NULL OR t.plan_date < #{endDateExclusive})
            ORDER BY t.plan_date DESC, t.task_code
            LIMIT 5000
            """)
    java.util.List<Map<String, Object>> selectInspections(@Param("startDate") String startDate,
                                                          @Param("endDateExclusive") String endDateExclusive);

    /** 维修工单（周期内，按报告时间） */
    @Select("""
            SELECT w.order_no AS orderNo, w.title AS title,
                   e.equipment_name AS equipmentName, w.level AS level,
                   w.status AS status, r.real_name AS reporterName,
                   a.real_name AS assigneeName, w.report_time AS reportTime,
                   w.actual_finish_time AS actualFinishTime,
                   w.overdue_flag AS overdueFlag, w.spare_cost AS spareCost
            FROM wo_work_order w
            JOIN eq_equipment e ON e.id = w.equipment_id
            LEFT JOIN sys_user r ON r.id = w.reporter_id
            LEFT JOIN sys_user a ON a.id = w.assignee_id
            WHERE (#{startDate} IS NULL OR w.report_time >= #{start})
              AND (#{end} IS NULL OR w.report_time < #{end})
            ORDER BY w.report_time DESC
            LIMIT 5000
            """)
    java.util.List<Map<String, Object>> selectWorkOrders(@Param("startDate") String startDate,
                                                         @Param("endDateExclusive") String endDateExclusive,
                                                         @Param("start") String start,
                                                         @Param("end") String end);

    /** 备件库存（当前快照） */
    @Select("""
            SELECT p.part_no AS partNo, p.part_name AS partName,
               p.category AS categoryName, p.spec AS spec,
               p.unit AS unit, p.current_stock AS currentStock,
               p.safety_stock AS safetyStock, p.unit_price AS unitPrice,
               p.warehouse_location AS warehouseLocation, p.supplier AS supplier,
               p.status AS status
            FROM sp_part p
            WHERE p.deleted = 0
            ORDER BY p.part_no
            LIMIT 5000
            """)
    java.util.List<Map<String, Object>> selectInventory();

    /** 维修成本统计（周期内完成的工单，按完成时间） */
    @Select("""
            SELECT w.order_no AS orderNo, e.equipment_name AS equipmentName,
                   a.real_name AS assigneeName, w.actual_finish_time AS actualFinishTime,
                   w.labor_hours AS laborHours, w.spare_cost AS spareCost,
                   w.quality_score AS qualityScore
            FROM wo_work_order w
            JOIN eq_equipment e ON e.id = w.equipment_id
            LEFT JOIN sys_user a ON a.id = w.assignee_id
            WHERE w.status = 'COMPLETED'
              AND (#{startDate} IS NULL OR w.actual_finish_time >= #{start})
              AND (#{end} IS NULL OR w.actual_finish_time < #{end})
            ORDER BY w.actual_finish_time DESC
            LIMIT 5000
            """)
    java.util.List<Map<String, Object>> selectCostStats(@Param("startDate") String startDate,
                                                        @Param("endDateExclusive") String endDateExclusive,
                                                        @Param("start") String start,
                                                        @Param("end") String end);
}
