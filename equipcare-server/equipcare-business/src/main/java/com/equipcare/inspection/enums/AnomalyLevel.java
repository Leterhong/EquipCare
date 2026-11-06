package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 异常等级，与工单等级映射：CRITICAL→P1，HIGH→P2，MEDIUM→P3，LOW→P4。
 */
public enum AnomalyLevel implements EnumCode {

    CRITICAL("CRITICAL", "紧急", "P1"),
    HIGH("HIGH", "严重", "P2"),
    MEDIUM("MEDIUM", "一般", "P3"),
    LOW("LOW", "轻微", "P4");

    private final String code;
    private final String label;
    private final String workOrderLevel;

    AnomalyLevel(String code, String label, String workOrderLevel) {
        this.code = code;
        this.label = label;
        this.workOrderLevel = workOrderLevel;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }

    /** 映射到工单等级 */
    public String getWorkOrderLevel() {
        return workOrderLevel;
    }

    public static AnomalyLevel of(String code) {
        for (AnomalyLevel l : values()) {
            if (l.code.equals(code)) {
                return l;
            }
        }
        return MEDIUM;
    }
}
