package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 巡检任务详情：任务信息 + 检查项清单 + 已产生的异常。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "巡检任务详情")
public class TaskDetailVO extends TaskVO {

    private String remark;

    @Schema(description = "任务设备摘要，含二维码地址")
    private EquipmentBriefVO equipment;

    @Schema(description = "跳过的检查项数")
    private Integer skippedCount;

    @Schema(description = "检查项清单（含模板的标准范围，便于移动端展示）")
    private List<TaskItemVO> items = new ArrayList<>();

    @Schema(description = "本任务产生的异常单")
    private List<AnomalyVO> anomalies = new ArrayList<>();
}
