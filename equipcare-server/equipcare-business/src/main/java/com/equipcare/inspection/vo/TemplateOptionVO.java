package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 下拉选择用的模板精简信息。
 */
@Data
@Schema(description = "巡检模板选项")
public class TemplateOptionVO {

    private Long id;

    private String templateCode;

    private String templateName;

    private Long categoryId;

    private String categoryName;

    private Integer itemCount;

    private String status;
}
