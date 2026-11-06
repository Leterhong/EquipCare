package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检项类型。数值型按 min/max 判定异常，枚举型按枚举选项判定，布尔型直接记录是否正常。
 */
public enum InspItemType implements EnumCode {

    NUMBER("NUMBER", "数值"),
    ENUM("ENUM", "枚举"),
    BOOLEAN("BOOLEAN", "是否"),
    TEXT("TEXT", "文本");

    private final String code;
    private final String label;

    InspItemType(String code, String label) {
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

    /**
     * 解析检查项类型。
     *
     * <p>兼容历史数据中的 {@code BOOL} 写法（第一版字典曾用 BOOL，后统一为 BOOLEAN），
     * 避免老模板在新页面里显示为未知类型。
     */
    public static InspItemType of(String code) {
        if (code == null) {
            return null;
        }
        if ("BOOL".equalsIgnoreCase(code)) {
            return BOOLEAN;
        }
        for (InspItemType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
