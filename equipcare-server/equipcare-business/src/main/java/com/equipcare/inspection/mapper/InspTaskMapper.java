package com.equipcare.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.equipcare.inspection.dto.TaskQuery;
import com.equipcare.inspection.entity.InspTask;
import com.equipcare.inspection.vo.CalendarDayVO;
import com.equipcare.inspection.vo.InspectionStatsVO;
import com.equipcare.inspection.vo.TaskDetailVO;
import com.equipcare.inspection.vo.TaskVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface InspTaskMapper extends BaseMapper<InspTask> {

    /** 同一计划同一设备同一天只生成一条任务（与唯一索引对应） */
    @Select("""
            SELECT COUNT(1) FROM insp_task
             WHERE plan_id = #{planId} AND equipment_id = #{equipmentId} AND plan_date = #{planDate}
            """)
    int countPlanEquipmentDate(@Param("planId") Long planId,
                               @Param("equipmentId") Long equipmentId,
                               @Param("planDate") LocalDate planDate);

    /**
     * 幂等插入任务。
     *
     * <p>依靠唯一索引 {@code uk_insp_task_plan_equip_date} 兜底：即便定时器与「立即生成」
     * 同时命中同一计划同一设备同一天，也只有一条能落库，返回 0 表示被忽略。
     * 这里不用 BaseMapper.insert，是因为需要 INSERT IGNORE 语义而不是抛异常中断整批生成。
     */
    @Insert("""
            INSERT IGNORE INTO insp_task
                (task_code, plan_id, equipment_id, template_id, plan_date, assignee_id, status, source,
                 sla_due_time, abnormal_count, overdue_flag, qr_verified, remark,
                 create_by, create_time, update_by, update_time)
            VALUES
                (#{t.taskCode}, #{t.planId}, #{t.equipmentId}, #{t.templateId}, #{t.planDate}, #{t.assigneeId},
                 #{t.status}, #{t.source}, #{t.slaDueTime}, 0, 0, 0, #{t.remark},
                 #{t.createBy}, NOW(), #{t.createBy}, NOW())
            """)
    int insertIgnore(@Param("t") InspTask task);

    /** 任务分页：带出设备、模板、来源计划与填写进度 */
    @Select("""
            <script>
            SELECT t.id, t.task_code, t.plan_id, p.plan_name, t.equipment_id, e.equipment_name, e.asset_no,
                   t.template_id, tp.template_name, t.plan_date, t.assignee_id,
                   u.real_name AS assignee_name, t.status, t.source, t.start_time,
                   t.submit_time, t.finish_time, t.result, t.abnormal_count,
                   t.sla_due_time, t.overdue_flag, t.qr_verified,
                   (SELECT COUNT(1) FROM insp_template_item ti WHERE ti.template_id = t.template_id) AS item_count,
                   (SELECT COUNT(1) FROM insp_task_item ii WHERE ii.task_id = t.id) AS finished_item_count
              FROM insp_task t
              LEFT JOIN eq_equipment e ON e.id = t.equipment_id
              LEFT JOIN insp_template tp ON tp.id = t.template_id
              LEFT JOIN insp_plan p ON p.id = t.plan_id
              LEFT JOIN sys_user u ON u.id = t.assignee_id
             WHERE 1 = 1
            <if test="q.assigneeId != null and q.assigneeId == 0"> AND t.assignee_id = #{q.currentUserId} </if>
            <if test="q.assigneeId != null and q.assigneeId != 0"> AND t.assignee_id = #{q.assigneeId} </if>
            <if test="q.equipmentId != null"> AND t.equipment_id = #{q.equipmentId} </if>
            <if test="q.planId != null"> AND t.plan_id = #{q.planId} </if>
            <if test="q.status != null and q.status != ''"> AND t.status = #{q.status} </if>
            <if test="q.result != null and q.result != ''"> AND t.result = #{q.result} </if>
            <if test="q.planDate != null"> AND t.plan_date = #{q.planDate} </if>
            <if test="q.dateFrom != null"> AND t.plan_date &gt;= #{q.dateFrom} </if>
            <if test="q.dateTo != null"> AND t.plan_date &lt;= #{q.dateTo} </if>
            <if test="q.overdueOnly != null and q.overdueOnly"> AND t.overdue_flag = 1 </if>
            <if test="q.keyword != null and q.keyword != ''">
              AND (t.task_code LIKE CONCAT('%', #{q.keyword}, '%')
                   OR e.equipment_name LIKE CONCAT('%', #{q.keyword}, '%')
                   OR e.asset_no LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
             ORDER BY t.plan_date DESC, t.id DESC
            </script>
            """)
    IPage<TaskVO> selectTaskPage(IPage<TaskVO> page, @Param("q") TaskQuery query);

    /** 详情表头：与列表同源，避免两处字段口径不一致 */
    @Select("""
            SELECT t.id, t.task_code, t.plan_id, p.plan_name, t.equipment_id, e.equipment_name, e.asset_no,
                   t.template_id, tp.template_name, t.plan_date, t.assignee_id,
                   u.real_name AS assignee_name, t.status, t.source, t.start_time,
                   t.submit_time, t.finish_time, t.result, t.abnormal_count,
                   t.sla_due_time, t.overdue_flag, t.qr_verified,
                   (SELECT COUNT(1) FROM insp_template_item ti WHERE ti.template_id = t.template_id) AS item_count,
                   (SELECT COUNT(1) FROM insp_task_item ii WHERE ii.task_id = t.id) AS finished_item_count
              FROM insp_task t
              LEFT JOIN eq_equipment e ON e.id = t.equipment_id
              LEFT JOIN insp_template tp ON tp.id = t.template_id
              LEFT JOIN insp_plan p ON p.id = t.plan_id
              LEFT JOIN sys_user u ON u.id = t.assignee_id
             WHERE t.id = #{id}
            """)
    TaskDetailVO selectTaskHeader(@Param("id") Long id);

    /** 计划最近生成的任务 */
    @Select("""
            SELECT t.id, t.task_code, t.plan_id, t.equipment_id, e.equipment_name, e.asset_no,
                   t.template_id, t.plan_date, t.assignee_id, u.real_name AS assignee_name,
                   t.status, t.source, t.result, t.abnormal_count, t.sla_due_time,
                   t.overdue_flag, t.qr_verified, t.submit_time
              FROM insp_task t
              LEFT JOIN eq_equipment e ON e.id = t.equipment_id
              LEFT JOIN sys_user u ON u.id = t.assignee_id
             WHERE t.plan_id = #{planId}
             ORDER BY t.plan_date DESC, t.id DESC
             LIMIT #{limit}
            """)
    List<TaskVO> selectRecentByPlan(@Param("planId") Long planId, @Param("limit") int limit);

    /** 任务状态条件更新，避免并发重复提交 */
    @Update("""
            UPDATE insp_task
               SET status = #{to}, update_by = #{operatorId}, update_time = NOW()
             WHERE id = #{id} AND status = #{from}
            """)
    int casUpdateStatus(@Param("id") Long id, @Param("from") String from,
                        @Param("to") String to, @Param("operatorId") Long operatorId);

    /** 任务编号流水：取同前缀最大编号 */
    @Select("SELECT MAX(task_code) FROM insp_task WHERE task_code LIKE CONCAT(#{prefix}, '%')")
    String selectMaxTaskCodeByPrefix(@Param("prefix") String prefix);

    /** 待执行且已过期的任务，供定时任务告警 */
    @Select("""
            SELECT * FROM insp_task
             WHERE status IN ('PENDING', 'IN_PROGRESS') AND sla_due_time < NOW()
             LIMIT #{limit}
            """)
    List<InspTask> selectOverdue(@Param("limit") int limit);

    /** 过期处理：未开始的任务直接标记 EXPIRED */
    @Update("""
            UPDATE insp_task
               SET status = 'EXPIRED', overdue_flag = 1, update_time = NOW()
             WHERE status = 'PENDING' AND sla_due_time < #{now}
            """)
    int markPendingExpired(@Param("now") LocalDateTime now);

    /** 已开始但超过时限的任务只打逾期标记，避免把正在执行的任务判死 */
    @Update("""
            UPDATE insp_task
               SET overdue_flag = 1, update_time = NOW()
             WHERE status = 'IN_PROGRESS' AND overdue_flag = 0 AND sla_due_time < #{now}
            """)
    int markInProgressOverdue(@Param("now") LocalDateTime now);

    /** 日历：按计划日聚合（SUM 在无匹配行时为 NULL，统一用 COALESCE 兜底） */
    @Select("""
            <script>
            SELECT plan_date AS `date`, COUNT(1) AS total,
                   COALESCE(SUM(status = 'PENDING'), 0) AS pending,
                   COALESCE(SUM(status = 'IN_PROGRESS'), 0) AS in_progress,
                   COALESCE(SUM(status = 'SUBMITTED'), 0) AS submitted,
                   COALESCE(SUM(status = 'EXPIRED'), 0) AS expired,
                   COALESCE(SUM(result = 'ABNORMAL'), 0) AS abnormal
              FROM insp_task
             WHERE plan_date BETWEEN #{from} AND #{to}
            <if test="assigneeId != null"> AND assignee_id = #{assigneeId} </if>
             GROUP BY plan_date
             ORDER BY plan_date ASC
            </script>
            """)
    List<CalendarDayVO> selectCalendar(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                       @Param("assigneeId") Long assigneeId);

    /** 统计聚合：总数 / 已提交 / 已逾期 / 未完成 / 异常 */
    @Select("""
            <script>
            SELECT COUNT(1) AS total,
                   COALESCE(SUM(status = 'SUBMITTED'), 0) AS submitted,
                   COALESCE(SUM(status = 'EXPIRED'), 0) AS expired,
                   COALESCE(SUM(status IN ('PENDING', 'IN_PROGRESS')), 0) AS pending,
                   COALESCE(SUM(result = 'ABNORMAL'), 0) AS abnormal
              FROM insp_task
             WHERE plan_date BETWEEN #{from} AND #{to}
            <if test="assigneeId != null"> AND assignee_id = #{assigneeId} </if>
            </script>
            """)
    Map<String, Object> selectTaskAggregate(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                            @Param("assigneeId") Long assigneeId);

    @Select("""
            <script>
            SELECT status AS code, COUNT(1) AS count
              FROM insp_task
             WHERE plan_date BETWEEN #{from} AND #{to}
            <if test="assigneeId != null"> AND assignee_id = #{assigneeId} </if>
             GROUP BY status
            </script>
            """)
    List<InspectionStatsVO.StatusCount> selectStatusCounts(@Param("from") LocalDate from,
                                                           @Param("to") LocalDate to,
                                                           @Param("assigneeId") Long assigneeId);

    /** 计划维度统计 */
    @Select("""
            SELECT t.plan_id, p.plan_name, COUNT(1) AS total,
                   SUM(t.status = 'SUBMITTED') AS submitted,
                   SUM(t.result = 'ABNORMAL') AS abnormal
              FROM insp_task t
              INNER JOIN insp_plan p ON p.id = t.plan_id
             WHERE t.plan_date BETWEEN #{from} AND #{to}
             GROUP BY t.plan_id, p.plan_name
             ORDER BY total DESC
             LIMIT #{limit}
            """)
    List<InspectionStatsVO.PlanStat> selectPlanStats(@Param("from") LocalDate from,
                                                     @Param("to") LocalDate to,
                                                     @Param("limit") int limit);

    @Select("SELECT COUNT(1) FROM insp_task WHERE plan_id = #{planId}")
    int countByPlan(@Param("planId") Long planId);
}
