package com.equipcare.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.inspection.entity.InspTemplateItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InspTemplateItemMapper extends BaseMapper<InspTemplateItem> {

    @Select("SELECT * FROM insp_template_item WHERE template_id = #{templateId} ORDER BY seq_no ASC")
    List<InspTemplateItem> selectByTemplate(@Param("templateId") Long templateId);
}
