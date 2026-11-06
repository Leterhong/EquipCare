package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 验收结果。
 */
public enum VerifyResult implements EnumCode {

    PASS("PASS", "通过"),
    REJECT("REJECT", "驳回");

    private final String code;
    private final String label;

    VerifyResult(String code, String label) {
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
