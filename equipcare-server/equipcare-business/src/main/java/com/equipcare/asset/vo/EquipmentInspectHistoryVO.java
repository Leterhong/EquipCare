package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备巡检历史（一行一次巡检任务）。
 */
@Data
@Schema(description = "设备巡检历史")
public class EquipmentInspectHistoryVO {

    private Long taskId;

    private String taskCode;

    private LocalDate planDate;

    @Schema(description = "任务状态：PENDING/IN_PROGRESS/SUBMITTED/COMPLETED/OVERDUE/CANCELLED")
    private String status;

    @Schema(description = "结果：NORMAL/ABNORMAL，未完成为 null")
    private String result;

    @Schema(description = "异常项数")
    private Integer abnormalCount;

    private Long assigneeId;

    @Schema(description = "执行人姓名")
    private String assigneeName;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private LocalDateTime finishTime;

    private LocalDateTime slaDueTime;

    @Schema(description = "是否逾期")
    private Integer overdueFlag;
}
