package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

import java.util.Map;

/**
 * 工单业务状态（第五阶段定义为 7 态）。
 *
 * <p>状态只能由人通过动作推进，系统定时任务不得修改；超时只升级 {@code sla_status}。
 *
 * <pre>
 * 待派单 PENDING_ASSIGN ──派单──▶ 待接单 PENDING_ACCEPT ──接单──▶ 维修中 IN_PROGRESS
 *        ▲                              │                              │
 *        │                           拒接│                              │提交验收
 *        └──────── 重新派单 ──── 已驳回 REJECTED                       ▼
 *                                                                待验收 PENDING_REVIEW
 *                                                                  │        │
 *                                                            验收通过│        │验收不通过
 *                                                                  ▼        ▼
 *                                                            已完成 COMPLETED  回到维修中
 * </pre>
 *
 * <p><b>两点约定</b>
 * <ol>
 *   <li>{@code REJECTED} 专指「维修人拒接、等待重新派单」，可再次派单；验收不通过按业务流程直接回到
 *       {@code IN_PROGRESS}，不占用该状态，驳回归属记在 {@code wo_verify_record} 与状态日志里。</li>
 *   <li>第五阶段按需求收敛为 7 态，第二阶段设计的 {@code SUSPENDED}（挂起）已移出状态机；
 *       历史上已产生的挂起记录在库里保留原码，展示层用 {@link #legacyLabel(String)} 兜底，
 *       不修改任何一条历史轨迹。</li>
 * </ol>
 */
public enum WorkOrderStatus implements EnumCode {

    PENDING_ASSIGN("PENDING_ASSIGN", "待派单", false),
    PENDING_ACCEPT("PENDING_ACCEPT", "待接单", false),
    IN_PROGRESS("IN_PROGRESS", "维修中", false),
    PENDING_REVIEW("PENDING_REVIEW", "待验收", false),
    REJECTED("REJECTED", "已驳回", false),
    COMPLETED("COMPLETED", "已完成", true),
    CANCELLED("CANCELLED", "已取消", true);

    /** 历史状态码 → 中文名。只用于展示既有轨迹，不参与任何流转判断。 */
    private static final Map<String, String> LEGACY_LABELS = Map.of(
            "PENDING_DISPATCH", "待派单",
            "PENDING_VERIFY", "待验收",
            "SUSPENDED", "已挂起"
    );

    private final String code;
    private final String label;
    private final boolean terminal;

    WorkOrderStatus(String code, String label, boolean terminal) {
        this.code = code;
        this.label = label;
        this.terminal = terminal;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }

    /** 是否终态：终态工单不再接受任何动作 */
    public boolean isTerminal() {
        return terminal;
    }

    public static WorkOrderStatus of(String code) {
        if (code == null) {
            return null;
        }
        for (WorkOrderStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 状态码 → 中文名，兼容第五阶段之前的历史状态码。
     *
     * <p>历史轨迹（{@code wo_status_log}）由触发器保护不可改写，升级脚本也不会去动它，
     * 因此详情页可能读到旧码；这里做展示兜底，避免页面上出现英文字符串。
     */
    public static String labelOf(String code) {
        WorkOrderStatus status = of(code);
        if (status != null) {
            return status.getLabel();
        }
        return LEGACY_LABELS.getOrDefault(code, code);
    }

    /** 是否为第五阶段之前的历史状态码 */
    public static boolean isLegacy(String code) {
        return code != null && of(code) == null && LEGACY_LABELS.containsKey(code);
    }
}
