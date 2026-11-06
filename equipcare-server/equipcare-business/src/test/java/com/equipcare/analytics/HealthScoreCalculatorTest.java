package com.equipcare.analytics;

import com.equipcare.analytics.dto.HealthMetrics;
import com.equipcare.analytics.service.HealthScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康分计算单元测试：验证公式、上限截断与等级划分。
 */
class HealthScoreCalculatorTest {

    @Test
    @DisplayName("无任何工单与异常时满分为 100，等级健康")
    void perfectScore() {
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(new HealthMetrics());
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(result.level()).isEqualTo("HEALTHY");
        assertThat(result.faultScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("故障扣分 = 工单数×2 + 紧急工单数×3，上限 30")
    void faultScore() {
        HealthMetrics m = new HealthMetrics();
        m.setWorkOrderCount(2);
        m.setUrgentWorkOrderCount(1);
        // 2*2 + 1*3 = 7
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.faultScore()).isEqualByComparingTo(BigDecimal.valueOf(7));
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(93));

        HealthMetrics heavy = new HealthMetrics();
        heavy.setWorkOrderCount(50);
        heavy.setUrgentWorkOrderCount(50);
        assertThat(HealthScoreCalculator.calculate(heavy).faultScore())
                .isEqualByComparingTo(BigDecimal.valueOf(HealthScoreCalculator.MAX_FAULT));
    }

    @Test
    @DisplayName("超时扣分 = 超时工单数×5，上限 25")
    void timeoutScore() {
        HealthMetrics m = new HealthMetrics();
        m.setOverdueWorkOrderCount(2);
        assertThat(HealthScoreCalculator.calculate(m).timeoutScore())
                .isEqualByComparingTo(BigDecimal.valueOf(10));

        m.setOverdueWorkOrderCount(100);
        assertThat(HealthScoreCalculator.calculate(m).timeoutScore())
                .isEqualByComparingTo(BigDecimal.valueOf(HealthScoreCalculator.MAX_TIMEOUT));
    }

    @Test
    @DisplayName("异常扣分含严重异常加权，上限 20")
    void anomalyScore() {
        HealthMetrics m = new HealthMetrics();
        m.setAnomalyCount(3);
        m.setHighAnomalyCount(2);
        // 3*2 + 2*2 = 10
        assertThat(HealthScoreCalculator.calculate(m).anomalyScore())
                .isEqualByComparingTo(BigDecimal.valueOf(10));

        m.setAnomalyCount(100);
        m.setHighAnomalyCount(100);
        assertThat(HealthScoreCalculator.calculate(m).anomalyScore())
                .isEqualByComparingTo(BigDecimal.valueOf(HealthScoreCalculator.MAX_ANOMALY));
    }

    @Test
    @DisplayName("巡检逾期扣分与正常完成加分互相抵消")
    void overdueAndBonus() {
        HealthMetrics m = new HealthMetrics();
        m.setOverdueInspectCount(2);   // -10
        m.setNormalInspectCount(5);    // +5
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.overdueScore()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(result.bonusScore()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(95));
    }

    @Test
    @DisplayName("多项叠加后分数被限制在 0-100 之间，不会出现负分")
    void clampedToZero() {
        HealthMetrics m = new HealthMetrics();
        m.setWorkOrderCount(30);
        m.setUrgentWorkOrderCount(30);
        m.setOverdueWorkOrderCount(30);
        m.setAnomalyCount(30);
        m.setHighAnomalyCount(30);
        m.setOverdueInspectCount(30);
        // 上限扣分合计 30+25+20+15 = 90
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(result.level()).isEqualTo("DANGER");
    }

    @Test
    @DisplayName("加分不会把分数推高到 100 以上")
    void bonusCannotExceedHundred() {
        HealthMetrics m = new HealthMetrics();
        m.setNormalInspectCount(200);
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.bonusScore()).isEqualByComparingTo(BigDecimal.valueOf(HealthScoreCalculator.MAX_BONUS));
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("等级边界：90 健康 / 75 亚健康 / 60 预警 / 其余危险")
    void levelBoundary() {
        assertThat(HealthScoreCalculator.level(100)).isEqualTo("HEALTHY");
        assertThat(HealthScoreCalculator.level(90)).isEqualTo("HEALTHY");
        assertThat(HealthScoreCalculator.level(89)).isEqualTo("SUB_HEALTHY");
        assertThat(HealthScoreCalculator.level(75)).isEqualTo("SUB_HEALTHY");
        assertThat(HealthScoreCalculator.level(74)).isEqualTo("WARNING");
        assertThat(HealthScoreCalculator.level(60)).isEqualTo("WARNING");
        assertThat(HealthScoreCalculator.level(59)).isEqualTo("DANGER");
        assertThat(HealthScoreCalculator.level(0)).isEqualTo("DANGER");
    }

    @Test
    @DisplayName("空指标按 0 处理，不抛空指针")
    void nullMetrics() {
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(null);
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("等级中文名可读")
    void levelLabel() {
        assertThat(HealthScoreCalculator.levelLabel("HEALTHY")).isEqualTo("健康");
        assertThat(HealthScoreCalculator.levelLabel("DANGER")).isEqualTo("危险");
        assertThat(HealthScoreCalculator.levelLabel("UNKNOWN_X")).isEqualTo("未知");
    }

    // ==================================================================
    // 第六阶段 v2：未完成工单 / 久未维修 / 运行状态
    // ==================================================================

    @Test
    @DisplayName("v2 未完成工单：每张扣 3 分，15 分封顶")
    void openOrderDeduction() {
        HealthMetrics m = new HealthMetrics();
        m.setOpenWorkOrderCount(3);
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.openOrderScore()).isEqualByComparingTo(BigDecimal.valueOf(9));
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(91));

        m.setOpenWorkOrderCount(10);
        result = HealthScoreCalculator.calculate(m);
        assertThat(result.openOrderScore()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(85));
    }

    @Test
    @DisplayName("v2 久未维修：>180 天扣 5，>365 天扣 10，从未维修不扣")
    void idleDeduction() {
        assertThat(HealthScoreCalculator.idleDeduction(null)).isZero();
        assertThat(HealthScoreCalculator.idleDeduction(180)).isZero();
        assertThat(HealthScoreCalculator.idleDeduction(181)).isEqualTo(5);
        assertThat(HealthScoreCalculator.idleDeduction(365)).isEqualTo(5);
        assertThat(HealthScoreCalculator.idleDeduction(366)).isEqualTo(10);

        HealthMetrics m = new HealthMetrics();
        m.setIdleDays(400);
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.idleScore()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(90));
    }

    @Test
    @DisplayName("v2 运行状态：闲置扣 5，在用/维修中不扣")
    void statusDeduction() {
        assertThat(HealthScoreCalculator.statusDeduction("IN_USE")).isZero();
        assertThat(HealthScoreCalculator.statusDeduction("REPAIRING")).isZero();
        assertThat(HealthScoreCalculator.statusDeduction("IDLE")).isEqualTo(5);

        HealthMetrics m = new HealthMetrics();
        m.setEquipmentStatus("IDLE");
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.statusScore()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(95));
    }

    @Test
    @DisplayName("v2 叠加扣分不超过 0 分下限")
    void v2ClampToZero() {
        HealthMetrics m = new HealthMetrics();
        m.setWorkOrderCount(20);          // 故障扣 30（封顶）
        m.setOverdueWorkOrderCount(5);    // 超时扣 25（封顶）
        m.setAnomalyCount(10);            // 异常扣 20（封顶）
        m.setOverdueInspectCount(3);      // 逾期扣 15（封顶）
        m.setOpenWorkOrderCount(10);      // 未完成工单扣 15（封顶）
        m.setIdleDays(500);               // 久未维修扣 10（封顶）
        m.setEquipmentStatus("IDLE");     // 闲置扣 5
        // 100 - 30 - 25 - 20 - 15 - 15 - 10 - 5 = -20 → 截断为 0
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(m);
        assertThat(result.healthScore()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }
}
