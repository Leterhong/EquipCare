package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 维修工作台 / 派单工作台聚合视图。
 *
 * <p>一次请求返回「我该处理什么」，避免前端拿六个状态分别请求再自己拼。
 * 每个列表只取前 {@code limit} 条（默认 10），看全量走工单列表页。
 */
@Data
@Schema(description = "工单工作台")
public class WorkOrderWorkbenchVO {

    @Schema(description = "各状态数量（全量统计，不受 limit 影响）")
    private List<StatusCountVO> counts = new ArrayList<>();

    @Schema(description = "待派单（派单人视角）")
    private List<WorkOrderVO> pendingAssign = new ArrayList<>();

    @Schema(description = "已驳回待重新派单")
    private List<WorkOrderVO> rejected = new ArrayList<>();

    @Schema(description = "待我接单")
    private List<WorkOrderVO> pendingAccept = new ArrayList<>();

    @Schema(description = "我维修中的工单")
    private List<WorkOrderVO> repairing = new ArrayList<>();

    @Schema(description = "待我验收")
    private List<WorkOrderVO> pendingReview = new ArrayList<>();

    @Schema(description = "我最近完成的工单")
    private List<WorkOrderVO> completed = new ArrayList<>();
}
