package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单备件使用记录。
 *
 * <p>只读视图：数据来自 {@code sp_work_order_part} 与备件主数据，
 * 工单侧不复制备件档案，避免入库价调整后历史工单显示不一致。
 */
@Data
@Schema(description = "工单备件使用记录")
public class WorkOrderPartVO {

    private Long id;

    private Long partId;

    private String partNo;

    private String partName;

    @Schema(description = "规格型号")
    private String spec;

    private String unit;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    @Schema(description = "状态：APPLIED / ISSUED / REJECTED / RETURNED")
    private String issueStatus;

    private String issueStatusLabel;

    private Long applyUserId;

    private String applyUserName;

    private Long auditUserId;

    private String auditUserName;

    private LocalDateTime applyTime;

    private LocalDateTime issueTime;

    private String remark;
}
