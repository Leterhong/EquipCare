package com.equipcare.system.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户查询条件")
public class UserQuery extends PageQuery {

    @Schema(description = "关键字：用户名 / 姓名 / 手机号")
    private String keyword;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
}
