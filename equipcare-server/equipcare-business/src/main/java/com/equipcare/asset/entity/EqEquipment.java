package com.equipcare.asset.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_equipment")
public class EqEquipment extends BaseEntity {

    private String assetNo;

    private String equipmentName;

    private Long categoryId;

    private Long locationId;

    private Long deptId;

    private String brand;

    private String model;

    private String spec;

    private String serialNo;

    private String manufacturer;

    private String supplier;

    private LocalDate purchaseDate;

    private LocalDate installDate;

    private LocalDate warrantyEndDate;

    private BigDecimal originalValue;

    private Long responsibleUserId;

    /** IN_USE / IDLE / REPAIRING / SCRAPPED */
    private String status;

    private BigDecimal healthScore;

    private LocalDateTime lastInspectTime;

    private LocalDateTime nextInspectTime;

    private String qrToken;

    private String remark;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
