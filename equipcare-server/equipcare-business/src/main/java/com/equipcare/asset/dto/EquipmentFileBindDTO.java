package com.equipcare.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绑定已上传的文件到设备。
 * <p>先调用 {@code POST /api/files/upload} 拿到 fileId，再调用本接口建立关联，
 * 避免设备保存与文件上传耦合在一次请求里。
 */
@Data
@Schema(description = "设备文件绑定请求")
public class EquipmentFileBindDTO {

    @Schema(description = "文件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    @Schema(description = "用途：IMAGE / ATTACHMENT，默认 ATTACHMENT")
    @Pattern(regexp = "^(IMAGE|ATTACHMENT)$", message = "文件用途只能是 IMAGE 或 ATTACHMENT")
    private String fileType;

    @Schema(description = "排序号，越小越靠前")
    @Min(value = 0, message = "排序号不能为负")
    @Max(value = 9999, message = "排序号不能超过 9999")
    private Integer sortNo;
}
