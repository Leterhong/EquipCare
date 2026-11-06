package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.workorder.entity.SlaAlertRecord;
import com.equipcare.workorder.vo.SlaAlertVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface SlaAlertRecordMapper extends BaseMapper<SlaAlertRecord> {

    /**
     * 预警登记（幂等去重的落点）。
     *
     * <p>返回值语义（MySQL upsert 规则）：
     * <ul>
     *   <li>{@code 1}：今天该工单该阶段首次预警，<b>调用方应发送通知</b>；</li>
     *   <li>{@code 2}：今天已预警过，仅累加计数，调用方跳过通知。</li>
     * </ul>
     * 唯一键 (work_order_id, stage, alert_date) 兜底，并发扫描也不会重复通知。
     */
    @Insert("""
            INSERT INTO sla_alert_record
                (work_order_id, stage, alert_date, order_level, overdue_minutes,
                 alert_count, last_alert_time, create_time)
            VALUES
                (#{r.workOrderId}, #{r.stage}, #{r.alertDate}, #{r.orderLevel},
                 #{r.overdueMinutes}, 1, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                alert_count     = alert_count + 1,
                last_alert_time = NOW(),
                overdue_minutes = #{r.overdueMinutes}
            """)
    int upsertToday(@Param("r") SlaAlertRecord record);

    /** 预警记录分页：联工单与设备补展示字段 */
    @Select("""
            <script>
            SELECT a.id, a.work_order_id, o.order_no, o.title, e.equipment_name,
                   a.stage, a.order_level, a.overdue_minutes,
                   a.alert_count, a.last_alert_time, a.create_time
              FROM sla_alert_record a
              JOIN wo_work_order o ON o.id = a.work_order_id
              LEFT JOIN eq_equipment e ON e.id = o.equipment_id
             WHERE 1 = 1
             <if test="stage != null and stage != ''"> AND a.stage = #{stage}</if>
             <if test="level != null and level != ''"> AND a.order_level = #{level}</if>
             ORDER BY a.last_alert_time DESC, a.id DESC
            </script>
            """)
    IPage<SlaAlertVO> selectAlertPage(Page<SlaAlertVO> page,
                                      @Param("stage") String stage,
                                      @Param("level") String level);

    /** 当天是否已有某工单某阶段的预警（供测试与幂等自检） */
    @Select("""
            SELECT COUNT(1) FROM sla_alert_record
             WHERE work_order_id = #{workOrderId} AND stage = #{stage} AND alert_date = #{date}
            """)
    int countToday(@Param("workOrderId") Long workOrderId, @Param("stage") String stage,
                   @Param("date") LocalDate date);
}
