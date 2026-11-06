package com.equipcare.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单备件领用申请（审批视角）。
 */
@Data
@Schema(description = "工单备件领用申请")
public class PartApplyVO {

    private Long id;

    private Long workOrderId;

    @Schema(description = "工单编号")
    private String orderNo;

    @Schema(description = "故障标题")
    private String faultTitle;

    private Long partId;

    @Schema(description = "备件名称（快照）")
    private String partName;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private Long applyUserId;

    @Schema(description = "申请人姓名")
    private String applyUserName;

    private LocalDateTime applyTime;

    private Long auditUserId;

    @Schema(description = "审批人姓名")
    private String auditUserName;

    private LocalDateTime auditTime;

    @Schema(description = "状态：APPLIED/ISSUED/REJECTED/RETURNED")
    private String issueStatus;

    private LocalDateTime issueTime;

    private String remark;
}
