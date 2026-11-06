package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 异常单状态。OPEN 待处理 → WORK_ORDER 已转工单 → CLOSED 已关闭。
 */
public enum AnomalyStatus implements EnumCode {

    OPEN("OPEN", "待处理"),
    WORK_ORDER("WORK_ORDER", "已转工单"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String label;

    AnomalyStatus(String code, String label) {
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
