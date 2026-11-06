package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.equipcare.workorder.dto.WorkOrderQuery;
import com.equipcare.workorder.entity.WoWorkOrder;
import com.equipcare.workorder.vo.StatusCountVO;
import com.equipcare.workorder.vo.WorkOrderAssigneeVO;
import com.equipcare.workorder.vo.WorkOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WoWorkOrderMapper extends BaseMapper<WoWorkOrder> {

    /**
     * 状态条件更新（CAS）——状态机落库的唯一 SQL。
     *
     * <p>只有当「当前状态 = from 且版本号未被改动」时才更新成功，
     * 影响行数为 0 表示发生并发冲突，由上层抛 {@code WO_STATE_CHANGED}。
     */
    @Update("""
            UPDATE wo_work_order
               SET status = #{to},
                   version = version + 1,
                   update_by = #{operatorId},
                   update_time = NOW()
             WHERE id = #{id} AND status = #{from} AND version = #{version}
            """)
    int casUpdateStatus(@Param("id") Long id,
                        @Param("from") String from,
                        @Param("to") String to,
                        @Param("version") Integer version,
                        @Param("operatorId") Long operatorId);

    /** 按异常ID查工单，用于「一个异常只能建一张单」 */
    @Select("SELECT * FROM wo_work_order WHERE anomaly_id = #{anomalyId} LIMIT 1")
    WoWorkOrder selectByAnomalyId(@Param("anomalyId") Long anomalyId);

    /** 同设备同故障类型是否已有未完结工单，用于合并建单 */
    @Select("""
            SELECT * FROM wo_work_order
             WHERE equipment_id = #{equipmentId}
               AND fault_type = #{faultType}
               AND status NOT IN ('COMPLETED', 'CANCELLED')
             ORDER BY id DESC LIMIT 1
            """)
    WoWorkOrder selectInflightSameFault(@Param("equipmentId") Long equipmentId,
                                        @Param("faultType") String faultType);

    /** 设备是否存在未完结工单（设备删除 / 状态变更前的守卫） */
    @Select("""
            SELECT COUNT(1) FROM wo_work_order
             WHERE equipment_id = #{equipmentId} AND status NOT IN ('COMPLETED', 'CANCELLED')
            """)
    int countInflightByEquipment(@Param("equipmentId") Long equipmentId);

    /** 工单编号流水：取同前缀最大编号 */
    @Select("SELECT MAX(order_no) FROM wo_work_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxOrderNoByPrefix(@Param("prefix") String prefix);

    /**
     * 工单备件成本原子调整（Phase 11 批八）：
     * 出库时传 +amount 累加、退料时传 -amount 冲减，GREATEST 兜底不为负。
     * 数据库侧单条 UPDATE 自带行锁，并发下不丢账；金额为 DECIMAL 精度，无浮点误差。
     */
    @Update("""
            UPDATE wo_work_order
               SET spare_cost = GREATEST(COALESCE(spare_cost, 0) + #{delta}, 0)
             WHERE id = #{orderId}
            """)
    int adjustSpareCost(@Param("orderId") Long orderId, @Param("delta") java.math.BigDecimal delta);

    /**
     * 工单分页。一次性带出设备名称、资产编号、维修人姓名，避免 N+1。
     */
    @Select("""
            <script>
            SELECT o.id, o.order_no, o.title, o.source, o.equipment_id,
                   e.equipment_name, e.asset_no,
                   o.fault_type, o.fault_desc, o.level, o.priority, o.status,
                   o.reporter_id, u1.real_name AS reporter_name,
                   o.assignee_id, u2.real_name AS assignee_name,
                   o.report_time, o.dispatch_time, o.accept_time,
                   o.response_due_time, o.resolve_due_time,
                   o.sla_status, o.overdue_flag, o.reject_count, o.reject_dispatch_count,
                   o.close_time, o.spare_cost,
                   (SELECT COUNT(1) FROM wo_evaluation ev WHERE ev.work_order_id = o.id) > 0 AS evaluated
              FROM wo_work_order o
              LEFT JOIN eq_equipment e ON e.id = o.equipment_id
              LEFT JOIN sys_user u1 ON u1.id = o.reporter_id
              LEFT JOIN sys_user u2 ON u2.id = o.assignee_id
             WHERE 1 = 1
            <if test="q.keyword != null and q.keyword != ''">
              AND (o.order_no LIKE CONCAT('%', #{q.keyword}, '%')
                   OR o.title LIKE CONCAT('%', #{q.keyword}, '%')
                   OR e.asset_no LIKE CONCAT('%', #{q.keyword}, '%')
                   OR e.equipment_name LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.status != null and q.status != ''"> AND o.status = #{q.status} </if>
            <if test="q.statuses != null and q.statuses.size() > 0">
              AND o.status IN
              <foreach collection="q.statuses" item="s" open="(" separator="," close=")">#{s}</foreach>
            </if>
            <if test="q.level != null and q.level != ''"> AND o.level = #{q.level} </if>
            <if test="q.priority != null and q.priority != ''"> AND o.priority = #{q.priority} </if>
            <if test="q.source != null and q.source != ''"> AND o.source = #{q.source} </if>
            <if test="q.slaStatus != null and q.slaStatus != ''"> AND o.sla_status = #{q.slaStatus} </if>
            <if test="q.equipmentId != null"> AND o.equipment_id = #{q.equipmentId} </if>
            <if test="q.faultType != null and q.faultType != ''"> AND o.fault_type = #{q.faultType} </if>
            <if test="q.assigneeId != null"> AND o.assignee_id = #{q.assigneeId} </if>
            <if test="q.reporterId != null"> AND o.reporter_id = #{q.reporterId} </if>
            <if test="q.onlyMine == true and q.currentUserId != null">
              AND (o.assignee_id = #{q.currentUserId} OR o.reporter_id = #{q.currentUserId})
            </if>
            <if test="q.onlyAssignee == true and q.currentUserId != null">
              AND o.assignee_id = #{q.currentUserId}
            </if>
            <if test="q.reportBegin != null"> AND o.report_time &gt;= #{q.reportBegin} </if>
            <if test="q.reportEnd != null"> AND o.report_time &lt;= #{q.reportEnd} </if>
            <if test="q.overdueOnly == true"> AND o.sla_status = 'OVERDUE' </if>
             ORDER BY
            <if test="q.priorityFirst == true">
              FIELD(o.priority, 'HIGH', 'NORMAL', 'LOW'), FIELD(o.level, 'P1', 'P2', 'P3', 'P4'),
            </if>
             o.id DESC
            </script>
            """)
    IPage<WorkOrderVO> selectWorkOrderPage(IPage<WorkOrderVO> page, @Param("q") WorkOrderQuery query);

    /** 各状态工单数量，用于看板与个人待办角标 */
    @Select("""
            <script>
            SELECT status, COUNT(1) AS total FROM wo_work_order
             WHERE 1 = 1
            <if test="assigneeId != null"> AND assignee_id = #{assigneeId} </if>
             GROUP BY status
            </script>
            """)
    List<StatusCountVO> countGroupByStatus(@Param("assigneeId") Long assigneeId);

    /**
     * 可选维修人：具备维修人 / 设备管理员角色且账号启用，附带在办工单数。
     *
     * <p>按在办量升序，派单人优先看到负载低的人；在办数含待接单与维修中，
     * 不含待验收（活儿已经干完了）。
     */
    @Select("""
            SELECT u.id, u.username, u.real_name, d.dept_name, u.skill_tags,
                   (SELECT COUNT(1) FROM wo_work_order o
                     WHERE o.assignee_id = u.id
                       AND o.status IN ('PENDING_ACCEPT', 'IN_PROGRESS')) AS pending_count
              FROM sys_user u
              LEFT JOIN sys_dept d ON d.id = u.dept_id
             WHERE u.status = 1
               AND EXISTS (SELECT 1 FROM sys_user_role ur
                             JOIN sys_role r ON r.id = ur.role_id
                            WHERE ur.user_id = u.id
                              AND r.role_code IN ('REPAIRMAN', 'EQUIP_ADMIN'))
             ORDER BY pending_count ASC, u.id ASC
            """)
    List<WorkOrderAssigneeVO> selectAssignees();
}
