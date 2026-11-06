package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 派单轨迹类型。
 */
public enum DispatchType implements EnumCode {

    ASSIGN("ASSIGN", "首次派单"),
    REDISPATCH("REDISPATCH", "重新派单"),
    TRANSFER("TRANSFER", "转派"),
    REASSIGN("REASSIGN", "强制转派"),
    REVOKE("REVOKE", "撤回");

    private final String code;
    private final String label;

    DispatchType(String code, String label) {
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
