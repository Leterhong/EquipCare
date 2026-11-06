package com.equipcare.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.workorder.entity.SlaPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SlaPolicyMapper extends BaseMapper<SlaPolicy> {

    /** 按等级取启用政策（等级唯一） */
    @Select("SELECT * FROM sla_policy WHERE level = #{level} AND enabled = 1 AND deleted = 0 LIMIT 1")
    SlaPolicy selectEnabledByLevel(@Param("level") String level);
}
