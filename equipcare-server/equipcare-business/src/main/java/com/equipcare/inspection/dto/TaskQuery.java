package com.equipcare.inspection.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 巡检任务查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "巡检任务查询条件")
public class TaskQuery extends PageQuery {

    @Schema(description = "关键字：任务编号 / 设备名称 / 资产编号")
    private String keyword;

    @Schema(description = "执行人ID；传 0 表示只看我的任务")
    private Long assigneeId;

    private Long equipmentId;

    @Schema(description = "来源计划ID")
    private Long planId;

    @Schema(description = "状态：PENDING / IN_PROGRESS / SUBMITTED / EXPIRED")
    private String status;

    @Schema(description = "计划日期")
    private LocalDate planDate;

    @Schema(description = "计划日期起（含）")
    private LocalDate dateFrom;

    @Schema(description = "计划日期止（含）")
    private LocalDate dateTo;

    @Schema(description = "结果：NORMAL / ABNORMAL")
    private String result;

    @Schema(description = "仅看已逾期任务")
    private Boolean overdueOnly;

    /** 服务端注入，不对外暴露：用于「只看我的任务」 */
    @Schema(hidden = true)
    private Long currentUserId;
}
