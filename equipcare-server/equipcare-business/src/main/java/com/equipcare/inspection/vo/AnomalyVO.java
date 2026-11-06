package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 异常单列表项，带上对应工单号与状态，体现「异常 → 工单」闭环。
 */
@Data
@Schema(description = "异常单列表项")
public class AnomalyVO {

    private Long id;

    private String anomalyNo;

    private Long taskId;

    @Schema(description = "来源任务编号")
    private String taskCode;

    private Long equipmentId;

    private String equipmentName;

    private String assetNo;

    private String itemName;

    @Schema(description = "异常项实测值")
    private String itemValue;

    @Schema(description = "等级：CRITICAL / HIGH / MEDIUM / LOW")
    private String level;

    private String description;

    @Schema(description = "状态：OPEN / WORK_ORDER / CLOSED")
    private String status;

    private Long workOrderId;

    private String workOrderNo;

    private String workOrderStatus;

    @Schema(description = "现场照片")
    private List<AttachmentVO> attachments = new ArrayList<>();

    @Schema(hidden = true)
    private String attachmentIds;

    private Long reporterId;

    private String reporterName;

    private LocalDateTime reportTime;

    private LocalDateTime closeTime;
}
