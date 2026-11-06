package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.workorder.entity.WoDispatchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WoDispatchLogMapper extends BaseMapper<WoDispatchLog> {

    @Select("SELECT * FROM wo_dispatch_log WHERE work_order_id = #{workOrderId} ORDER BY id ASC")
    List<WoDispatchLog> selectByOrder(@Param("workOrderId") Long workOrderId);
}
