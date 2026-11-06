package com.equipcare.workorder.statemachine;

import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import com.equipcare.workorder.enums.WorkOrderAction;
import com.equipcare.workorder.enums.WorkOrderStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 工单状态机：全系统修改工单状态的唯一规则来源。
 *
 * <p><b>设计纪律（对应第一阶段 5.4 节）</b>
 * <ol>
 *   <li><b>唯一入口</b>：任何改变 {@code wo_work_order.status} 的写操作都必须先经过本类
 *       {@link #require(WorkOrderStatus, WorkOrderAction)} 取得目标状态；</li>
 *   <li><b>转移表驱动</b>：未登记的 (from, action) 组合一律抛 {@code WO_ILLEGAL_TRANSITION}(3001)，
 *       并在异常信息中给出当前状态与允许的动作；</li>
 *   <li><b>动作而非状态</b>：对外只暴露动作，不接受前端传入目标状态；</li>
 *   <li><b>条件更新兜底</b>：由 {@code WorkOrderService} 以
 *       {@code WHERE id=? AND status=? AND version=?} 落库，影响行数为 0 即并发冲突。</li>
 * </ol>
 *
 * <p>本类为纯函数、无状态、无数据库依赖，便于对 7 状态 × 10 动作共 70 种组合做穷举单测。
 */
@Component
public class WorkOrderStateMachine {

    /** 角色编码：与 sys_role.role_code 保持一致 */
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EQUIP_ADMIN = "EQUIP_ADMIN";
    public static final String ROLE_VERIFIER = "VERIFIER";
    public static final String ROLE_REPAIRMAN = "REPAIRMAN";

    private static final Set<String> DISPATCH_ROLES = Set.of(ROLE_ADMIN, ROLE_EQUIP_ADMIN);
    private static final Set<String> VERIFY_ROLES = Set.of(ROLE_ADMIN, ROLE_EQUIP_ADMIN, ROLE_VERIFIER);
    private static final Set<String> ADMIN_ONLY = Set.of(ROLE_ADMIN);
    /** 取消工单是管理动作：需求明确「允许管理员在满足条件时取消」 */
    private static final Set<String> CANCEL_ROLES = Set.of(ROLE_ADMIN, ROLE_EQUIP_ADMIN);

    /** (from, action) -> 规则 */
    private final Map<WorkOrderStatus, Map<WorkOrderAction, WorkOrderTransition>> table;

    public WorkOrderStateMachine() {
        this.table = buildTable();
    }

    /**
     * 状态转移表 —— 与第五阶段需求「二、工单状态机」逐行对应，共 14 条规则。
     *
     * <p>合法路径：
     * <pre>
     * 待派单 --派单--> 待接单 --接单--> 维修中 --提交验收--> 待验收 --通过--> 已完成
     *                                  ▲                        │
     *                                  └────── 验收不通过 ───────┘
     * 待接单 --拒接--> 已驳回 --重新派单--> 待接单
     * 待接单 --转派/撤回--> 待接单 / 待派单
     * 维修中 --强制转派--> 维修中（仅 ADMIN）
     * 待派单/已驳回/待接单/维修中 --取消--> 已取消（仅管理员）
     * </pre>
     */
    private static Map<WorkOrderStatus, Map<WorkOrderAction, WorkOrderTransition>> buildTable() {
        Map<WorkOrderStatus, Map<WorkOrderAction, WorkOrderTransition>> map =
                new EnumMap<>(WorkOrderStatus.class);

        // 1. 派单：待派单 → 待接单
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_ASSIGN, WorkOrderAction.DISPATCH,
                WorkOrderStatus.PENDING_ACCEPT, DISPATCH_ROLES, false, 0, "派单"));
        // 2. 重新派单：已驳回 → 待接单（拒接后的工单回到派单池，不占用原维修人的待办）
        register(map, new WorkOrderTransition(WorkOrderStatus.REJECTED, WorkOrderAction.DISPATCH,
                WorkOrderStatus.PENDING_ACCEPT, DISPATCH_ROLES, false, 0, "重新派单"));
        // 3. 接单：待接单 → 维修中
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.ACCEPT,
                WorkOrderStatus.IN_PROGRESS, Set.of(ROLE_REPAIRMAN), true, 0, "接单"));
        // 4. 拒接：待接单 → 已驳回（必须写清拒接原因）
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.REJECT_DISPATCH,
                WorkOrderStatus.REJECTED, Set.of(ROLE_REPAIRMAN), true, 5, "拒接"));
        // 5. 转派：待接单 → 待接单（换人，维修人未变时由 Service 拦截）
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.TRANSFER,
                WorkOrderStatus.PENDING_ACCEPT, DISPATCH_ROLES, false, 0, "转派"));
        // 6. 撤回：待接单 → 待派单
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.REVOKE,
                WorkOrderStatus.PENDING_ASSIGN, DISPATCH_ROLES, false, 0, "撤回派单"));
        // 7. 提交验收：维修中 → 待验收
        register(map, new WorkOrderTransition(WorkOrderStatus.IN_PROGRESS, WorkOrderAction.SUBMIT_VERIFY,
                WorkOrderStatus.PENDING_REVIEW, Set.of(ROLE_REPAIRMAN), true, 0, "提交验收"));
        // 8. 验收通过：待验收 → 已完成
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_REVIEW, WorkOrderAction.VERIFY_PASS,
                WorkOrderStatus.COMPLETED, VERIFY_ROLES, false, 0, "验收通过"));
        // 9. 验收不通过：待验收 → 维修中（原因必须写清，少于 10 字拒绝）
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_REVIEW, WorkOrderAction.VERIFY_REJECT,
                WorkOrderStatus.IN_PROGRESS, VERIFY_ROLES, false, 10, "验收不通过"));
        // 10-13. 取消：管理员在未完结状态下可取消
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_ASSIGN, WorkOrderAction.CANCEL,
                WorkOrderStatus.CANCELLED, CANCEL_ROLES, false, 5, "取消工单"));
        register(map, new WorkOrderTransition(WorkOrderStatus.REJECTED, WorkOrderAction.CANCEL,
                WorkOrderStatus.CANCELLED, CANCEL_ROLES, false, 5, "取消工单"));
        register(map, new WorkOrderTransition(WorkOrderStatus.PENDING_ACCEPT, WorkOrderAction.CANCEL,
                WorkOrderStatus.CANCELLED, CANCEL_ROLES, false, 5, "取消工单"));
        register(map, new WorkOrderTransition(WorkOrderStatus.IN_PROGRESS, WorkOrderAction.CANCEL,
                WorkOrderStatus.CANCELLED, CANCEL_ROLES, false, 5, "取消工单"));
        // 14. 强制转派：维修中换人，状态不变
        register(map, new WorkOrderTransition(WorkOrderStatus.IN_PROGRESS, WorkOrderAction.FORCE_TRANSFER,
                WorkOrderStatus.IN_PROGRESS, ADMIN_ONLY, false, 5, "强制转派"));

        return map;
    }

    private static void register(Map<WorkOrderStatus, Map<WorkOrderAction, WorkOrderTransition>> map,
                                 WorkOrderTransition t) {
        Map<WorkOrderAction, WorkOrderTransition> row =
                map.computeIfAbsent(t.from(), k -> new EnumMap<>(WorkOrderAction.class));
        WorkOrderTransition exists = row.put(t.action(), t);
        if (exists != null) {
            throw new IllegalStateException("状态机配置重复：(" + t.from() + ", " + t.action() + ")");
        }
    }

    /**
     * 查询规则；不存在的组合返回 {@link Optional#empty()}。
     */
    public Optional<WorkOrderTransition> resolve(WorkOrderStatus from, WorkOrderAction action) {
        if (from == null || action == null) {
            return Optional.empty();
        }
        Map<WorkOrderAction, WorkOrderTransition> row = table.get(from);
        return row == null ? Optional.empty() : Optional.ofNullable(row.get(action));
    }

    /**
     * 取得规则，不存在则抛 {@code WO_ILLEGAL_TRANSITION}。
     */
    public WorkOrderTransition require(WorkOrderStatus from, WorkOrderAction action) {
        return resolve(from, action).orElseThrow(() -> new BizException(
                ResultCode.WO_ILLEGAL_TRANSITION,
                "工单当前状态「" + label(from) + "」不支持动作「"
                        + (action == null ? "null" : action.getLabel()) + "」，允许的动作："
                        + allowedActions(from)));
    }

    /**
     * 某状态下允许的动作集合，用于前端按钮渲染与错误提示。
     */
    public Set<WorkOrderAction> allowedActions(WorkOrderStatus from) {
        Map<WorkOrderAction, WorkOrderTransition> row = from == null ? null : table.get(from);
        if (row == null || row.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(row.keySet()));
    }

    /**
     * 是否允许从某状态执行某动作。
     */
    public boolean canTransition(WorkOrderStatus from, WorkOrderAction action) {
        return resolve(from, action).isPresent();
    }

    /**
     * 角色校验。
     *
     * @param transition 规则
     * @param roleCodes  操作人角色集合
     * @param isAssignee 操作人是否为当前工单维修人
     */
    public void checkPermission(WorkOrderTransition transition, Set<String> roleCodes, boolean isAssignee) {
        Set<String> roles = roleCodes == null ? Collections.emptySet() : roleCodes;
        if (transition.assigneeOnly() && !isAssignee) {
            throw BizException.forbidden("「" + transition.label() + "」只允许当前工单维修人本人执行");
        }
        if (!transition.allowedRoles().isEmpty()
                && roles.stream().noneMatch(transition.allowedRoles()::contains)) {
            throw BizException.forbidden("当前角色无权执行「" + transition.label() + "」");
        }
    }

    /**
     * 原因校验：长度不足时抛出参数错误。
     */
    public void checkReason(WorkOrderTransition transition, String reason) {
        if (!transition.reasonRequired()) {
            return;
        }
        String text = reason == null ? "" : reason.trim();
        if (text.length() < transition.reasonMinLength()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "「" + transition.label() + "」需填写原因，且不少于 "
                            + transition.reasonMinLength() + " 个字");
        }
    }

    /** 全部规则，供文档与测试遍历 */
    public Set<WorkOrderTransition> allTransitions() {
        Set<WorkOrderTransition> all = new LinkedHashSet<>();
        table.values().forEach(row -> all.addAll(row.values()));
        return Collections.unmodifiableSet(all);
    }

    private static String label(WorkOrderStatus status) {
        return status == null ? "未知" : status.getLabel();
    }

    /** 测试辅助：列出所有状态 */
    public static Set<WorkOrderStatus> allStatuses() {
        return Collections.unmodifiableSet(EnumSet.allOf(WorkOrderStatus.class));
    }

    /** 测试辅助：合法组合数量 */
    public int size() {
        Map<WorkOrderStatus, Integer> counter = new HashMap<>();
        table.forEach((k, v) -> counter.put(k, v.size()));
        return counter.values().stream().mapToInt(Integer::intValue).sum();
    }
}
