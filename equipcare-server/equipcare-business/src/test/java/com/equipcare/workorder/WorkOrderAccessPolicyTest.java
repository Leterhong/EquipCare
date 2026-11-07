package com.equipcare.workorder;

import com.equipcare.workorder.entity.WoWorkOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单详情可见性策略穷举测试（渗透整改 P3）。
 */
class WorkOrderAccessPolicyTest {

    private WoWorkOrder order(Long reporter, Long dispatcher, Long assignee, Long verifier) {
        WoWorkOrder o = new WoWorkOrder();
        o.setReporterId(reporter);
        o.setDispatcherId(dispatcher);
        o.setAssigneeId(assignee);
        o.setVerifyUserId(verifier);
        return o;
    }

    private final WoWorkOrder order = order(100L, 200L, 300L, 400L);

    @Test
    @DisplayName("ALL 范围角色全量可见")
    void allScopeRoles() {
        for (String role : new String[]{"ADMIN", "EQUIP_ADMIN", "VERIFIER", "MANAGER"}) {
            assertTrue(WorkOrderAccessPolicy.isVisible(order, 999L, Set.of(role)), role);
        }
    }

    @Test
    @DisplayName("SELF 角色：本单相关人员可见")
    void involvedVisible() {
        for (Long uid : new Long[]{100L, 200L, 300L, 400L}) {
            assertTrue(WorkOrderAccessPolicy.isVisible(order, uid, Set.of("REPAIRMAN")), "uid=" + uid);
        }
    }

    @Test
    @DisplayName("SELF 角色：无关人员不可见")
    void strangerDenied() {
        assertFalse(WorkOrderAccessPolicy.isVisible(order, 999L, Set.of("REPAIRMAN")));
        assertFalse(WorkOrderAccessPolicy.isVisible(order, 999L, Set.of("INSPECTOR")));
        assertFalse(WorkOrderAccessPolicy.isVisible(order, 999L, Set.of("REPAIRMAN", "INSPECTOR")));
    }

    @Test
    @DisplayName("混合角色：含任一 ALL 角色即放行")
    void mixedRoles() {
        assertTrue(WorkOrderAccessPolicy.isVisible(order, 999L, Set.of("REPAIRMAN", "ADMIN")));
    }

    @Test
    @DisplayName("空角色或未知角色拒绝")
    void emptyRolesDenied() {
        // 无关人员即使角色集合为空/未知也拒绝（997 不是本单任何角色）
        assertFalse(WorkOrderAccessPolicy.isVisible(order, 997L, Set.of()));
        assertFalse(WorkOrderAccessPolicy.isVisible(order, 997L, Set.of("UNKNOWN_ROLE")));
        // 相关人员但角色不含 ALL：仍然可见（涉及关系优先）
        assertTrue(WorkOrderAccessPolicy.isVisible(order, 300L, Set.of("UNKNOWN_ROLE")));
    }
}
