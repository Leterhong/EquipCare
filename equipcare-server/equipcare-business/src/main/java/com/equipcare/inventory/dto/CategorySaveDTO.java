package com.equipcare.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 备件分类保存。
 */
@Data
@Schema(description = "备件分类保存请求")
public class CategorySaveDTO {

    private Long id;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过 50")
    private String categoryName;

    @Size(max = 200, message = "说明长度不能超过 200")
    private String remark;

    @Schema(description = "排序（小在前）")
    private Integer sort;
}
