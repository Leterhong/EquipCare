package com.equipcare.workorder;

import com.equipcare.workorder.entity.WoWorkOrder;

import java.util.Objects;
import java.util.Set;

/**
 * 工单详情可见性策略（渗透整改 P3：详情与列表数据范围对齐）。
 *
 * <p>规则：管理类角色（ALL 数据范围：ADMIN/EQUIP_ADMIN/VERIFIER/MANAGER）全量可见；
 * SELF 数据范围角色（INSPECTOR/REPAIRMAN）仅当为本单相关人员（报修人/派单人/
 * 维修人/验收人）时可见。验收场景：VERIFIER 为 ALL，天然覆盖跨人验收。
 *
 * <p>独立静态策略类便于穷举角色组合的单测覆盖。
 */
public final class WorkOrderAccessPolicy {

    /** 数据范围为 ALL 的角色（seed.sql：全部数据） */
    private static final Set<String> ALL_SCOPE_ROLES = Set.of(
            "ADMIN", "EQUIP_ADMIN", "VERIFIER", "MANAGER");

    private WorkOrderAccessPolicy() {
    }

    /**
     * 判定用户是否可见该工单详情。
     *
     * @param order      工单实体（取相关人员 id）
     * @param userId     当前用户 id
     * @param roleCodes  当前用户角色码集合
     */
    public static boolean isVisible(WoWorkOrder order, Long userId, Set<String> roleCodes) {
        if (order == null) {
            return false;
        }
        for (String role : roleCodes) {
            if (ALL_SCOPE_ROLES.contains(role)) {
                return true;
            }
        }
        // SELF 范围：本单相关人员（报修 / 派单 / 维修 / 验收）
        return Objects.equals(order.getReporterId(), userId)
                || Objects.equals(order.getAssigneeId(), userId)
                || Objects.equals(order.getDispatcherId(), userId)
                || Objects.equals(order.getVerifyUserId(), userId);
    }
}
