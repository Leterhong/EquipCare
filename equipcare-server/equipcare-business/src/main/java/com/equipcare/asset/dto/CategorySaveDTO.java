package com.equipcare.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备类别保存请求。
 */
@Data
@Schema(description = "设备类别保存请求")
public class CategorySaveDTO {

    @Schema(description = "类别ID；为空表示新增，非空表示编辑")
    private Long id;

    @Schema(description = "父级类别ID，根节点传 0")
    private Long parentId;

    @Schema(description = "类别编码，同时作为资产编号前缀", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类别编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$", message = "类别编码需为 2-10 位大写字母或数字，且以字母开头")
    private String categoryCode;

    @Schema(description = "类别名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类别名称不能为空")
    @Size(max = 60, message = "类别名称长度不能超过 60")
    private String categoryName;

    @Schema(description = "默认巡检模板ID")
    private Long defaultTemplateId;

    @Schema(description = "建议保养周期（天）")
    @Min(value = 1, message = "保养周期至少 1 天")
    @Max(value = 3650, message = "保养周期不能超过 3650 天")
    private Integer maintainCycleDays;

    @Schema(description = "排序值，越小越靠前")
    private Integer sort;

    @Schema(description = "状态：1 启用 0 停用")
    private Integer status;
}
