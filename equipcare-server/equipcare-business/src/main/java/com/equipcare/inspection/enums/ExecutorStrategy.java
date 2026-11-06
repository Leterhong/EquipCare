package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检执行人分配策略。
 *
 * <p>FIXED 固定人员：按设备快照顺序在指定人员间轮转，人少设备多时同一人承担多台；
 * ROUND_ROBIN 全局轮值：按执行日期在人员间轮换，避免总是同一个人；
 * DEPT_OWNER 设备负责人：优先取设备档案里的负责人，取不到再回退到固定人员。
 */
public enum ExecutorStrategy implements EnumCode {

    FIXED("FIXED", "指定人员"),
    ROUND_ROBIN("ROUND_ROBIN", "按日轮值"),
    DEPT_OWNER("DEPT_OWNER", "设备负责人");

    private final String code;
    private final String label;

    ExecutorStrategy(String code, String label) {
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

    public static ExecutorStrategy of(String code) {
        for (ExecutorStrategy s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return FIXED;
    }
}
