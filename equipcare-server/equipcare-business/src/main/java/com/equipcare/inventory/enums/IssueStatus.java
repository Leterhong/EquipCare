package com.equipcare.inventory.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 工单备件领用状态。
 *
 * <p>状态流转：APPLIED → ISSUED / REJECTED；ISSUED 后未使用可 RETURNED。
 * 只有 ISSUED 才扣减库存并写流水，APPLIED 不占用库存但会阻塞「提交验收」。
 */
public enum IssueStatus implements EnumCode {

    APPLIED("APPLIED", "待审批"),
    ISSUED("ISSUED", "已出库"),
    REJECTED("REJECTED", "已驳回"),
    RETURNED("RETURNED", "已退料");

    private final String code;
    private final String label;

    IssueStatus(String code, String label) {
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
