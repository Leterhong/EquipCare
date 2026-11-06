package com.equipcare.inspection.service;

import com.equipcare.common.result.PageResult;
import com.equipcare.inspection.dto.TemplateQuery;
import com.equipcare.inspection.dto.TemplateSaveDTO;
import com.equipcare.inspection.vo.TemplateDetailVO;
import com.equipcare.inspection.vo.TemplateOptionVO;
import com.equipcare.inspection.vo.TemplateVO;

import java.util.List;

/**
 * 巡检模板管理。
 */
public interface InspectionTemplateService {

    PageResult<TemplateVO> page(TemplateQuery query);

    TemplateDetailVO detail(Long id);

    /** 计划表单可选的模板（仅已发布） */
    List<TemplateOptionVO> options();

    Long create(TemplateSaveDTO dto);

    void update(Long id, TemplateSaveDTO dto);

    /** 逻辑删除；被计划引用时拒绝 */
    void delete(Long id);

    /** 发布：检查项必须完整可判定 */
    void publish(Long id);

    void disable(Long id);
}
