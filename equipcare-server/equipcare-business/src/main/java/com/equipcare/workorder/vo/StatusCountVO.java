package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单状态计数。
 */
@Data
@Schema(description = "工单状态计数")
public class StatusCountVO {

    @Schema(description = "状态码")
    private String status;

    @Schema(description = "数量")
    private Long total;
}
