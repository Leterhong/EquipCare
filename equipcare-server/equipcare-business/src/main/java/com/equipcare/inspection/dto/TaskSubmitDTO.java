package com.equipcare.inspection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交巡检任务。
 */
@Data
@Schema(description = "提交巡检任务请求")
public class TaskSubmitDTO {

    @Schema(description = "逐项检查结果", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少填写一项检查结果")
    @Valid
    private List<ItemResultDTO> items;

    @Schema(description = "任务备注")
    @Size(max = 300, message = "备注长度不能超过 300")
    private String remark;

    /**
     * 单项检查结果。
     */
    @Data
    @Schema(description = "单项检查结果")
    public static class ItemResultDTO {

        @Schema(description = "模板检查项ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "检查项ID不能为空")
        private Long templateItemId;

        @Schema(description = "检查结果值")
        @Size(max = 500, message = "检查结果过长")
        private String itemValue;

        @Schema(description = "单项结果：NORMAL 正常 / ABNORMAL 异常 / SKIPPED 跳过；不传时按数值区间自动判定")
        private String result;

        @Schema(description = "是否异常（兼容旧版调用；result 为空时生效）")
        private Boolean abnormal;

        @Schema(description = "异常描述 / 跳过原因；判为异常时必须填写")
        @Size(max = 300, message = "异常描述长度不能超过 300")
        private String remark;

        @Schema(description = "跳过原因；result=SKIPPED 时必填")
        @Size(max = 200, message = "跳过原因长度不能超过 200")
        private String skipReason;

        @Schema(description = "现场照片文件ID，逗号分隔；模板要求留证的异常项必填")
        @Size(max = 300, message = "附件标识过长")
        private String attachmentIds;
    }
}
