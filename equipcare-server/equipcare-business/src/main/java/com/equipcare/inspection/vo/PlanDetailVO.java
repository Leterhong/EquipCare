package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 巡检计划详情：表头 + 覆盖设备快照 + 最近生成的任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "巡检计划详情")
public class PlanDetailVO extends PlanVO {

    @Schema(description = "作用域展开后的设备清单")
    private List<PlanEquipmentVO> equipment = new ArrayList<>();

    @Schema(description = "最近生成的任务，便于确认计划是否正常产出")
    private List<TaskVO> recentTasks = new ArrayList<>();
}
