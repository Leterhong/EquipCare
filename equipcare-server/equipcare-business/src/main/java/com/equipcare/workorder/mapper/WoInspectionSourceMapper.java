package com.equipcare.workorder.mapper;

import com.equipcare.workorder.vo.InspectionSourceVO;
import com.equipcare.workorder.vo.SourceAttachmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单的巡检来源只读查询。
 *
 * <p>放在工单模块自己的 Mapper 里、用原生 SQL 联查 insp_* 表，
 * 是为了避免 workorder 反向依赖 inspection 的 Service —— 模块依赖保持单向：
 * inspection → workorder（转单），workorder 只认表结构，不认对方的 Java 类型。
 */
@Mapper
public interface WoInspectionSourceMapper {

    @Select("""
            SELECT a.id AS anomaly_id, a.anomaly_no, a.item_name,
                   a.description AS anomaly_description, a.level AS anomaly_level,
                   a.attachment_ids, a.report_time,
                   t.id AS task_id, t.task_code, t.plan_date,
                   a.equipment_id, e.equipment_name, e.asset_no,
                   ti.item_value,
                   u.real_name AS reporter_name
              FROM insp_anomaly a
              LEFT JOIN insp_task t ON t.id = a.task_id
              LEFT JOIN insp_task_item ti ON ti.task_id = a.task_id AND ti.template_item_id = a.template_item_id
              LEFT JOIN sys_user u ON u.id = a.reporter_id
              LEFT JOIN eq_equipment e ON e.id = a.equipment_id
             WHERE a.id = #{anomalyId}
            """)
    InspectionSourceVO selectByAnomalyId(@Param("anomalyId") Long anomalyId);

    /**
     * 同一张工单下的全部巡检异常。
     *
     * <p>同设备同故障类型已有在途工单时，新异常会合并到该工单（避免工单风暴），
     * 此时 {@code wo_work_order.anomaly_id} 只记住第一张异常单，后续异常靠
     * {@code insp_anomaly.work_order_id} 归属。工单详情必须把这一组异常都查出来，
     * 否则合并进来的现场描述与照片就看不到了。
     */
    @Select("""
            SELECT a.id AS anomaly_id, a.anomaly_no, a.item_name,
                   a.description AS anomaly_description, a.level AS anomaly_level,
                   a.attachment_ids, a.report_time,
                   t.id AS task_id, t.task_code, t.plan_date,
                   a.equipment_id, e.equipment_name, e.asset_no,
                   ti.item_value,
                   u.real_name AS reporter_name
              FROM insp_anomaly a
              LEFT JOIN insp_task t ON t.id = a.task_id
              LEFT JOIN insp_task_item ti ON ti.task_id = a.task_id AND ti.template_item_id = a.template_item_id
              LEFT JOIN sys_user u ON u.id = a.reporter_id
              LEFT JOIN eq_equipment e ON e.id = a.equipment_id
             WHERE a.work_order_id = #{orderId}
             ORDER BY a.id ASC
            """)
    List<InspectionSourceVO> selectByWorkOrderId(@Param("orderId") Long orderId);

    @Select("""
            SELECT f.id, f.file_name, f.file_size, f.content_type
              FROM sys_file f
             WHERE FIND_IN_SET(f.id, #{ids})
             ORDER BY f.id ASC
            """)
    List<SourceAttachmentVO> selectAttachments(@Param("ids") String ids);
}
