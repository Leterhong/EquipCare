package com.equipcare.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.equipcare.inspection.dto.PlanQuery;
import com.equipcare.inspection.entity.InspPlan;
import com.equipcare.inspection.vo.PlanVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InspPlanMapper extends BaseMapper<InspPlan> {

    /** 计划分页：带出模板名、创建人、覆盖设备数与已生成任务数 */
    @Select("""
            <script>
            SELECT p.id, p.plan_code, p.plan_name, p.template_id, tp.template_name,
                   p.scope_type, p.scope_config, p.executor_strategy, p.executor_ids,
                   p.cycle_type, p.cycle_config, p.start_date, p.end_date,
                   p.sla_minutes, p.generate_ahead_hours, p.status,
                   p.next_generate_time, p.last_generate_time, p.creator_id,
                   u.real_name AS creator_name, p.update_time,
                   (SELECT COUNT(1) FROM insp_plan_equipment pe WHERE pe.plan_id = p.id) AS equipment_count,
                   (SELECT COUNT(1) FROM insp_task t WHERE t.plan_id = p.id) AS task_count
              FROM insp_plan p
              LEFT JOIN insp_template tp ON tp.id = p.template_id
              LEFT JOIN sys_user u ON u.id = p.creator_id
             WHERE p.deleted = 0
            <if test="q.keyword != null and q.keyword != ''">
              AND (p.plan_name LIKE CONCAT('%', #{q.keyword}, '%')
                   OR p.plan_code LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.status != null and q.status != ''"> AND p.status = #{q.status} </if>
            <if test="q.cycleType != null and q.cycleType != ''"> AND p.cycle_type = #{q.cycleType} </if>
            <if test="q.templateId != null"> AND p.template_id = #{q.templateId} </if>
             ORDER BY p.status DESC, p.id DESC
            </script>
            """)
    IPage<PlanVO> selectPlanPage(IPage<PlanVO> page, @Param("q") PlanQuery query);

    @Select("""
            SELECT p.id, p.plan_code, p.plan_name, p.template_id, tp.template_name,
                   p.scope_type, p.scope_config, p.executor_strategy, p.executor_ids,
                   p.cycle_type, p.cycle_config, p.start_date, p.end_date,
                   p.sla_minutes, p.generate_ahead_hours, p.status,
                   p.next_generate_time, p.last_generate_time, p.creator_id,
                   u.real_name AS creator_name, p.update_time,
                   (SELECT COUNT(1) FROM insp_plan_equipment pe WHERE pe.plan_id = p.id) AS equipment_count,
                   (SELECT COUNT(1) FROM insp_task t WHERE t.plan_id = p.id) AS task_count
              FROM insp_plan p
              LEFT JOIN insp_template tp ON tp.id = p.template_id
              LEFT JOIN sys_user u ON u.id = p.creator_id
             WHERE p.id = #{id} AND p.deleted = 0
            """)
    PlanVO selectPlanDetail(@Param("id") Long id);

    /** 定时器扫描：只取启用中的计划 */
    @Select("SELECT * FROM insp_plan WHERE status = 'ENABLED' AND deleted = 0 ORDER BY id ASC")
    List<InspPlan> selectEnabled();

    @Select("SELECT MAX(plan_code) FROM insp_plan WHERE plan_code LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCodeByPrefix(@Param("prefix") String prefix);
}
