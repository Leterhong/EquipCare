package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单详情：列表字段 + 全量业务字段 + 轨迹 / 维修记录 / 验收记录 / 派单轨迹。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工单详情")
public class WorkOrderDetailVO extends WorkOrderVO {

    private Long anomalyId;

    private Long sourceId;

    private Long dispatcherId;

    private String dispatcherName;

    private LocalDateTime startRepairTime;

    @Schema(description = "是否已开始维修（开始维修后才有维修时长与维修前照片）")
    private Boolean repairStarted;

    private LocalDateTime submitVerifyTime;

    private String repairSummary;

    @Schema(description = "工时（小时）")
    private BigDecimal laborHours;

    private Long verifyUserId;

    private String verifyUserName;

    private LocalDateTime verifyTime;

    @Schema(description = "验收结果：PASS / REJECT")
    private String verifyResult;

    private String verifyRemark;

    @Schema(description = "质量评分 1-5")
    private Integer qualityScore;

    private LocalDateTime actualFinishTime;

    @Schema(description = "创建前设备状态")
    private String prevEquipmentStatus;

    private String cancelReason;

    private Integer version;

    private LocalDateTime updateTime;

    @Schema(description = "流转轨迹（按时间正序）")
    private List<WorkOrderTimelineVO> timeline = new ArrayList<>();

    @Schema(description = "维修过程记录")
    private List<RepairRecordVO> repairRecords = new ArrayList<>();

    @Schema(description = "验收记录")
    private List<VerifyRecordVO> verifyRecords = new ArrayList<>();

    @Schema(description = "派单轨迹")
    private List<DispatchRecordVO> dispatchRecords = new ArrayList<>();

    @Schema(description = "巡检来源（仅巡检异常转单的工单有值，实时联查，非副本）")
    private InspectionSourceVO sourceInspection;

    @Schema(description = "合并进本工单的其它巡检异常（同设备同故障在途合并，按时间升序）")
    private List<InspectionSourceVO> mergedInspections = new ArrayList<>();

    @Schema(hidden = true)
    private String faultImageIds;

    @Schema(description = "故障现场照片（报修时上传，按文件ID实时回查）")
    private List<SourceAttachmentVO> faultImages = new ArrayList<>();

    @Schema(description = "备件使用记录（验收人据此核对用料）")
    private List<WorkOrderPartVO> parts = new ArrayList<>();

    @Schema(description = "服务评价（仅已完成且已评价时有值）")
    private WorkOrderEvaluationVO evaluation;

    @Schema(description = "全量操作时间线（状态流转 + 维修记录 + 派单 + 验收 + 备件 + 评价，按时间正序）")
    private List<WorkOrderTimelineItemVO> history = new ArrayList<>();
}
