package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 计划作用域内的设备快照。
 */
@Data
@Schema(description = "计划设备快照")
public class PlanEquipmentVO {

    private Long id;

    private Long equipmentId;

    private String assetNo;

    private String equipmentName;

    private String categoryName;

    private String locationPath;

    private Integer seqNo;

    private Long assigneeId;

    private String assigneeName;

    @Schema(description = "设备状态：IN_USE / IDLE / REPAIRING / SCRAPPED")
    private String equipmentStatus;
}
