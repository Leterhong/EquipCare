package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 维修记录类型。
 */
public enum RepairRecordType implements EnumCode {

    START("START", "开始维修"),
    DIAGNOSIS("DIAGNOSIS", "故障诊断"),
    PROCESS("PROCESS", "维修过程"),
    FINISH("FINISH", "完工说明"),
    OTHER("OTHER", "其他记录");

    private final String code;
    private final String label;

    RepairRecordType(String code, String label) {
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

    /** 按码取中文名；未知码原样返回，避免历史数据把详情页打挂 */
    public static String labelOf(String code) {
        if (code == null) {
            return null;
        }
        for (RepairRecordType t : values()) {
            if (t.code.equals(code)) {
                return t.label;
            }
        }
        return code;
    }
}
