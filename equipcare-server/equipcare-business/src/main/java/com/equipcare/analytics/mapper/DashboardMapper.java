package com.equipcare.analytics.mapper;

import com.equipcare.analytics.vo.DashboardChartsVO.MonthValue;
import com.equipcare.analytics.vo.DashboardChartsVO.NameValue;
import com.equipcare.analytics.vo.DashboardSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 驾驶舱聚合查询。时间范围为 [start, end) 半开区间，由 Service 统一换算；
 * 全部口径注释见 {@link DashboardSummaryVO}，SQL 只做聚合不做口径加工。
 */
@Mapper
public interface DashboardMapper {

    /** 设备状态分布（当前快照，不含已删除） */
    @Select("""
            SELECT status AS name, COUNT(1) AS value
            FROM eq_equipment
            WHERE deleted = 0
            GROUP BY status
            """)
    List<NameValue> selectEquipmentStatusDist();

    /** 14 指标中的设备四项（总数 / 正常 / 故障 / 停用） */
    @Select("""
            SELECT COUNT(1) AS equipmentTotal,
              COALESCE(SUM(status IN ('IN_USE', 'IDLE')), 0)  AS equipmentNormal,
              COALESCE(SUM(status = 'REPAIRING'), 0)           AS equipmentFault,
              COALESCE(SUM(status = 'SCRAPPED'), 0)            AS equipmentDisabled
            FROM eq_equipment
            WHERE deleted = 0
            """)
    DashboardSummaryVO selectEquipmentCounts();

    /** 巡检完成率与异常发现数（周期内） */
    @Select("""
            SELECT
              CASE WHEN COUNT(1) = 0 THEN NULL
                   ELSE ROUND(COALESCE(SUM(status = 'SUBMITTED'), 0) * 100.0 / COUNT(1), 1) END
                AS inspectionCompletionRate,
              (SELECT COUNT(1) FROM insp_anomaly
                WHERE create_time >= #{start} AND create_time < #{end})
                AS anomalyCount
            FROM insp_task
            WHERE plan_date >= #{startDate} AND plan_date < #{endDateExclusive}
            """)
    DashboardSummaryVO selectInspectionMetrics(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               @Param("startDate") String startDate,
                                               @Param("endDateExclusive") String endDateExclusive);

    /** 工单数量三项 + MTTR + MTBF + 维修成本（周期内，按报告/完成时间归属） */
    @Select("""
            SELECT
              (SELECT COUNT(1) FROM wo_work_order
                WHERE status NOT IN ('COMPLETED', 'CANCELLED'))
                AS workOrderPending,
              COALESCE(SUM(status = 'COMPLETED' AND actual_finish_time >= #{start}
                           AND actual_finish_time < #{end}), 0)  AS workOrderCompleted,
              COALESCE(SUM(overdue_flag = 1 AND report_time >= #{start}
                           AND report_time < #{end}), 0)         AS workOrderOverdue,
              AVG(CASE WHEN status = 'COMPLETED'
                        AND start_repair_time IS NOT NULL
                        AND actual_finish_time >= #{start} AND actual_finish_time < #{end}
                       THEN TIMESTAMPDIFF(MINUTE, start_repair_time, actual_finish_time) END)
                AS mttrMinutes,
              CASE WHEN SUM(status <> 'CANCELLED' AND report_time >= #{start}
                            AND report_time < #{end}) = 0 THEN NULL
                   ELSE ROUND(#{periodHours} * #{normalEquipments}
                        / SUM(status <> 'CANCELLED' AND report_time >= #{start}
                              AND report_time < #{end}), 1) END
                AS mtbfHours,
              COALESCE(SUM(CASE WHEN status = 'COMPLETED'
                             AND actual_finish_time >= #{start} AND actual_finish_time < #{end}
                             THEN spare_cost END), 0) AS maintenanceCost
            FROM wo_work_order
            """)
    DashboardSummaryVO selectWorkOrderMetrics(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("periodHours") double periodHours,
                                              @Param("normalEquipments") long normalEquipments);

    /** 备件消耗金额：周期内出库流水金额合计 */
    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM sp_stock_txn
            WHERE txn_type = 'OUT'
              AND txn_time >= #{start} AND txn_time < #{end}
            """)
    BigDecimal selectSpareConsumption(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /** 图 2：工单状态分布（周期内，按报告时间） */
    @Select("""
            SELECT status AS name, COUNT(1) AS value
            FROM wo_work_order
            WHERE report_time >= #{start} AND report_time < #{end}
            GROUP BY status
            ORDER BY value DESC
            """)
    List<NameValue> selectWorkOrderStatusDist(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /** 图 3：月度故障趋势（非取消工单按报告月份计数） */
    @Select("""
            SELECT DATE_FORMAT(report_time, '%Y-%m') AS month, COUNT(1) AS total, 0 AS part
            FROM wo_work_order
            WHERE status <> 'CANCELLED'
              AND report_time >= #{start} AND report_time < #{end}
            GROUP BY month ORDER BY month
            """)
    List<MonthValue> selectMonthlyFaultTrend(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /** 图 4：巡检完成趋势（total=当月任务数，part=已提交数） */
    @Select("""
            SELECT DATE_FORMAT(plan_date, '%Y-%m') AS month,
              COUNT(1) AS total,
              COALESCE(SUM(status = 'SUBMITTED'), 0) AS part
            FROM insp_task
            WHERE plan_date >= #{startDate} AND plan_date < #{endDateExclusive}
            GROUP BY month ORDER BY month
            """)
    List<MonthValue> selectMonthlyInspectionTrend(@Param("startDate") String startDate,
                                                  @Param("endDateExclusive") String endDateExclusive);

    /** 图 5：维修成本趋势（完成工单备件费按完成月份合计） */
    @Select("""
            SELECT DATE_FORMAT(actual_finish_time, '%Y-%m') AS month,
              COALESCE(SUM(spare_cost), 0) AS total, 0 AS part
            FROM wo_work_order
            WHERE status = 'COMPLETED'
              AND actual_finish_time >= #{start} AND actual_finish_time < #{end}
            GROUP BY month ORDER BY month
            """)
    List<MonthValue> selectMonthlyCostTrend(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /** 图 6：故障类型排行 */
    @Select("""
            SELECT COALESCE(NULLIF(fault_type, ''), '未分类') AS name, COUNT(1) AS value
            FROM wo_work_order
            WHERE status <> 'CANCELLED'
              AND report_time >= #{start} AND report_time < #{end}
            GROUP BY name ORDER BY value DESC LIMIT 10
            """)
    List<NameValue> selectFaultTypeRank(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /** 图 7：设备故障排行 */
    @Select("""
            SELECT e.equipment_name AS name, COUNT(1) AS value
            FROM wo_work_order w
            JOIN eq_equipment e ON e.id = w.equipment_id AND e.deleted = 0
            WHERE w.status <> 'CANCELLED'
              AND w.report_time >= #{start} AND w.report_time < #{end}
            GROUP BY w.equipment_id, e.equipment_name
            ORDER BY value DESC LIMIT 10
            """)
    List<NameValue> selectEquipmentFaultRank(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /** 图 8：维修人员工作量排行（周期内完成工单数） */
    @Select("""
            SELECT u.real_name AS name, COUNT(1) AS value
            FROM wo_work_order w
            JOIN sys_user u ON u.id = w.assignee_id AND u.deleted = 0
            WHERE w.status = 'COMPLETED'
              AND w.actual_finish_time >= #{start} AND w.actual_finish_time < #{end}
            GROUP BY w.assignee_id, u.real_name
            ORDER BY value DESC LIMIT 10
            """)
    List<NameValue> selectRepairmanRank(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /** 图 9：备件消耗排行（周期内出库金额按备件合计 Top 10，驾驶舱第五层用） */
    @Select("""
            SELECT p.part_name AS name, COALESCE(SUM(t.amount), 0) AS value
            FROM sp_stock_txn t
            JOIN sp_part p ON p.id = t.part_id AND p.deleted = 0
            WHERE t.txn_type = 'OUT'
              AND t.txn_time >= #{start} AND t.txn_time < #{end}
            GROUP BY t.part_id, p.part_name
            ORDER BY value DESC
            LIMIT 10
            """)
    List<NameValue> selectSparePartRank(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
