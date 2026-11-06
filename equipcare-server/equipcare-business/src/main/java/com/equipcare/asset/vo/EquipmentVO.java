package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备台账列表项。列表接口只返回必要字段，详情请查 {@link EquipmentDetailVO}。
 */
@Data
@Schema(description = "设备台账列表项")
public class EquipmentVO {

    private Long id;

    @Schema(description = "资产编号")
    private String assetNo;

    @Schema(description = "设备名称")
    private String equipmentName;

    private Long categoryId;

    @Schema(description = "类别名称")
    private String categoryName;

    private Long locationId;

    @Schema(description = "位置全路径")
    private String locationPath;

    private Long deptId;

    private String brand;

    private String model;

    private String spec;

    @Schema(description = "状态：IN_USE / IDLE / REPAIRING / SCRAPPED")
    private String status;

    @Schema(description = "健康评分 0-100")
    private BigDecimal healthScore;

    @Schema(description = "设备责任人ID")
    private Long responsibleUserId;

    @Schema(description = "设备责任人姓名")
    private String responsibleUserName;

    @Schema(description = "最近巡检时间")
    private LocalDateTime lastInspectTime;

    @Schema(description = "下次应巡检时间")
    private LocalDateTime nextInspectTime;

    @Schema(description = "二维码令牌")
    private String qrToken;

    @Schema(description = "封面图片地址（首张设备图片），无图片为 null")
    private String coverUrl;

    private LocalDateTime createTime;
}
