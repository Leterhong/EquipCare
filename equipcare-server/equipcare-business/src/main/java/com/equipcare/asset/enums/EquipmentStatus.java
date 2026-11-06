package com.equipcare.asset.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 设备状态。
 *
 * <p>{@code REPAIRING} 仅由工单流转写入，页面不提供手工切换入口。
 */
public enum EquipmentStatus implements EnumCode {

    IN_USE("IN_USE", "在用"),
    IDLE("IDLE", "闲置"),
    REPAIRING("REPAIRING", "维修中"),
    SCRAPPED("SCRAPPED", "已报废");

    private final String code;
    private final String label;

    EquipmentStatus(String code, String label) {
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

    /** 是否允许人工切换（维修中由工单驱动，禁止手工设置） */
    public static boolean manualChangeable(String code) {
        return IN_USE.getCode().equals(code) || IDLE.getCode().equals(code) || SCRAPPED.getCode().equals(code);
    }
}
