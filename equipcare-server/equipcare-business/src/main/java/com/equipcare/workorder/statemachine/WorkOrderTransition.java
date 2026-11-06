package com.equipcare.workorder.statemachine;

import com.equipcare.workorder.enums.WorkOrderAction;
import com.equipcare.workorder.enums.WorkOrderStatus;

import java.util.Collections;
import java.util.Set;

/**
 * 一条状态转移规则。
 *
 * @param from           源状态
 * @param action         动作
 * @param to             目标状态
 * @param allowedRoles   允许执行的角色编码；为空表示不限制角色（仍需通过 {@code assigneeOnly} 校验）
 * @param assigneeOnly   是否只允许当前工单维修人本人执行
 * @param reasonMinLength 原因最小长度，0 表示不强制填写原因
 * @param label          规则说明，用于错误提示与文档生成
 */
public record WorkOrderTransition(WorkOrderStatus from,
                                  WorkOrderAction action,
                                  WorkOrderStatus to,
                                  Set<String> allowedRoles,
                                  boolean assigneeOnly,
                                  int reasonMinLength,
                                  String label) {

    public WorkOrderTransition {
        allowedRoles = allowedRoles == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(allowedRoles);
    }

    /** 是否需要填写原因 */
    public boolean reasonRequired() {
        return reasonMinLength > 0;
    }
}
