package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检计划执行周期。
 *
 * <p>具体到「哪几天执行」由 {@code insp_plan.cycle_config} 的 JSON 决定：
 * <pre>
 * DAILY   {"time":"08:30"}
 * WEEKLY  {"time":"09:00","weekDays":[1,4]}        // ISO 星期，周一=1
 * MONTHLY {"time":"09:00","monthDays":[1,15]}      // 每月 1 日、15 日
 * </pre>
 * MONTHLY 遇到 31 日在 30 天的月份会顺延到当月最后一天，避免「2 月永远不巡检」。
 */
public enum InspectionCycleType implements EnumCode {

    DAILY("DAILY", "每日"),
    WEEKLY("WEEKLY", "每周"),
    MONTHLY("MONTHLY", "每月");

    private final String code;
    private final String label;

    InspectionCycleType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public static InspectionCycleType of(String code) {
        for (InspectionCycleType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
