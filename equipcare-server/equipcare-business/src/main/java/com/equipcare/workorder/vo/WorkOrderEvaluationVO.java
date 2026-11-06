package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单服务评价。
 */
@Data
@Schema(description = "工单服务评价")
public class WorkOrderEvaluationVO {

    private Long id;

    private Long evaluatorId;

    private String evaluatorName;

    @Schema(description = "服务态度 1-5")
    private Integer attitudeScore;

    @Schema(description = "维修质量 1-5")
    private Integer qualityScore;

    @Schema(description = "响应速度 1-5")
    private Integer speedScore;

    @Schema(description = "三项平均分，保留一位小数")
    private Double averageScore;

    private String content;

    private LocalDateTime createTime;
}
