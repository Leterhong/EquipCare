package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 工单动作码（10 个）。
 *
 * <p>接口只接受动作，不接受目标状态，避免「传个 status 就改」。
 *
 * <p>「开始维修」「服务评价」不改变状态，因此不在本枚举里：前者落一条
 * {@link RepairRecordType#START} 维修记录并开始计时，后者落 {@code wo_evaluation}。
 * 状态机只登记真正改变状态的动作。
 */
public enum WorkOrderAction implements EnumCode {

    DISPATCH("DISPATCH", "派单"),
    ACCEPT("ACCEPT", "接单"),
    REJECT_DISPATCH("REJECT_DISPATCH", "拒接"),
    TRANSFER("TRANSFER", "转派"),
    REVOKE("REVOKE", "撤回派单"),
    SUBMIT_VERIFY("SUBMIT_VERIFY", "提交验收"),
    VERIFY_PASS("VERIFY_PASS", "验收通过"),
    VERIFY_REJECT("VERIFY_REJECT", "验收不通过"),
    CANCEL("CANCEL", "取消工单"),
    FORCE_TRANSFER("FORCE_TRANSFER", "强制转派");

    private final String code;
    private final String label;

    WorkOrderAction(String code, String label) {
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

    public static WorkOrderAction of(String code) {
        if (code == null) {
            return null;
        }
        for (WorkOrderAction a : values()) {
            if (a.code.equalsIgnoreCase(code)) {
                return a;
            }
        }
        return null;
    }
}
