package com.equipcare.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.equipcare.inspection.dto.TemplateQuery;
import com.equipcare.inspection.entity.InspTemplate;
import com.equipcare.inspection.vo.TemplateOptionVO;
import com.equipcare.inspection.vo.TemplateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InspTemplateMapper extends BaseMapper<InspTemplate> {

    /** 模板分页：带出适用分类与被引用计划数 */
    @Select("""
            <script>
            SELECT t.id, t.template_code, t.template_name, t.category_id, c.category_name,
                   t.version, t.status, t.item_count, t.description, t.publish_time, t.update_time,
                   (SELECT COUNT(1) FROM insp_plan p WHERE p.template_id = t.id AND p.deleted = 0) AS plan_count
              FROM insp_template t
              LEFT JOIN eq_category c ON c.id = t.category_id
             WHERE t.deleted = 0
            <if test="q.keyword != null and q.keyword != ''">
              AND (t.template_name LIKE CONCAT('%', #{q.keyword}, '%')
                   OR t.template_code LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.categoryId != null"> AND t.category_id = #{q.categoryId} </if>
            <if test="q.status != null and q.status != ''"> AND t.status = #{q.status} </if>
             ORDER BY t.update_time DESC, t.id DESC
            </script>
            """)
    IPage<TemplateVO> selectTemplatePage(IPage<TemplateVO> page, @Param("q") TemplateQuery query);

    @Select("""
            SELECT t.id, t.template_code, t.template_name, t.category_id, c.category_name,
                   t.version, t.status, t.item_count, t.description, t.publish_time, t.update_time,
                   (SELECT COUNT(1) FROM insp_plan p WHERE p.template_id = t.id AND p.deleted = 0) AS plan_count
              FROM insp_template t
              LEFT JOIN eq_category c ON c.id = t.category_id
             WHERE t.id = #{id} AND t.deleted = 0
            """)
    TemplateVO selectTemplateDetail(@Param("id") Long id);

    /** 计划表单可选的模板：已发布 + 未删除 */
    @Select("""
            SELECT t.id, t.template_code, t.template_name, t.category_id, c.category_name,
                   t.item_count, t.status
              FROM insp_template t
              LEFT JOIN eq_category c ON c.id = t.category_id
             WHERE t.deleted = 0 AND t.status = 'PUBLISHED'
             ORDER BY t.category_id IS NULL, t.category_id, t.id
            """)
    List<TemplateOptionVO> selectOptions();

    @Select("SELECT COUNT(1) FROM insp_plan WHERE template_id = #{templateId} AND deleted = 0")
    int countPlansUsing(@Param("templateId") Long templateId);

    @Select("SELECT COUNT(1) FROM insp_template WHERE template_code = #{code} AND deleted = 0 AND id <> #{excludeId}")
    int countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Select("SELECT MAX(template_code) FROM insp_template WHERE template_code LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCodeByPrefix(@Param("prefix") String prefix);
}
