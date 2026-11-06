package com.equipcare.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.equipcare.common.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 备件分类。
 *
 * <p>第六阶段起分类从「备件档案上的一个字符串」升级为可管理实体：
 * 分类重命名时不必逐条改备件，统计也能按分类聚合。
 * {@code sp_part.category} 字符串列保留作历史快照，管理一律走 {@code category_id}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "备件分类")
@TableName("sp_part_category")
public class SpPartCategory extends BaseEntity {

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "说明")
    private String remark;

    @Schema(description = "排序（小在前）")
    private Integer sort;

    @Schema(description = "逻辑删除：0正常 1已删除")
    @TableLogic
    private Integer deleted;
}
