package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.workorder.entity.WoVerifyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WoVerifyRecordMapper extends BaseMapper<WoVerifyRecord> {

    @Select("SELECT * FROM wo_verify_record WHERE work_order_id = #{workOrderId} ORDER BY id ASC")
    List<WoVerifyRecord> selectByOrder(@Param("workOrderId") Long workOrderId);
}
