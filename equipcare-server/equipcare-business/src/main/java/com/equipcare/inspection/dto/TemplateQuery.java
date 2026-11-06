package com.equipcare.inspection.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡检模板查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "巡检模板查询条件")
public class TemplateQuery extends PageQuery {

    @Schema(description = "关键字：模板名称 / 编码")
    private String keyword;

    private Long categoryId;

    @Schema(description = "状态：DRAFT / PUBLISHED / DISABLED")
    private String status;
}
