package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检计划的作用域类型。
 */
public enum PlanScopeType implements EnumCode {

    EQUIPMENT("EQUIPMENT", "指定设备"),
    CATEGORY("CATEGORY", "按设备分类"),
    LOCATION("LOCATION", "按所在位置");

    private final String code;
    private final String label;

    PlanScopeType(String code, String label) {
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

    public static PlanScopeType of(String code) {
        for (PlanScopeType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
