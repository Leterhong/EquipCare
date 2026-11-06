package com.equipcare.asset.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备类别。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_category")
public class EqCategory extends BaseEntity {

    private Long parentId;

    private String categoryCode;

    private String categoryName;

    private Long defaultTemplateId;

    private Integer maintainCycleDays;

    private Integer sort;

    private Integer status;

    @TableLogic
    private Integer deleted;
}
