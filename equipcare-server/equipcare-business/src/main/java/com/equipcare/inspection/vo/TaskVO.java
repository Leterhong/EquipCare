package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 巡检任务列表项。
 */
@Data
@Schema(description = "巡检任务列表项")
public class TaskVO {

    private Long id;

    private String taskCode;

    private Long planId;

    private Long equipmentId;

    private String equipmentName;

    private String assetNo;

    private Long templateId;

    private String templateName;

    @Schema(description = "来源计划名称；手工任务为空")
    private String planName;

    @Schema(description = "检查项总数")
    private Integer itemCount;

    @Schema(description = "已填写项数")
    private Integer finishedItemCount;

    private LocalDate planDate;

    private Long assigneeId;

    private String assigneeName;

    private String status;

    @Schema(description = "来源：AUTO / MANUAL")
    private String source;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private LocalDateTime finishTime;

    @Schema(description = "结果：NORMAL / ABNORMAL")
    private String result;

    private Integer abnormalCount;

    private LocalDateTime slaDueTime;

    private Integer overdueFlag;

    private Integer qrVerified;
}
