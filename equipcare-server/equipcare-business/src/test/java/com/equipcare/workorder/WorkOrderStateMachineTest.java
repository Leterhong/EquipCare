package com.equipcare.workorder;

import com.equipcare.common.exception.BizException;
import com.equipcare.workorder.enums.WorkOrderAction;
import com.equipcare.workorder.enums.WorkOrderStatus;
import com.equipcare.workorder.statemachine.WorkOrderStateMachine;
import com.equipcare.workorder.statemachine.WorkOrderTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工单状态机单元测试。
 *
 * <p>核心目标：把 7 个状态 × 10 个动作共 70 种组合全部穷举，
 * 逐一断言「只有转移表登记的 14 条组合合法，其余 56 条必须抛 3001」。
 */
class WorkOrderStateMachineTest {

    private final WorkOrderStateMachine stateMachine = new WorkOrderStateMachine();

    /**
     * 合法转移表——与第五阶段需求「二、工单状态机」逐行对应，
     * 这里独立手写一遍，作为状态机实现的「第二份事实来源」，
     * 任何一侧被误改，测试都会失败。
     */
    private static final Set<String> LEGAL = Set.of(
            "PENDING_ASSIGN|DISPATCH->PENDING_ACCEPT",
            "REJECTED|DISPATCH->PENDING_ACCEPT",
            "PENDING_ACCEPT|ACCEPT->IN_PROGRESS",
            "PENDING_ACCEPT|REJECT_DISPATCH->REJECTED",
            "PENDING_ACCEPT|TRANSFER->PENDING_ACCEPT",
            "PENDING_ACCEPT|REVOKE->PENDING_ASSIGN",
            "IN_PROGRESS|SUBMIT_VERIFY->PENDING_REVIEW",
            "PENDING_REVIEW|VERIFY_PASS->COMPLETED",
            "PENDING_REVIEW|VERIFY_REJECT->IN_PROGRESS",
            "PENDING_ASSIGN|CANCEL->CANCELLED",
            "REJECTED|CANCEL->CANCELLED",
            "PENDING_ACCEPT|CANCEL->CANCELLED",
            "IN_PROGRESS|CANCEL->CANCELLED",
            "IN_PROGRESS|FORCE_TRANSFER->IN_PROGRESS"
    );

    static Stream<Arguments> allCombinations() {
        return Stream.of(WorkOrderStatus.values())
                .flatMap(status -> Stream.of(WorkOrderAction.values())
                        .map(action -> Arguments.of(status, action)));
    }

    @Test
    @DisplayName("转移表共 14 条规则，终态无出向动作")
    void tableSize() {
        assertThat(stateMachine.allTransitions()).hasSize(14);
        assertThat(stateMachine.allowedActions(WorkOrderStatus.COMPLETED)).isEmpty();
        assertThat(stateMachine.allowedActions(WorkOrderStatus.CANCELLED)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0} + {1}")
    @MethodSource("allCombinations")
    @DisplayName("70 种状态×动作组合：合法 14 条，其余全部拒绝")
    void everyCombination(WorkOrderStatus from, WorkOrderAction action) {
        String key = from.name() + "|" + action.name();
        Optional<WorkOrderTransition> resolved = stateMachine.resolve(from, action);
        Optional<String> matchedRule = LEGAL.stream().filter(rule -> rule.startsWith(key + "->")).findFirst();

        if (matchedRule.isPresent()) {
            assertThat(resolved).as("组合 %s 应当合法", key).isPresent();
            assertThat(resolved.get().to().name()).isEqualTo(targetOf(matchedRule.get()));
            assertThat(stateMachine.canTransition(from, action)).isTrue();
        } else {
            assertThat(resolved).as("组合 %s 必须被拒绝", key).isEmpty();
            assertThat(stateMachine.canTransition(from, action)).isFalse();
            BizException ex = (BizException) org.assertj.core.api.Assertions
                    .catchThrowable(() -> stateMachine.require(from, action));
            assertThat(ex).isNotNull();
            assertThat(ex.getResultCode().getCode()).isEqualTo(3001);
            assertThat(ex.getMessage()).contains("不支持动作");
        }
    }

    @Test
    @DisplayName("全部动作都在转移表中出现过，不存在只定义不生效的动作")
    void everyActionIsUsed() {
        // 同一动作可出现在多个起始状态（CANCEL 有 4 条、DISPATCH 有 2 条），这里按去重后的动作集合断言
        Set<WorkOrderAction> used = stateMachine.allTransitions().stream()
                .map(WorkOrderTransition::action)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(used).containsExactlyInAnyOrder(WorkOrderAction.values());
    }

    @Test
    @DisplayName("主流程闭环：待派单→待接单→维修中→待验收→已完成")
    void happyPath() {
        WorkOrderStatus s = WorkOrderStatus.PENDING_ASSIGN;
        s = stateMachine.require(s, WorkOrderAction.DISPATCH).to();
        assertThat(s).isEqualTo(WorkOrderStatus.PENDING_ACCEPT);
        s = stateMachine.require(s, WorkOrderAction.ACCEPT).to();
        assertThat(s).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        s = stateMachine.require(s, WorkOrderAction.SUBMIT_VERIFY).to();
        assertThat(s).isEqualTo(WorkOrderStatus.PENDING_REVIEW);
        s = stateMachine.require(s, WorkOrderAction.VERIFY_PASS).to();
        assertThat(s).isEqualTo(WorkOrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("验收不通过退回维修中，可再次提交验收")
    void verifyRejectReturnsToRepair() {
        WorkOrderStatus s = stateMachine.require(WorkOrderStatus.PENDING_REVIEW,
                WorkOrderAction.VERIFY_REJECT).to();
        assertThat(s).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(stateMachine.canTransition(s, WorkOrderAction.SUBMIT_VERIFY)).isTrue();
    }

    @Test
    @DisplayName("拒接后进入已驳回，可重新派单")
    void rejectThenRedispatch() {
        WorkOrderStatus s = stateMachine.require(WorkOrderStatus.PENDING_ACCEPT,
                WorkOrderAction.REJECT_DISPATCH).to();
        assertThat(s).isEqualTo(WorkOrderStatus.REJECTED);
        assertThat(stateMachine.canTransition(s, WorkOrderAction.DISPATCH)).isTrue();
        assertThat(stateMachine.allowedActions(WorkOrderStatus.REJECTED))
                .containsExactlyInAnyOrder(WorkOrderAction.DISPATCH, WorkOrderAction.CANCEL);
    }

    @Test
    @DisplayName("已完成为终态，任何动作都不可执行")
    void completedIsTerminal() {
        assertThat(WorkOrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(WorkOrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(WorkOrderStatus.IN_PROGRESS.isTerminal()).isFalse();
        assertThat(WorkOrderStatus.REJECTED.isTerminal()).isFalse();
        for (WorkOrderAction action : WorkOrderAction.values()) {
            assertThat(stateMachine.canTransition(WorkOrderStatus.COMPLETED, action)).isFalse();
            assertThat(stateMachine.canTransition(WorkOrderStatus.CANCELLED, action)).isFalse();
        }
    }

    @Test
    @DisplayName("非法流转的异常信息包含当前状态与允许动作，便于前端提示")
    void illegalTransitionMessage() {
        assertThatThrownBy(() -> stateMachine.require(WorkOrderStatus.COMPLETED, WorkOrderAction.DISPATCH))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已完成")
                .hasMessageContaining("派单");
        // 未接单不能直接提交验收（跨状态跳转）
        assertThatThrownBy(() -> stateMachine.require(WorkOrderStatus.PENDING_ACCEPT,
                WorkOrderAction.SUBMIT_VERIFY))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待接单");
    }

    @Test
    @DisplayName("只能由维修人本人执行的动作被正确标记")
    void assigneeOnlyActions() {
        Set<WorkOrderAction> assigneeOnly = Set.of(WorkOrderAction.ACCEPT, WorkOrderAction.REJECT_DISPATCH,
                WorkOrderAction.SUBMIT_VERIFY);
        for (WorkOrderTransition t : stateMachine.allTransitions()) {
            assertThat(t.assigneeOnly())
                    .as("动作 %s 的 assigneeOnly 标记", t.action())
                    .isEqualTo(assigneeOnly.contains(t.action()));
        }
    }

    @Test
    @DisplayName("原因最小长度：拒接/取消/强制转派 ≥5 字，验收不通过 ≥10 字")
    void reasonMinLength() {
        assertThat(stateMachine.require(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.REJECT_DISPATCH)
                .reasonMinLength()).isEqualTo(5);
        assertThat(stateMachine.require(WorkOrderStatus.PENDING_ASSIGN, WorkOrderAction.CANCEL)
                .reasonMinLength()).isEqualTo(5);
        assertThat(stateMachine.require(WorkOrderStatus.IN_PROGRESS, WorkOrderAction.FORCE_TRANSFER)
                .reasonMinLength()).isEqualTo(5);
        assertThat(stateMachine.require(WorkOrderStatus.PENDING_REVIEW, WorkOrderAction.VERIFY_REJECT)
                .reasonMinLength()).isEqualTo(10);
        // 派单、接单不强制填写原因
        assertThat(stateMachine.require(WorkOrderStatus.PENDING_ASSIGN, WorkOrderAction.DISPATCH)
                .reasonRequired()).isFalse();
        assertThat(stateMachine.require(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.ACCEPT)
                .reasonRequired()).isFalse();
    }

    @Test
    @DisplayName("原因长度不足时抛出 400，达到最小长度则通过")
    void checkReason() {
        WorkOrderTransition reject = stateMachine.require(WorkOrderStatus.PENDING_REVIEW,
                WorkOrderAction.VERIFY_REJECT);
        assertThatThrownBy(() -> stateMachine.checkReason(reject, "修得不好"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不少于 10 个字");
        stateMachine.checkReason(reject, "现场复查仍有异响，需重新调整联轴器同心度");
        stateMachine.checkReason(
                stateMachine.require(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.ACCEPT), null);
    }

    @Test
    @DisplayName("角色校验：非维修人不能接单，非管理角色不能派单")
    void checkPermission() {
        WorkOrderTransition accept = stateMachine.require(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.ACCEPT);
        stateMachine.checkPermission(accept, Set.of("REPAIRMAN"), true);
        assertThatThrownBy(() -> stateMachine.checkPermission(accept, Set.of("REPAIRMAN"), false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("维修人本人");
        assertThatThrownBy(() -> stateMachine.checkPermission(accept, Set.of("INSPECTOR"), true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权执行");

        WorkOrderTransition dispatch = stateMachine.require(WorkOrderStatus.PENDING_ASSIGN,
                WorkOrderAction.DISPATCH);
        stateMachine.checkPermission(dispatch, Set.of("EQUIP_ADMIN"), false);
        stateMachine.checkPermission(dispatch, Set.of("ADMIN"), false);
        assertThatThrownBy(() -> stateMachine.checkPermission(dispatch, Set.of("REPAIRMAN"), true))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("取消与强制转派只有管理角色可以执行")
    void cancelAndForceTransferRoles() {
        for (WorkOrderStatus from : new WorkOrderStatus[]{WorkOrderStatus.PENDING_ASSIGN,
                WorkOrderStatus.REJECTED, WorkOrderStatus.PENDING_ACCEPT, WorkOrderStatus.IN_PROGRESS}) {
            WorkOrderTransition cancel = stateMachine.require(from, WorkOrderAction.CANCEL);
            assertThat(cancel.allowedRoles()).contains("ADMIN", "EQUIP_ADMIN");
            assertThatThrownBy(() -> stateMachine.checkPermission(cancel, Set.of("REPAIRMAN"), true))
                    .isInstanceOf(BizException.class);
        }
        WorkOrderTransition force = stateMachine.require(WorkOrderStatus.IN_PROGRESS,
                WorkOrderAction.FORCE_TRANSFER);
        assertThat(force.allowedRoles()).containsExactly("ADMIN");
        assertThatThrownBy(() -> stateMachine.checkPermission(force, Set.of("EQUIP_ADMIN"), false))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("按状态给出允许动作列表，供前端渲染按钮")
    void allowedActionsByStatus() {
        assertThat(stateMachine.allowedActions(WorkOrderStatus.PENDING_ASSIGN))
                .containsExactlyInAnyOrder(WorkOrderAction.DISPATCH, WorkOrderAction.CANCEL);
        assertThat(stateMachine.allowedActions(WorkOrderStatus.PENDING_ACCEPT))
                .containsExactlyInAnyOrder(WorkOrderAction.ACCEPT, WorkOrderAction.REJECT_DISPATCH,
                        WorkOrderAction.TRANSFER, WorkOrderAction.REVOKE, WorkOrderAction.CANCEL);
        assertThat(stateMachine.allowedActions(WorkOrderStatus.IN_PROGRESS))
                .containsExactlyInAnyOrder(WorkOrderAction.SUBMIT_VERIFY, WorkOrderAction.FORCE_TRANSFER,
                        WorkOrderAction.CANCEL);
        assertThat(stateMachine.allowedActions(WorkOrderStatus.PENDING_REVIEW))
                .containsExactlyInAnyOrder(WorkOrderAction.VERIFY_PASS, WorkOrderAction.VERIFY_REJECT);
        assertThat(stateMachine.allowedActions(WorkOrderStatus.REJECTED))
                .containsExactlyInAnyOrder(WorkOrderAction.DISPATCH, WorkOrderAction.CANCEL);
    }

    @Test
    @DisplayName("空状态与空动作不会误判为合法")
    void nullSafety() {
        assertThat(stateMachine.resolve(null, WorkOrderAction.DISPATCH)).isEmpty();
        assertThat(stateMachine.resolve(WorkOrderStatus.PENDING_ASSIGN, null)).isEmpty();
        assertThat(stateMachine.allowedActions(null)).isEmpty();
        assertThatThrownBy(() -> stateMachine.require(null, null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("历史状态码仍能显示中文，且不再参与流转")
    void legacyStatusCode() {
        assertThat(WorkOrderStatus.labelOf("PENDING_DISPATCH")).isEqualTo("待派单");
        assertThat(WorkOrderStatus.labelOf("PENDING_VERIFY")).isEqualTo("待验收");
        assertThat(WorkOrderStatus.labelOf("SUSPENDED")).isEqualTo("已挂起");
        assertThat(WorkOrderStatus.isLegacy("SUSPENDED")).isTrue();
        assertThat(WorkOrderStatus.isLegacy("IN_PROGRESS")).isFalse();
        // 旧码不产生任何合法动作
        assertThat(stateMachine.allowedActions(WorkOrderStatus.of("SUSPENDED"))).isEmpty();
    }

    private static String targetOf(String key) {
        return key.substring(key.indexOf("->") + 2);
    }
}
