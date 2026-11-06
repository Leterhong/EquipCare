package com.equipcare.workorder.dto;

import com.equipcare.workorder.enums.WorkOrderLevel;
import com.equipcare.common.validation.EnumValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工报修建单。
 *
 * <p>异常转单不经过本 DTO，由巡检模块的异常处理流程直接调用 Service。
 */
@Data
@Schema(description = "人工报修建单请求")
public class WorkOrderCreateDTO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备不能为空")
    private Long equipmentId;

    @Schema(description = "工单标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "工单标题不能为空")
    @Size(max = 120, message = "标题长度不能超过 120")
    private String title;

    @Schema(description = "故障类型（字典编码）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "故障类型不能为空")
    @Size(max = 30, message = "故障类型编码过长")
    private String faultType;

    @Schema(description = "故障描述", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 1000, message = "故障描述长度不能超过 1000")
    private String faultDesc;

    @Schema(description = "故障现场照片文件ID，逗号分隔（可先调 /api/system/files/upload 取得）")
    @Size(max = 500, message = "照片标识过长")
    @Pattern(regexp = "^$|^\\d+(,\\d+)*$", message = "故障照片标识格式非法")
    private String faultImageIds;

    @Schema(description = "等级：P1 / P2 / P3 / P4", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "等级不能为空")
    @EnumValue(target = WorkOrderLevel.class, message = "工单等级取值非法")
    private String level;
}
