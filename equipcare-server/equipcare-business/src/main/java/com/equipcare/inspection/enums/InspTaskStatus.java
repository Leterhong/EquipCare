package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检任务状态。
 *
 * <p>PENDING 待执行 → IN_PROGRESS 执行中 → SUBMITTED 已提交；超时未提交由定时任务标记 EXPIRED（仅告警）。
 */
public enum InspTaskStatus implements EnumCode {

    PENDING("PENDING", "待执行"),
    IN_PROGRESS("IN_PROGRESS", "执行中"),
    SUBMITTED("SUBMITTED", "已提交"),
    EXPIRED("EXPIRED", "已逾期");

    private final String code;
    private final String label;

    InspTaskStatus(String code, String label) {
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

    public static InspTaskStatus of(String code) {
        for (InspTaskStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
