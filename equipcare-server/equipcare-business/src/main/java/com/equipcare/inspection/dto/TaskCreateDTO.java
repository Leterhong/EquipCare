package com.equipcare.inspection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手工发起巡检任务（扫码巡检、临时补检场景）。
 *
 * <p>计划任务由定时器自动生成，本接口只覆盖人工发起的情况，来源记为 MANUAL。
 */
@Data
@Schema(description = "手工发起巡检请求")
public class TaskCreateDTO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备不能为空")
    private Long equipmentId;

    @Schema(description = "巡检模板ID；为空时取设备类别的默认模板")
    private Long templateId;

    @Schema(description = "执行人ID；为空时默认当前登录用户")
    private Long assigneeId;

    @Schema(description = "备注")
    @Size(max = 300, message = "备注长度不能超过 300")
    private String remark;
}
