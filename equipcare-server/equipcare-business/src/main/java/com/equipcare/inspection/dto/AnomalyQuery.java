package com.equipcare.inspection.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 异常单查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "异常单查询条件")
public class AnomalyQuery extends PageQuery {

    private Long equipmentId;

    private Long taskId;

    @Schema(description = "状态：OPEN / WORK_ORDER / CLOSED")
    private String status;

    @Schema(description = "等级：HIGH / MEDIUM / LOW")
    private String level;
}
