package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备维修历史（一行一张工单）。
 *
 * <p>由 asset 模块的只读查询直接联表取数，不经过工单服务，
 * 避免 asset → workorder 的服务级反向依赖。
 */
@Data
@Schema(description = "设备维修历史")
public class EquipmentRepairHistoryVO {

    private Long workOrderId;

    private String orderNo;

    private String title;

    @Schema(description = "故障类型编码")
    private String faultType;

    private String faultDesc;

    @Schema(description = "等级：P1-P4")
    private String level;

    @Schema(description = "工单状态：PENDING_ASSIGN/PENDING_ACCEPT/IN_PROGRESS/PENDING_REVIEW/REJECTED/COMPLETED/CANCELLED")
    private String status;

    private LocalDateTime reportTime;

    @Schema(description = "实际修复完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "维修人姓名")
    private String assigneeName;

    private String repairSummary;

    @Schema(description = "工时（小时）")
    private BigDecimal laborHours;

    @Schema(description = "验收结果：PASS/REJECT，未验收为 null")
    private String verifyResult;

    @Schema(description = "质量评分 1-5")
    private Integer qualityScore;

    @Schema(description = "备件成本（元）")
    private BigDecimal spareCost;
}
