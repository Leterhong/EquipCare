package com.equipcare.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.inspection.entity.InspPlanEquipment;
import com.equipcare.inspection.vo.PlanEquipmentVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InspPlanEquipmentMapper extends BaseMapper<InspPlanEquipment> {

    @Delete("DELETE FROM insp_plan_equipment WHERE plan_id = #{planId}")
    int deleteByPlan(@Param("planId") Long planId);

    /**
     * 幂等写入快照：同一计划同一设备只保留一行。
     *
     * <p>用 INSERT IGNORE 而不是先查后插，避免并发保存或定时器补设备时撞唯一索引。
     */
    @Insert("""
            INSERT IGNORE INTO insp_plan_equipment (plan_id, equipment_id, seq_no)
            VALUES (#{planId}, #{equipmentId}, #{seqNo})
            """)
    int insertIgnore(@Param("planId") Long planId, @Param("equipmentId") Long equipmentId,
                     @Param("seqNo") int seqNo);

    @Select("""
            SELECT pe.id, pe.equipment_id, pe.seq_no, pe.assignee_id,
                   e.asset_no, e.equipment_name, e.status AS equipment_status,
                   c.category_name, l.full_path AS location_path,
                   u.real_name AS assignee_name
              FROM insp_plan_equipment pe
              INNER JOIN eq_equipment e ON e.id = pe.equipment_id AND e.deleted = 0
              LEFT JOIN eq_category c ON c.id = e.category_id
              LEFT JOIN eq_location l ON l.id = e.location_id
              LEFT JOIN sys_user u ON u.id = pe.assignee_id
             WHERE pe.plan_id = #{planId}
             ORDER BY pe.seq_no ASC, pe.id ASC
            """)
    List<PlanEquipmentVO> selectByPlan(@Param("planId") Long planId);

    @Select("SELECT equipment_id FROM insp_plan_equipment WHERE plan_id = #{planId} ORDER BY seq_no ASC, id ASC")
    List<Long> selectEquipmentIds(@Param("planId") Long planId);

    @Select("SELECT COUNT(1) FROM insp_plan_equipment WHERE plan_id = #{planId}")
    int countByPlan(@Param("planId") Long planId);

    /**
     * 移除已不在作用域内的设备快照。
     *
     * <p>分类/位置范围下设备会被调拨到别的分类，如果只增不减，旧计划会一直巡检已经不属于它的设备。
     * 只删快照行，不动已生成的历史任务。
     */
    @Delete("""
            <script>
            DELETE FROM insp_plan_equipment
             WHERE plan_id = #{planId}
               AND equipment_id NOT IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteNotIn(@Param("planId") Long planId, @Param("ids") List<Long> ids);
}
