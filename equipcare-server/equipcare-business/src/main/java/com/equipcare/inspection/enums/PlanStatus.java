package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检计划状态。仅 ENABLED 的计划会被定时器扫描并生成任务。
 */
public enum PlanStatus implements EnumCode {

    ENABLED("ENABLED", "已启用"),
    DISABLED("DISABLED", "已暂停");

    private final String code;
    private final String label;

    PlanStatus(String code, String label) {
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
}
