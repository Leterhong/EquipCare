package com.equipcare.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.equipcare.inspection.dto.AnomalyQuery;
import com.equipcare.inspection.entity.InspAnomaly;
import com.equipcare.inspection.vo.AnomalyVO;
import com.equipcare.inspection.vo.AttachmentVO;
import com.equipcare.inspection.vo.InspectionStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InspAnomalyMapper extends BaseMapper<InspAnomaly> {

    @Select("SELECT MAX(anomaly_no) FROM insp_anomaly WHERE anomaly_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxNoByPrefix(@Param("prefix") String prefix);

    @Select("SELECT * FROM insp_anomaly WHERE task_id = #{taskId} ORDER BY id ASC")
    List<InspAnomaly> selectByTask(@Param("taskId") Long taskId);

    /**
     * 异常分页：带出设备、来源任务、实测值与工单信息，展示「异常→工单」闭环。
     *
     * <p>实测值来自 insp_task_item 的当前值（同一任务同一检查项唯一），
     * 不在异常表里冗余存一份，避免两处对不上。
     */
    @Select("""
            <script>
            SELECT a.id, a.anomaly_no, a.task_id, t.task_code, a.equipment_id,
                   e.equipment_name, e.asset_no,
                   a.item_name, ti.item_value, a.level, a.description, a.status, a.work_order_id,
                   o.order_no AS work_order_no, o.status AS work_order_status,
                   a.attachment_ids,
                   a.reporter_id, u.real_name AS reporter_name, a.report_time, a.close_time
              FROM insp_anomaly a
              LEFT JOIN eq_equipment e ON e.id = a.equipment_id
              LEFT JOIN insp_task t ON t.id = a.task_id
              LEFT JOIN insp_task_item ti ON ti.task_id = a.task_id AND ti.template_item_id = a.template_item_id
              LEFT JOIN wo_work_order o ON o.id = a.work_order_id
              LEFT JOIN sys_user u ON u.id = a.reporter_id
             WHERE 1 = 1
            <if test="q.equipmentId != null"> AND a.equipment_id = #{q.equipmentId} </if>
            <if test="q.status != null and q.status != ''"> AND a.status = #{q.status} </if>
            <if test="q.level != null and q.level != ''"> AND a.level = #{q.level} </if>
            <if test="q.taskId != null"> AND a.task_id = #{q.taskId} </if>
             ORDER BY a.id DESC
            </script>
            """)
    IPage<AnomalyVO> selectAnomalyPage(IPage<AnomalyVO> page, @Param("q") AnomalyQuery query);

    /** 现场照片：按逗号分隔的文件ID批量取元数据 */
    @Select("""
            SELECT f.id, f.file_name, f.file_size, f.content_type
              FROM sys_file f
             WHERE FIND_IN_SET(f.id, #{ids})
             ORDER BY f.id ASC
            """)
    List<AttachmentVO> selectAttachments(@Param("ids") String ids);

    /** 现场照片：按文件 ID 集合批量取元数据（IN 走主键索引，供列表页批量装配） */
    @Select({
        "<script>",
        "SELECT f.id, f.file_name, f.file_size, f.content_type",
        "  FROM sys_file f",
        " WHERE f.id IN",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        " ORDER BY f.id ASC",
        "</script>",
    })
    List<AttachmentVO> selectAttachmentsByIds(@Param("ids") java.util.Collection<Long> ids);

    /** 异常状态分布（统计页） */
    @Select("""
            SELECT status AS code, COUNT(1) AS count
              FROM insp_anomaly
             WHERE report_time >= #{from} AND report_time < #{to}
             GROUP BY status
            """)
    List<InspectionStatsVO.StatusCount> selectAnomalyStatusCounts(@Param("from") LocalDateTime from,
                                                                 @Param("to") LocalDateTime to);

    /** 异常项目排行（统计页） */
    @Select("""
            SELECT item_name, COUNT(1) AS count
              FROM insp_anomaly
             WHERE report_time >= #{from} AND report_time < #{to}
               AND item_name IS NOT NULL
             GROUP BY item_name
             ORDER BY count DESC
             LIMIT #{limit}
            """)
    List<InspectionStatsVO.ItemStat> selectTopItems(@Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to,
                                                    @Param("limit") int limit);
}
