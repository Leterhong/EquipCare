package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 巡检模板详情：表头 + 有序检查项。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "巡检模板详情")
public class TemplateDetailVO extends TemplateVO {

    @Schema(description = "检查项，按 seqNo 升序")
    private List<TemplateItemVO> items = new ArrayList<>();
}
