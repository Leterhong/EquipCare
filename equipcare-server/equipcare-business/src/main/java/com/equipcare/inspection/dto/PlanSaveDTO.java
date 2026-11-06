package com.equipcare.inspection.dto;

import com.equipcare.common.validation.EnumValue;
import com.equipcare.inspection.enums.ExecutorStrategy;
import com.equipcare.inspection.enums.InspectionCycleType;
import com.equipcare.inspection.enums.PlanScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 新增 / 编辑巡检计划。
 *
 * <p>周期用结构化字段表达（执行时间 + 星期几 / 每月几号），由服务端拼成
 * {@code cycle_config} JSON 存库，前端不需要自己拼 JSON。
 */
@Data
@Schema(description = "巡检计划保存请求")
public class PlanSaveDTO {

    @Schema(description = "计划编码；留空自动生成", example = "PLAN-PUMP-WEEKLY")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,40}$", message = "计划编码只能是字母、数字、下划线或短横线，长度 2-40")
    private String planCode;

    @Schema(description = "计划名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "水泵每周巡检")
    @NotBlank(message = "计划名称不能为空")
    @Size(max = 100, message = "计划名称不能超过 100 字")
    private String planName;

    @Schema(description = "巡检模板ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "巡检模板不能为空")
    private Long templateId;

    @Schema(description = "作用域：EQUIPMENT / CATEGORY / LOCATION", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "作用域类型不能为空")
    @EnumValue(target = PlanScopeType.class, message = "作用域类型取值非法")
    private String scopeType;

    @Schema(description = "作用域对象ID列表：设备ID / 分类ID / 位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少选择一台设备或一个分类/位置")
    private List<Long> scopeIds;

    @Schema(description = "执行人策略：FIXED / ROUND_ROBIN / DEPT_OWNER")
    @EnumValue(target = ExecutorStrategy.class, message = "执行人策略取值非法")
    private String executorStrategy = "FIXED";

    @Schema(description = "执行人ID列表；DEPT_OWNER 策略下作为兜底")
    private List<Long> executorIds;

    @Schema(description = "周期：DAILY / WEEKLY / MONTHLY", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "执行周期不能为空")
    @EnumValue(target = InspectionCycleType.class, message = "执行周期取值非法")
    private String cycleType;

    @Schema(description = "执行时间 HH:mm", example = "09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "执行时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "执行时间格式应为 HH:mm")
    private String executeTime;

    @Schema(description = "每周执行日：1-7，周一为 1（WEEKLY 必填）")
    private List<Integer> weekDays;

    @Schema(description = "每月执行日：1-31（MONTHLY 必填）")
    private List<Integer> monthDays;

    @Schema(description = "生效开始日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生效开始日期不能为空")
    private LocalDate startDate;

    @Schema(description = "生效结束日期；为空表示长期有效")
    private LocalDate endDate;

    @Schema(description = "单次巡检时限（分钟）", example = "480")
    @Min(value = 30, message = "时限不能小于 30 分钟")
    @Max(value = 43200, message = "时限不能大于 30 天")
    private Integer slaMinutes = 480;

    @Schema(description = "提前生成小时数", example = "24")
    @Min(value = 0, message = "提前生成小时数不能为负")
    @Max(value = 168, message = "提前生成小时数不能超过 168")
    private Integer generateAheadHours = 24;

    @Schema(description = "状态：ENABLED / DISABLED；默认 DISABLED，确认无误后再启用")
    private String status;
}
