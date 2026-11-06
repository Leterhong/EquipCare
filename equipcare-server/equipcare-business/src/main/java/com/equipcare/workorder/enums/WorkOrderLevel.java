package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 工单紧急等级。等级决定 SLA 时限与通知范围。
 *
 * <p>P1 需 2 小时内响应、24 小时内修复；P4 为低优先，7 天内修复。
 */
public enum WorkOrderLevel implements EnumCode {

    P1("P1", "紧急", 60, 240),
    P2("P2", "高", 240, 1440),
    P3("P3", "中", 480, 4320),
    P4("P4", "低", 1440, 10080);

    private final String code;
    private final String label;
    /** 响应时限（分钟） */
    private final int responseMinutes;
    /** 修复时限（分钟） */
    private final int resolveMinutes;

    WorkOrderLevel(String code, String label, int responseMinutes, int resolveMinutes) {
        this.code = code;
        this.label = label;
        this.responseMinutes = responseMinutes;
        this.resolveMinutes = resolveMinutes;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolveMinutes() {
        return resolveMinutes;
    }

    public static WorkOrderLevel of(String code) {
        for (WorkOrderLevel l : values()) {
            if (l.code.equals(code)) {
                return l;
            }
        }
        return null;
    }
}
