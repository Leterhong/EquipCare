package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检结果。
 */
public enum InspectionResult implements EnumCode {

    NORMAL("NORMAL", "正常"),
    ABNORMAL("ABNORMAL", "发现异常");

    private final String code;
    private final String label;

    InspectionResult(String code, String label) {
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
