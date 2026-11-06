package com.equipcare.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.inspection.entity.InspTaskItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InspTaskItemMapper extends BaseMapper<InspTaskItem> {

    @Select("SELECT * FROM insp_task_item WHERE task_id = #{taskId} ORDER BY id ASC")
    List<InspTaskItem> selectByTask(@Param("taskId") Long taskId);

    @Select("SELECT COUNT(1) FROM insp_task_item WHERE task_id = #{taskId}")
    int countByTask(@Param("taskId") Long taskId);
}
