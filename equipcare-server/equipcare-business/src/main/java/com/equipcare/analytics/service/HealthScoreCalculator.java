package com.equipcare.analytics.service;

import com.equipcare.analytics.dto.HealthMetrics;
import com.equipcare.asset.enums.EquipmentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 设备健康分计算器（纯函数，无外部依赖，便于单元测试）。
 *
 * <p>口径固定为「100 分起算，八类项目扣减，巡检正常完成给予少量加分」。
 * 前四项为 v1 因素；后三项（未完成工单 / 久未维修 / 运行状态）为第六阶段 v2 新增，
 * 与需求参考因素一一对应：
 * <pre>
 * health = clamp(100 - F - T - A - M - O - I - S + R, 0, 100)
 *
 * F 故障扣分 = min(30, 工单数 × 2 + 紧急工单数 × 3)
 * T 超时扣分 = min(25, 超时工单数 × 5)
 * A 异常扣分 = min(20, 异常数 × 2 + 严重异常数 × 2)
 * M 逾期扣分 = min(15, 逾期巡检数 × 5)
 * O 未完成工单扣分 = min(15, 未完成工单数 × 3)
 * I 久未维修扣分 = 距上次完成维修：>180 天扣 5，>365 天扣 10（从未维修不扣，新设备属正常）
 * S 运行状态扣分 = 闲置（IDLE）扣 5（维修中 REPAIRING 由工单因素覆盖，不重复扣）
 * R 巡检加分 = min(10, 正常完成巡检数 × 1)
 * </pre>
 *
 * <p>每一项都可以单独展示给用户，避免「一个黑盒分数」无法解释的问题。
 */
public final class HealthScoreCalculator {

    /** 故障扣分上限 */
    public static final int MAX_FAULT = 30;
    /** 超时扣分上限 */
    public static final int MAX_TIMEOUT = 25;
    /** 异常扣分上限 */
    public static final int MAX_ANOMALY = 20;
    /** 逾期扣分上限 */
    public static final int MAX_OVERDUE = 15;
    /** 未完成工单扣分上限（v2） */
    public static final int MAX_OPEN_ORDER = 15;
    /** 久未维修扣分上限（v2） */
    public static final int MAX_IDLE = 10;
    /** 运行状态扣分上限（v2） */
    public static final int MAX_STATUS = 5;
    /** 加分上限 */
    public static final int MAX_BONUS = 10;

    /** 久未维修的两档天数阈值（v2） */
    public static final int IDLE_DAYS_WARN = 180;
    public static final int IDLE_DAYS_DANGER = 365;

    private HealthScoreCalculator() {
    }

    /**
     * 计算结果。所有分项都为扣分值（正数表示扣了多少分）。
     */
    public record Result(BigDecimal healthScore,
                         BigDecimal faultScore,
                         BigDecimal timeoutScore,
                         BigDecimal anomalyScore,
                         BigDecimal overdueScore,
                         BigDecimal openOrderScore,
                         BigDecimal idleScore,
                         BigDecimal statusScore,
                         BigDecimal bonusScore,
                         String level) {
    }

    public static Result calculate(HealthMetrics m) {
        HealthMetrics metrics = m == null ? new HealthMetrics() : m;
        int workOrders = nvl(metrics.getWorkOrderCount());
        int urgent = nvl(metrics.getUrgentWorkOrderCount());
        int overdueOrders = nvl(metrics.getOverdueWorkOrderCount());
        int anomalies = nvl(metrics.getAnomalyCount());
        int highAnomalies = nvl(metrics.getHighAnomalyCount());
        int overdueInspections = nvl(metrics.getOverdueInspectCount());
        int normalInspections = nvl(metrics.getNormalInspectCount());
        int openOrders = nvl(metrics.getOpenWorkOrderCount());

        int fault = Math.min(MAX_FAULT, workOrders * 2 + urgent * 3);
        int timeout = Math.min(MAX_TIMEOUT, overdueOrders * 5);
        int anomaly = Math.min(MAX_ANOMALY, anomalies * 2 + highAnomalies * 2);
        int overdue = Math.min(MAX_OVERDUE, overdueInspections * 5);
        int open = Math.min(MAX_OPEN_ORDER, openOrders * 3);
        int idle = idleDeduction(metrics.getIdleDays());
        int status = statusDeduction(metrics.getEquipmentStatus());
        int bonus = Math.min(MAX_BONUS, normalInspections);

        int raw = 100 - fault - timeout - anomaly - overdue - open - idle - status + bonus;
        int clamped = Math.max(0, Math.min(100, raw));

        return new Result(scale(clamped), scale(fault), scale(timeout), scale(anomaly),
                scale(overdue), scale(open), scale(idle), scale(status), scale(bonus),
                level(clamped));
    }

    /**
     * 久未维修扣分：&gt;180 天扣 5，&gt;365 天扣 10；从未维修（null）不扣——
     * 新投用设备没有维修记录是正常状态，不应惩罚。
     */
    public static int idleDeduction(Integer idleDays) {
        if (idleDays == null) {
            return 0;
        }
        if (idleDays > IDLE_DAYS_DANGER) {
            return 10;
        }
        if (idleDays > IDLE_DAYS_WARN) {
            return 5;
        }
        return 0;
    }

    /** 运行状态扣分：闲置扣 5；维修中不扣（未完成工单因素已覆盖）；报废设备不参与计算 */
    public static int statusDeduction(String status) {
        return EquipmentStatus.IDLE.getCode().equals(status) ? 5 : 0;
    }

    /**
     * 健康等级：≥90 健康，≥75 亚健康，≥60 预警，其余危险。
     */
    public static String level(int score) {
        if (score >= 90) {
            return "HEALTHY";
        }
        if (score >= 75) {
            return "SUB_HEALTHY";
        }
        if (score >= 60) {
            return "WARNING";
        }
        return "DANGER";
    }

    public static String levelLabel(String level) {
        return switch (level) {
            case "HEALTHY" -> "健康";
            case "SUB_HEALTHY" -> "亚健康";
            case "WARNING" -> "预警";
            case "DANGER" -> "危险";
            default -> "未知";
        };
    }

    private static BigDecimal scale(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
