package com.equipcare.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 备件档案。
 *
 * <p>库存字段使用乐观锁 + 数据库 CHECK 约束双重保护；
 * 扣减必须走 {@code UPDATE ... WHERE current_stock >= qty} 原子语句。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sp_part")
public class SpPart extends BaseEntity {

    private String partNo;

    private String partName;

    private String category;

    /** 分类实体ID（第六阶段起管理走该字段；category 字符串列保留作历史快照） */
    private Long categoryId;

    private String spec;

    private String unit;

    private String brand;

    private Integer safetyStock;

    private Integer currentStock;

    private BigDecimal unitPrice;

    private String warehouseLocation;

    private String supplier;

    private Integer status;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
