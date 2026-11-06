package com.equipcare.common.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分组统计结果，用于「按某字段 GROUP BY 后取数量」的场景。
 *
 * <pre>
 * SELECT category_id AS id, COUNT(1) AS total FROM eq_equipment GROUP BY category_id
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分组统计结果")
public class GroupCount {

    @Schema(description = "分组键，通常为外键ID或枚举值")
    private Long id;

    @Schema(description = "该分组下的记录数")
    private Long total;
}
