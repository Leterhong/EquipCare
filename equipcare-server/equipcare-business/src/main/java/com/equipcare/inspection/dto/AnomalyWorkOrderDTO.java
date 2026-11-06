package com.equipcare.inspection.dto;

import com.equipcare.common.validation.EnumValue;
import com.equipcare.workorder.enums.WorkOrderLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 巡检异常转维修工单。
 *
 * <p>设备、巡检任务、异常项、现场照片、异常描述都不在本 DTO 里传递：
 * 它们由异常单本身携带，工单只落一个 anomaly_id 关联，
 * 避免「前端把数据抄一遍」造成后续两边对不上。
 */
@Data
@Schema(description = "异常转工单请求")
public class AnomalyWorkOrderDTO {

    @Schema(description = "故障类型（字典编码）；为空时按检查项类型推断", example = "ELECTRIC")
    @Size(max = 30, message = "故障类型编码过长")
    private String faultType;

    @Schema(description = "工单标题；为空时取「巡检异常：检查项名称」")
    @Size(max = 120, message = "工单标题不能超过 120 字")
    private String title;

    @Schema(description = "补充说明；会追加在异常描述之后，不覆盖原始异常内容")
    @Size(max = 500, message = "补充说明不能超过 500 字")
    private String remark;

    @Schema(description = "工单等级：P1/P2/P3/P4；为空时按异常等级映射")
    @EnumValue(target = WorkOrderLevel.class, message = "工单等级取值非法")
    private String level;
}
