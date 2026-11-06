package com.equipcare.inspection.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡检计划查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "巡检计划查询条件")
public class PlanQuery extends PageQuery {

    @Schema(description = "关键字：计划名称 / 编码")
    private String keyword;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;

    @Schema(description = "周期：DAILY / WEEKLY / MONTHLY")
    private String cycleType;

    private Long templateId;
}
