package com.equipcare.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 位置保存请求。
 */
@Data
@Schema(description = "位置保存请求")
public class LocationSaveDTO {

    @Schema(description = "位置ID；为空表示新增，非空表示编辑")
    private Long id;

    @Schema(description = "父级位置ID，根节点传 0")
    private Long parentId;

    @Schema(description = "位置编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "位置编码不能为空")
    @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{1,29}$", message = "位置编码需为 2-30 位大写字母、数字、下划线或中划线")
    private String locationCode;

    @Schema(description = "位置名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "位置名称不能为空")
    @Size(max = 60, message = "位置名称长度不能超过 60")
    private String locationName;

    @Schema(description = "类型：PARK / BUILDING / FLOOR / ROOM", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "位置类型不能为空")
    @Pattern(regexp = "^(PARK|BUILDING|FLOOR|ROOM)$", message = "位置类型只能是 PARK / BUILDING / FLOOR / ROOM")
    private String locationType;

    @Schema(description = "排序值，越小越靠前")
    private Integer sort;

    @Schema(description = "状态：1 启用 0 停用")
    private Integer status;
}
