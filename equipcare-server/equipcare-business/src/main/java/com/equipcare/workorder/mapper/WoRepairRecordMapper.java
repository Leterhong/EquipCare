package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.workorder.entity.WoRepairRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WoRepairRecordMapper extends BaseMapper<WoRepairRecord> {

    @Select("SELECT * FROM wo_repair_record WHERE work_order_id = #{workOrderId} ORDER BY id ASC")
    List<WoRepairRecord> selectByOrder(@Param("workOrderId") Long workOrderId);

    /** 某类记录数量，用于提交验收前置条件「至少 1 条 FINISH 记录」 */
    @Select("SELECT COUNT(1) FROM wo_repair_record WHERE work_order_id = #{workOrderId} AND record_type = #{recordType}")
    int countByType(@Param("workOrderId") Long workOrderId, @Param("recordType") String recordType);
}
