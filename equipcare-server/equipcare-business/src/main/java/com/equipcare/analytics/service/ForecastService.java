package com.equipcare.analytics.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.analytics.entity.EqHealthSnapshot;
import com.equipcare.analytics.mapper.EqHealthSnapshotMapper;
import com.equipcare.analytics.vo.EquipmentForecastVO;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.workorder.entity.WoWorkOrder;
import com.equipcare.workorder.enums.WorkOrderStatus;
import com.equipcare.workorder.mapper.WoWorkOrderMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预测性维护（第九期）：MTBF / MTTR / 健康趋势外推。
 *
 * <p>口径（全部只读既有表，不引入新表）：
 * <ul>
 *   <li><b>MTTR</b>（平均修复时长，小时）：近 90 天已完成工单的
 *       「开始维修 → 实际完成」均值，缺开始维修时间回退「报修 → 实际完成」；</li>
 *   <li><b>MTBF</b>（平均故障间隔，小时）：90 天观察窗 ÷ 窗口内非取消工单数，
 *       无故障单返回 null（表示窗口内零故障，而非无限可靠）；</li>
 *   <li><b>趋势外推</b>：对最近 ≤8 个健康分快照做最小二乘线性回归得每日斜率，
 *       7 天预测分 = 最新分 + 斜率 × 7（截断到 [0,100]）；快照 &lt;3 个不做外推。</li>
 * </ul>
 * 风险分级：预测分 &lt;60 → HIGH；斜率 &lt;-0.5 分/天 → WATCH；其余 LOW。
 * 设备量为几十到几百台，内存聚合一次请求完全可行；数据量增长后再下沉 SQL。
 */
@Service
@ConditionalOnProperty(name = "equipcare.analytics.forecast.enabled", havingValue = "true", matchIfMissing = true)
public class ForecastService {

    private static final int WINDOW_DAYS = 90;
    private static final int TREND_POINTS = 8;
    private static final int FORECAST_DAYS = 7;
    private static final double WATCH_SLOPE = -0.5;
    private static final double HIGH_PREDICTED = 60.0;

    private final EqEquipmentMapper equipmentMapper;
    private final EqHealthSnapshotMapper snapshotMapper;
    private final WoWorkOrderMapper orderMapper;

    public ForecastService(EqEquipmentMapper equipmentMapper, EqHealthSnapshotMapper snapshotMapper,
                           WoWorkOrderMapper orderMapper) {
        this.equipmentMapper = equipmentMapper;
        this.snapshotMapper = snapshotMapper;
        this.orderMapper = orderMapper;
    }

    /** 全量设备的预测视图（按预测分升序，最危险的排最前） */
    public List<EquipmentForecastVO> forecastAll() {
        LocalDateTime windowStart = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<EqEquipment> equipments = equipmentMapper.selectList(
                Wrappers.<EqEquipment>lambdaQuery().orderByAsc(EqEquipment::getId));
        if (equipments.isEmpty()) {
            return List.of();
        }
        List<Long> equipmentIds = equipments.stream().map(EqEquipment::getId).toList();
        // 健康快照（近 8 个点足够回归）与窗口内工单，一次性取出后按设备分组
        Map<Long, List<EqHealthSnapshot>> snapshots = snapshotMapper.selectList(
                        Wrappers.<EqHealthSnapshot>lambdaQuery()
                                .in(EqHealthSnapshot::getEquipmentId, equipmentIds)
                                .orderByDesc(EqHealthSnapshot::getStatDate))
                .stream().collect(Collectors.groupingBy(EqHealthSnapshot::getEquipmentId));
        Map<Long, List<WoWorkOrder>> orders = orderMapper.selectList(
                        Wrappers.<WoWorkOrder>lambdaQuery()
                                .in(WoWorkOrder::getEquipmentId, equipmentIds)
                                .ge(WoWorkOrder::getReportTime, windowStart)
                                .ne(WoWorkOrder::getStatus, WorkOrderStatus.CANCELLED.getCode()))
                .stream().collect(Collectors.groupingBy(WoWorkOrder::getEquipmentId));

        List<EquipmentForecastVO> result = new ArrayList<>();
        for (EqEquipment equipment : equipments) {
            result.add(buildOne(equipment,
                    snapshots.getOrDefault(equipment.getId(), List.of()),
                    orders.getOrDefault(equipment.getId(), List.of())));
        }
        result.sort(Comparator.comparing(
                vo -> vo.getPredictedScore7d() == null ? 200.0 : vo.getPredictedScore7d()));
        return result;
    }

    private EquipmentForecastVO buildOne(EqEquipment equipment,
                                         List<EqHealthSnapshot> snapshots,
                                         List<WoWorkOrder> windowOrders) {
        EquipmentForecastVO vo = new EquipmentForecastVO();
        vo.setEquipmentId(equipment.getId());
        vo.setEquipmentName(equipment.getEquipmentName());
        vo.setAssetNo(equipment.getAssetNo());
        vo.setStatus(equipment.getStatus());

        // ---- 健康趋势（快照按日期升序） ----
        List<EqHealthSnapshot> asc = snapshots.stream()
                .sorted(Comparator.comparing(EqHealthSnapshot::getStatDate))
                .toList();
        List<EqHealthSnapshot> recent = asc.size() > TREND_POINTS
                ? asc.subList(asc.size() - TREND_POINTS, asc.size()) : asc;
        vo.setTrend(recent.stream().map(s -> EquipmentForecastVO.TrendPoint.of(
                s.getStatDate(), s.getHealthScore() == null ? null
                        : s.getHealthScore().doubleValue())).toList());
        EqHealthSnapshot latest = asc.isEmpty() ? null : asc.get(asc.size() - 1);
        vo.setLatestScore(latest == null || latest.getHealthScore() == null
                ? null : latest.getHealthScore().doubleValue());

        // ---- 线性回归斜率 + 7 天外推 ----
        Double slope = regressionSlope(recent);
        vo.setSlopePerDay(slope);
        if (slope != null && vo.getLatestScore() != null) {
            double predicted = clamp(vo.getLatestScore() + slope * FORECAST_DAYS);
            vo.setPredictedScore7d(predicted);
        }

        // ---- MTTR / MTBF（90 天窗口） ----
        vo.setFailureCount90d(windowOrders.size());
        double mttrSum = 0;
        int mttrCount = 0;
        for (WoWorkOrder order : windowOrders) {
            if (!WorkOrderStatus.COMPLETED.getCode().equals(order.getStatus())
                    || order.getActualFinishTime() == null) {
                continue;
            }
            LocalDateTime start = order.getStartRepairTime() != null
                    ? order.getStartRepairTime() : order.getReportTime();
            if (start == null || order.getActualFinishTime().isBefore(start)) {
                continue;
            }
            mttrSum += Duration.between(start, order.getActualFinishTime()).toMinutes() / 60.0;
            mttrCount++;
        }
        vo.setMttrHours(mttrCount == 0 ? null : round1(mttrSum / mttrCount));
        vo.setMtbfHours(windowOrders.isEmpty() ? null
                : round1((double) WINDOW_DAYS * 24 / windowOrders.size()));

        // ---- 风险分级 ----
        if (vo.getPredictedScore7d() != null && vo.getPredictedScore7d() < HIGH_PREDICTED) {
            vo.setRiskLevel("HIGH");
            vo.setRiskReason(String.format("趋势外推 7 天后健康分约 %.0f 分（低于 60 分警戒线）", vo.getPredictedScore7d()));
        } else if (slope != null && slope < WATCH_SLOPE) {
            vo.setRiskLevel("WATCH");
            vo.setRiskReason(String.format("健康分以每天 %.2f 分下滑，建议关注", slope));
        } else if (recent.size() < 3) {
            vo.setRiskLevel("WATCH");
            vo.setRiskReason("健康快照不足 3 天，趋势数据积累中");
        } else {
            vo.setRiskLevel("LOW");
            vo.setRiskReason("趋势平稳");
        }
        return vo;
    }

    /** 最小二乘斜率（x=天数序号，y=分数）；点数不足 3 或分数缺失返回 null */
    private Double regressionSlope(List<EqHealthSnapshot> asc) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < asc.size(); i++) {
            if (asc.get(i).getHealthScore() != null) {
                pts.add(new double[]{i, asc.get(i).getHealthScore().doubleValue()});
            }
        }
        if (pts.size() < 3) {
            return null;
        }
        int n = pts.size();
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (double[] p : pts) {
            sx += p[0];
            sy += p[1];
            sxy += p[0] * p[1];
            sxx += p[0] * p[0];
        }
        double denominator = n * sxx - sx * sx;
        if (denominator == 0) {
            return null;
        }
        return (n * sxy - sx * sy) / denominator;
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
