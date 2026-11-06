package com.equipcare.asset.mapper;

import com.equipcare.asset.vo.EquipmentInspectHistoryVO;
import com.equipcare.asset.vo.EquipmentRepairHistoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备档案只读历史查询。
 *
 * <p>只读跨域联表，不写任何表、不调用其它模块的 Service：
 * <ul>
 *   <li>维修历史读 {@code wo_work_order}（工单表对设备只做外键引用，读操作无副作用）；</li>
 *   <li>巡检历史读 {@code insp_task}。</li>
 * </ul>
 * 这样 asset 模块无需依赖 workorder / inspection 模块，模块边界保持单向。
 */
@Mapper
public interface EquipmentHistoryMapper {

    @Select("""
            SELECT w.id AS work_order_id, w.order_no, w.title, w.fault_type, w.fault_desc,
                   w.level, w.status, w.report_time, w.actual_finish_time AS finish_time,
                   w.repair_summary, w.labor_hours, w.verify_result, w.quality_score, w.spare_cost,
                   u.real_name AS assignee_name
            FROM wo_work_order w
            LEFT JOIN sys_user u ON u.id = w.assignee_id
            WHERE w.equipment_id = #{equipmentId}
            ORDER BY w.report_time DESC, w.id DESC
            LIMIT #{limit}
            """)
    List<EquipmentRepairHistoryVO> selectRepairHistory(@Param("equipmentId") Long equipmentId,
                                                       @Param("limit") int limit);

    @Select("""
            SELECT t.id AS task_id, t.task_code, t.plan_date, t.status, t.result, t.abnormal_count,
                   t.assignee_id, u.real_name AS assignee_name,
                   t.start_time, t.submit_time, t.finish_time, t.sla_due_time, t.overdue_flag
            FROM insp_task t
            LEFT JOIN sys_user u ON u.id = t.assignee_id
            WHERE t.equipment_id = #{equipmentId}
            ORDER BY t.plan_date DESC, t.id DESC
            LIMIT #{limit}
            """)
    List<EquipmentInspectHistoryVO> selectInspectHistory(@Param("equipmentId") Long equipmentId,
                                                         @Param("limit") int limit);

    /** 设备历史工单总数，用于详情页角标与统计 */
    @Select("SELECT COUNT(1) FROM wo_work_order WHERE equipment_id = #{equipmentId}")
    int countRepairHistory(@Param("equipmentId") Long equipmentId);

    /** 设备历史巡检总数 */
    @Select("SELECT COUNT(1) FROM insp_task WHERE equipment_id = #{equipmentId}")
    int countInspectHistory(@Param("equipmentId") Long equipmentId);
}
