package com.equipcare.analytics.mapper;

import com.equipcare.analytics.dto.HealthMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 健康分计算的统计口径查询。
 *
 * <p>全部按「近 N 天窗口」聚合，一条 SQL 取回全部指标，避免多次往返。
 */
@Mapper
public interface HealthStatMapper {

    /** 工单指标：总数、紧急数、超时数（近 windowDays 天） */
    @Select("""
            SELECT
              COUNT(1) AS workOrderCount,
              COALESCE(SUM(CASE WHEN level IN ('P1','P2') THEN 1 ELSE 0 END), 0) AS urgentWorkOrderCount,
              COALESCE(SUM(CASE WHEN sla_status = 'OVERDUE' OR overdue_flag = 1 THEN 1 ELSE 0 END), 0) AS overdueWorkOrderCount
            FROM wo_work_order
            WHERE equipment_id = #{equipmentId}
              AND report_time >= DATE_SUB(NOW(), INTERVAL #{windowDays} DAY)
            """)
    HealthMetrics selectWorkOrderMetrics(@Param("equipmentId") Long equipmentId,
                                         @Param("windowDays") int windowDays);

    /** 异常指标：总数、严重数（近 windowDays 天） */
    @Select("""
            SELECT
              COUNT(1) AS anomalyCount,
              COALESCE(SUM(CASE WHEN level = 'HIGH' THEN 1 ELSE 0 END), 0) AS highAnomalyCount
            FROM insp_anomaly
            WHERE equipment_id = #{equipmentId}
              AND report_time >= DATE_SUB(NOW(), INTERVAL #{windowDays} DAY)
            """)
    HealthMetrics selectAnomalyMetrics(@Param("equipmentId") Long equipmentId,
                                       @Param("windowDays") int windowDays);

    /** 巡检指标：逾期数、正常完成数（近 windowDays 天） */
    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN status = 'EXPIRED' OR overdue_flag = 1 THEN 1 ELSE 0 END), 0) AS overdueInspectCount,
              COALESCE(SUM(CASE WHEN status = 'SUBMITTED' AND result = 'NORMAL' THEN 1 ELSE 0 END), 0) AS normalInspectCount
            FROM insp_task
            WHERE equipment_id = #{equipmentId}
              AND plan_date >= DATE_SUB(CURDATE(), INTERVAL #{windowDays} DAY)
            """)
    HealthMetrics selectInspectMetrics(@Param("equipmentId") Long equipmentId,
                                       @Param("windowDays") int windowDays);

    /** v2 因素：未完成维修工单数（不限窗口，在途即负担） */
    @Select("""
            SELECT COALESCE(SUM(CASE WHEN status IN
                     ('PENDING_ASSIGN', 'PENDING_ACCEPT', 'IN_PROGRESS', 'PENDING_REVIEW')
                     THEN 1 ELSE 0 END), 0) AS openWorkOrderCount
            FROM wo_work_order
            WHERE equipment_id = #{equipmentId}
            """)
    HealthMetrics selectOpenOrderMetrics(@Param("equipmentId") Long equipmentId);

    /** v2 因素：最近一次完成维修的时间（从未完成为 NULL） */
    @Select("""
            SELECT MAX(actual_finish_time) FROM wo_work_order
             WHERE equipment_id = #{equipmentId} AND status = 'COMPLETED'
            """)
    LocalDateTime selectLastFinishTime(@Param("equipmentId") Long equipmentId);
}
