package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 巡检计划列表项。
 */
@Data
@Schema(description = "巡检计划列表项")
public class PlanVO {

    private Long id;

    private String planCode;

    private String planName;

    private Long templateId;

    private String templateName;

    @Schema(description = "EQUIPMENT / CATEGORY / LOCATION")
    private String scopeType;

    @Schema(description = "作用域配置 JSON")
    private String scopeConfig;

    @Schema(description = "作用域中文描述，如「分类：水泵」")
    private String scopeLabel;

    @Schema(description = "作用域对象ID，编辑表单回填用")
    private List<Long> scopeIds = new ArrayList<>();

    @Schema(description = "FIXED / ROUND_ROBIN / DEPT_OWNER")
    private String executorStrategy;

    private String executorIds;

    private String executorNames;

    @Schema(description = "执行人ID列表，编辑表单回填用")
    private List<Long> executorIdList = new ArrayList<>();

    @Schema(description = "DAILY / WEEKLY / MONTHLY")
    private String cycleType;

    private String cycleConfig;

    @Schema(description = "周期中文描述，如「每周 一、四 09:00」")
    private String cycleLabel;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer slaMinutes;

    private Integer generateAheadHours;

    @Schema(description = "ENABLED / DISABLED")
    private String status;

    private LocalDateTime nextGenerateTime;

    private LocalDateTime lastGenerateTime;

    @Schema(description = "覆盖设备数")
    private Integer equipmentCount;

    @Schema(description = "已生成任务数")
    private Integer taskCount;

    private Long creatorId;

    private String creatorName;

    private LocalDateTime updateTime;
}
