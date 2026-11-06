package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 巡检日历中的一天。
 */
@Data
@Schema(description = "巡检日历单日汇总")
public class CalendarDayVO {

    private LocalDate date;

    private Integer total;

    private Integer pending;

    private Integer inProgress;

    private Integer submitted;

    private Integer expired;

    @Schema(description = "其中结果为异常的已完成任务数")
    private Integer abnormal;
}
