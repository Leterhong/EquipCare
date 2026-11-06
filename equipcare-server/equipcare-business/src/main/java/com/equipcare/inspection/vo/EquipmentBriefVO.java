package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 巡检任务关联设备的信息摘要。
 */
@Data
@Schema(description = "任务设备摘要")
public class EquipmentBriefVO {

    private Long id;

    private String assetNo;

    private String equipmentName;

    private String categoryName;

    private String locationPath;

    @Schema(description = "IN_USE / IDLE / REPAIRING / SCRAPPED")
    private String status;

    private String statusLabel;

    private String responsibleUserName;

    @Schema(description = "设备二维码颜色 PNG 地址（需登录 + 任务权限）")
    private String qrImageUrl;
}
