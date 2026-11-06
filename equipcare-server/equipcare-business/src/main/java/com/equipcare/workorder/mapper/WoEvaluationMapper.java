package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.workorder.entity.WoEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WoEvaluationMapper extends BaseMapper<WoEvaluation> {

    @Select("SELECT * FROM wo_evaluation WHERE work_order_id = #{workOrderId} LIMIT 1")
    WoEvaluation selectByOrder(@Param("workOrderId") Long workOrderId);
}
