package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 工单来源。
 */
public enum WoSource implements EnumCode {

    INSPECT("INSPECT", "巡检发现"),
    ANOMALY("ANOMALY", "异常转单"),
    MANUAL("MANUAL", "人工报修"),
    PLAN_MAINTAIN("PLAN_MAINTAIN", "计划保养");

    private final String code;
    private final String label;

    WoSource(String code, String label) {
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

    public static boolean contains(String code) {
        for (WoSource s : values()) {
            if (s.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
