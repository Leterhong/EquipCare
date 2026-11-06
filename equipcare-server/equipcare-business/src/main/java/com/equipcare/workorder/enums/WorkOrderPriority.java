package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 维修优先级。与紧急等级（P1–P4）区分：
 *
 * <ul>
 *   <li>{@code level} 描述故障本身的严重程度，由报修人填写，决定 SLA 时限；</li>
 *   <li>{@code priority} 描述派单时的处理次序，由派单人在派单环节设定，
 *       用于「先修哪一张」，不改变 SLA 计算。</li>
 * </ul>
 * 两者分开的好处：同一台 P1 故障也可能因为现场条件被临时降为「稍后处理」，
 * 而不至于为了调整次序去改故障等级、把 SLA 一起改掉。
 */
public enum WorkOrderPriority implements EnumCode {

    HIGH("HIGH", "高"),
    NORMAL("NORMAL", "中"),
    LOW("LOW", "低");

    private final String code;
    private final String label;

    WorkOrderPriority(String code, String label) {
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

    public static WorkOrderPriority of(String code) {
        if (code == null) {
            return null;
        }
        for (WorkOrderPriority p : values()) {
            if (p.code.equalsIgnoreCase(code)) {
                return p;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        WorkOrderPriority p = of(code);
        return p == null ? code : p.getLabel();
    }
}
