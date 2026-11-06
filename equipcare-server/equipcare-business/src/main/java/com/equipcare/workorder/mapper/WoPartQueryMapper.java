package com.equipcare.workorder.mapper;

import com.equipcare.workorder.vo.WorkOrderPartVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单备件用料只读查询。
 *
 * <p>备件台账归库存模块所有，工单侧只按 {@code work_order_id} 联查展示，
 * 不复制备件名称/单价，避免入库价调整后历史工单显示不一致。
 */
@Mapper
public interface WoPartQueryMapper {

    @Select("""
            SELECT w.id, w.part_id, p.part_no, w.part_name, p.spec, p.unit,
                   w.quantity, w.unit_price, w.amount,
                   w.issue_status, w.apply_user_id, u.real_name AS apply_user_name,
                   w.audit_user_id, u2.real_name AS audit_user_name,
                   w.apply_time, w.issue_time, w.remark
              FROM sp_work_order_part w
              LEFT JOIN sp_part p ON p.id = w.part_id
              LEFT JOIN sys_user u ON u.id = w.apply_user_id
              LEFT JOIN sys_user u2 ON u2.id = w.audit_user_id
             WHERE w.work_order_id = #{workOrderId}
             ORDER BY w.id ASC
            """)
    List<WorkOrderPartVO> selectByOrder(@Param("workOrderId") Long workOrderId);
}
