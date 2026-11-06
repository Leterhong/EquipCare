package com.equipcare.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.analytics.entity.EqHealthSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface EqHealthSnapshotMapper extends BaseMapper<EqHealthSnapshot> {

    @Select("SELECT * FROM eq_health_snapshot WHERE equipment_id = #{equipmentId} AND stat_date = #{statDate}")
    EqHealthSnapshot selectByEquipmentAndDate(@Param("equipmentId") Long equipmentId,
                                              @Param("statDate") LocalDate statDate);

    /** 近 N 天健康分走势 */
    @Select("""
            SELECT * FROM eq_health_snapshot
             WHERE equipment_id = #{equipmentId}
             ORDER BY stat_date DESC
             LIMIT #{limit}
            """)
    List<EqHealthSnapshot> selectRecent(@Param("equipmentId") Long equipmentId, @Param("limit") int limit);
}
