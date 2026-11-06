package com.equipcare.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工单服务评价请求。
 *
 * <p>三个维度分开打分，是为了让「修得快但态度差」和「态度好但反复返修」
 * 在统计上能区分开；只留一个总分，管理改进没有方向。
 */
@Data
@Schema(description = "工单评价请求")
public class EvaluateDTO {

    @Schema(description = "服务态度 1-5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请评价服务态度")
    @Min(value = 1, message = "服务态度最小为 1")
    @Max(value = 5, message = "服务态度最大为 5")
    private Integer attitudeScore;

    @Schema(description = "维修质量 1-5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请评价维修质量")
    @Min(value = 1, message = "维修质量最小为 1")
    @Max(value = 5, message = "维修质量最大为 5")
    private Integer qualityScore;

    @Schema(description = "响应速度 1-5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请评价响应速度")
    @Min(value = 1, message = "响应速度最小为 1")
    @Max(value = 5, message = "响应速度最大为 5")
    private Integer speedScore;

    @Schema(description = "文字评价")
    @Size(max = 500, message = "文字评价不能超过 500 字")
    private String content;
}
