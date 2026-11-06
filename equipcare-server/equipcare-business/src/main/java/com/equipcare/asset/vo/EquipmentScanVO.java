package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫码结果。
 *
 * <p>扫码链路（身份 → 权限 → 设备档案 → 状态 → 最近巡检 → 历史维修）一次返回，
 * 移动端只需一次请求即可渲染完整设备卡片。
 *
 * <p>安全说明：二维码本身只携带随机令牌，令牌只能换到本 VO；
 * 返回内容不含密码、JWT、手机号等敏感字段，且必须携带有效登录令牌才能调用。
 */
@Data
@Schema(description = "设备扫码结果")
public class EquipmentScanVO {

    private Long id;

    private String assetNo;

    private String equipmentName;

    @Schema(description = "类别名称")
    private String categoryName;

    @Schema(description = "位置全路径")
    private String locationPath;

    @Schema(description = "状态：IN_USE / IDLE / REPAIRING / SCRAPPED")
    private String status;

    @Schema(description = "状态中文名")
    private String statusLabel;

    @Schema(description = "健康评分 0-100")
    private BigDecimal healthScore;

    private String brand;

    private String model;

    @Schema(description = "设备责任人ID")
    private Long responsibleUserId;

    @Schema(description = "设备责任人姓名")
    private String responsibleUserName;

    @Schema(description = "封面图片地址")
    private String coverUrl;

    private LocalDateTime lastInspectTime;

    @Schema(description = "下次应巡检时间")
    private LocalDateTime nextInspectTime;

    @Schema(description = "巡检是否已逾期：nextInspectTime 早于当前时间")
    private Boolean inspectOverdue;

    @Schema(description = "是否允许对该设备发起巡检：仅启用中的设备可巡检")
    private Boolean inspectable;

    @Schema(description = "最近巡检记录（5 条）")
    private List<EquipmentInspectHistoryVO> recentInspections;

    @Schema(description = "历史维修记录（5 条）")
    private List<EquipmentRepairHistoryVO> repairHistory;

    @Schema(description = "本次扫码人（脱敏后的显示名）")
    private String scannedBy;
}
