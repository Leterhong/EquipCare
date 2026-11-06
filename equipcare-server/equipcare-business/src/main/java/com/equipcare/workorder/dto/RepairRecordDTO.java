package com.equipcare.workorder.dto;

import com.equipcare.common.validation.EnumValue;
import com.equipcare.workorder.enums.RepairRecordType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增维修过程记录。
 */
@Data
@Schema(description = "维修记录请求")
public class RepairRecordDTO {

    @Schema(description = "START / DIAGNOSIS / PROCESS / FINISH / OTHER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "记录类型不能为空")
    @EnumValue(target = RepairRecordType.class, message = "记录类型取值非法")
    private String recordType;

    @Schema(description = "记录内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "记录内容不能为空")
    @Size(max = 2000, message = "记录内容长度不能超过 2000")
    private String content;

    @Schema(description = "维修前照片文件ID，逗号分隔")
    @Size(max = 500, message = "维修前照片标识过长")
    @Pattern(regexp = "^$|^\\d+(,\\d+)*$", message = "维修前照片标识格式非法")
    private String beforeImageIds;

    @Schema(description = "维修后照片文件ID，逗号分隔")
    @Size(max = 500, message = "维修后照片标识过长")
    @Pattern(regexp = "^$|^\\d+(,\\d+)*$", message = "维修后照片标识格式非法")
    private String afterImageIds;

    @Schema(description = "本次耗时（分钟）")
    @Min(value = 0, message = "耗时不能为负数")
    private Integer costMinutes;

    @Schema(description = "其它附件ID，逗号分隔")
    @Size(max = 300, message = "附件标识过长")
    private String attachmentIds;
}
