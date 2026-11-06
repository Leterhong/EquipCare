package com.equipcare.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 验收请求。通过时质量评分必填，驳回时原因必填（不少于 10 字）。
 */
@Data
@Schema(description = "验收请求")
public class VerifyDTO {

    @Schema(description = "质量评分 1-5，验收通过时必填")
    @Min(value = 1, message = "质量评分最小为 1")
    @Max(value = 5, message = "质量评分最大为 5")
    private Integer qualityScore;

    @Schema(description = "验收意见；驳回时不少于 10 字")
    @Size(max = 500, message = "验收意见长度不能超过 500")
    private String remark;

    @Schema(description = "验收照片附件ID，逗号分隔")
    @Size(max = 300, message = "附件标识过长")
    private String attachmentIds;
}
