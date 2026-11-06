package com.equipcare.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.inventory.entity.SpPartCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpPartCategoryMapper extends BaseMapper<SpPartCategory> {

    /** 分类名是否已存在（含已删除，名称不复用） */
    @Select("SELECT COUNT(1) FROM sp_part_category WHERE category_name = #{name}")
    int countByNameIncludeDeleted(@Param("name") String name);

    /** 分类下仍启用的备件数（删除保护） */
    @Select("SELECT COUNT(1) FROM sp_part WHERE category_id = #{categoryId} AND deleted = 0")
    int countParts(@Param("categoryId") Long categoryId);

    /** 分类更名时同步备件档案上的分类快照，保证列表展示与过滤一致 */
    @Update("""
            UPDATE sp_part
               SET category = #{name}, update_time = NOW()
             WHERE category_id = #{categoryId} AND deleted = 0
            """)
    int syncPartCategoryName(@Param("categoryId") Long categoryId, @Param("name") String name);
}
