package com.equipcare.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.inventory.entity.SpWorkOrderPart;
import com.equipcare.inventory.vo.PartApplyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface SpWorkOrderPartMapper extends BaseMapper<SpWorkOrderPart> {

    @Select("SELECT * FROM sp_work_order_part WHERE work_order_id = #{workOrderId} ORDER BY id ASC")
    List<SpWorkOrderPart> selectByOrder(@Param("workOrderId") Long workOrderId);

    /**
     * 原子状态迁移（Phase 11 批七）：仅当当前状态等于期望状态时更新。
     * 影响行数为 0 表示状态已被并发请求改变（如重复退料），调用方必须拒绝操作。
     */
    @Update("""
            UPDATE sp_work_order_part
               SET issue_status = #{toStatus}, audit_time = NOW()
             WHERE id = #{id} AND issue_status = #{fromStatus}
            """)
    int transitionStatus(@Param("id") Long id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus);

    /** 待审批数量：>0 时禁止提交验收 */
    @Select("SELECT COUNT(1) FROM sp_work_order_part WHERE work_order_id = #{workOrderId} AND issue_status = 'APPLIED'")
    int countApplied(@Param("workOrderId") Long workOrderId);

    /** 已出库备件成本合计，验收通过时归集到工单 spare_cost */
    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM sp_work_order_part
             WHERE work_order_id = #{workOrderId} AND issue_status = 'ISSUED'
            """)
    BigDecimal sumIssuedAmount(@Param("workOrderId") Long workOrderId);

    /** 领用申请分页（审批视角）：联工单取编号 / 标题 / 设备，联用户取申请人 / 审批人姓名。 */
    @Select("""
            <script>
            SELECT a.id, a.work_order_id, o.order_no, o.title AS fault_title, a.part_id,
                   a.part_name, a.quantity, a.unit_price, a.amount,
                   a.apply_user_id, u.real_name AS apply_user_name, a.apply_time,
                   a.audit_user_id, au.real_name AS audit_user_name, a.audit_time,
                   a.issue_status, a.issue_time, a.remark
              FROM sp_work_order_part a
              JOIN wo_work_order o ON o.id = a.work_order_id
              LEFT JOIN sys_user u  ON u.id  = a.apply_user_id
              LEFT JOIN sys_user au ON au.id = a.audit_user_id
             WHERE 1 = 1
             <if test="status != null and status != ''"> AND a.issue_status = #{status}</if>
             <if test="workOrderId != null">         AND a.work_order_id = #{workOrderId}</if>
             ORDER BY a.id DESC
            </script>
            """)
    IPage<PartApplyVO> selectApplyPage(Page<PartApplyVO> page,
                                       @Param("status") String status,
                                       @Param("workOrderId") Long workOrderId);
}
