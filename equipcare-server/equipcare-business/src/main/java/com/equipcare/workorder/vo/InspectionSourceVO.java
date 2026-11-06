package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单的巡检来源。
 *
 * <p>只读联查 insp_anomaly / insp_task / insp_task_item 得到，工单表里不存这些字段的副本。
 * 这样巡检记录后续被补正（如补充说明），工单侧看到的始终是最新值，也不会出现「两套数据」。
 */
@Data
@Schema(description = "工单巡检来源")
public class InspectionSourceVO {

    private Long anomalyId;

    private String anomalyNo;

    @Schema(description = "异常检查项名称")
    private String itemName;

    @Schema(description = "异常项实测值")
    private String itemValue;

    @Schema(description = "异常描述")
    private String anomalyDescription;

    @Schema(description = "异常等级：CRITICAL / HIGH / MEDIUM / LOW")
    private String anomalyLevel;

    private Long taskId;

    private String taskCode;

    private LocalDate planDate;

    private Long equipmentId;

    private String equipmentName;

    private String assetNo;

    private String reporterName;

    private LocalDateTime reportTime;

    /** 异常记录上的附件 id 串（逗号分隔），仅用于内部回查 sys_file，不返回前端。 */
    @Schema(hidden = true)
    private String attachmentIds;

    @Schema(description = "现场照片")
    private List<SourceAttachmentVO> attachments = new ArrayList<>();
}
