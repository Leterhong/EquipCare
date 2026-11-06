package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备台账详情，在列表项基础上补充全量字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备台账详情")
public class EquipmentDetailVO extends EquipmentVO {

    @Schema(description = "出厂序列号")
    private String serialNo;

    @Schema(description = "制造商")
    private String manufacturer;

    @Schema(description = "供应商")
    private String supplier;

    private LocalDate purchaseDate;

    private LocalDate installDate;

    @Schema(description = "保修截止日期")
    private LocalDate warrantyEndDate;

    @Schema(description = "设备原值（元）")
    private BigDecimal originalValue;

    @Schema(description = "设备责任人姓名")
    private String responsibleUserName;

    @Schema(description = "归属部门名称")
    private String deptName;

    @Schema(description = "最近巡检人姓名")
    private String lastInspectUserName;

    private String remark;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    private LocalDateTime updateTime;

    @Schema(description = "设备图片与附件")
    private List<EquipmentFileVO> files;

    @Schema(description = "最近巡检记录（默认 10 条）")
    private List<EquipmentInspectHistoryVO> recentInspections;

    @Schema(description = "维修历史（默认 10 条）")
    private List<EquipmentRepairHistoryVO> repairHistory;

    @Schema(description = "历史工单总数")
    private Integer repairCount;
}
