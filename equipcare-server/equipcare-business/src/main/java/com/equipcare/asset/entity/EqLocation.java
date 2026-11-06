package com.equipcare.asset.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 位置（园区 / 楼栋 / 楼层 / 房间）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_location")
public class EqLocation extends BaseEntity {

    private Long parentId;

    private String locationCode;

    private String locationName;

    private String locationType;

    private String fullPath;

    private Integer sort;

    private Integer status;

    @TableLogic
    private Integer deleted;
}
