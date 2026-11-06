package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.workorder.entity.WoStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WoStatusLogMapper extends BaseMapper<WoStatusLog> {

    /** 工单流转轨迹，按时间正序 */
    @Select("SELECT * FROM wo_status_log WHERE work_order_id = #{workOrderId} ORDER BY id ASC")
    List<WoStatusLog> selectTimeline(@Param("workOrderId") Long workOrderId);
}
