package com.equipcare.analytics.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.analytics.dto.HealthMetrics;
import com.equipcare.analytics.entity.EqHealthSnapshot;
import com.equipcare.analytics.mapper.EqHealthSnapshotMapper;
import com.equipcare.analytics.mapper.HealthStatMapper;
import com.equipcare.analytics.service.HealthScoreCalculator;
import com.equipcare.analytics.service.HealthService;
import com.equipcare.analytics.vo.HealthVO;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.enums.EquipmentStatus;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备健康分实现。
 *
 * <p>计算窗口默认 30 天，快照按「设备 + 日期」唯一键 upsert，
 * 同一天重复计算不会产生脏数据。
 */
@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthServiceImpl.class);

    /** 统计窗口天数 */
    private static final int WINDOW_DAYS = 30;
    private static final int TREND_LIMIT = 30;

    private final HealthStatMapper statMapper;
    private final EqHealthSnapshotMapper snapshotMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final ObjectMapper objectMapper;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public HealthServiceImpl(HealthStatMapper statMapper, EqHealthSnapshotMapper snapshotMapper,
                             EqEquipmentMapper equipmentMapper, ObjectMapper objectMapper,
                             org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        this.statMapper = statMapper;
        this.snapshotMapper = snapshotMapper;
        this.equipmentMapper = equipmentMapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public HealthVO recalculate(Long equipmentId) {
        // 单台重算跨 eq_health_snapshot + eq_equipment 两表写入，必须同事务。
        // 用 TransactionTemplate 而非 @Transactional：批量重算路径（recalculateAll）
        // 存在同类自调用，Spring 代理不生效（阶段十二整改 Q-07）。
        return transactionTemplate.execute(status -> doRecalculate(equipmentId));
    }

    private HealthVO doRecalculate(Long equipmentId) {
        EqEquipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        HealthMetrics metrics = collectMetrics(equipmentId);
        metrics.setEquipmentStatus(equipment.getStatus());
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(metrics);
        List<HealthVO.RiskItem> riskItems = buildRiskItems(metrics, result);

        LocalDate today = LocalDate.now();
        EqHealthSnapshot snapshot = snapshotMapper.selectByEquipmentAndDate(equipmentId, today);
        boolean insert = snapshot == null;
        if (insert) {
            snapshot = new EqHealthSnapshot();
            snapshot.setEquipmentId(equipmentId);
            snapshot.setStatDate(today);
            snapshot.setCreateTime(LocalDateTime.now());
        }
        snapshot.setHealthScore(result.healthScore());
        snapshot.setFaultScore(result.faultScore());
        snapshot.setTimeoutScore(result.timeoutScore());
        snapshot.setAnomalyScore(result.anomalyScore());
        snapshot.setOverdueScore(result.overdueScore());
        snapshot.setBonusScore(result.bonusScore());
        snapshot.setWorkOrderCount(metrics.getWorkOrderCount());
        snapshot.setAnomalyCount(metrics.getAnomalyCount());
        snapshot.setOpenWoCount(metrics.getOpenWorkOrderCount());
        snapshot.setIdleDays(metrics.getIdleDays());
        snapshot.setRiskDetail(toJson(riskItems));
        snapshot.setLevel(result.level());
        if (insert) {
            snapshotMapper.insert(snapshot);
        } else {
            snapshotMapper.updateById(snapshot);
        }

        // 同步设备表的冗余字段，列表页无需联表即可展示健康分
        EqEquipment patch = new EqEquipment();
        patch.setId(equipmentId);
        patch.setHealthScore(result.healthScore());
        equipmentMapper.update(patch, Wrappers.<EqEquipment>lambdaUpdate()
                .eq(EqEquipment::getId, equipmentId));

        HealthVO vo = toVO(equipment, snapshot, true);
        vo.setRiskItems(riskItems);
        vo.setSuggestions(suggestionsOf(riskItems));
        return vo;
    }

    @Override
    public HealthVO current(Long equipmentId) {
        EqEquipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        EqHealthSnapshot snapshot = snapshotMapper.selectByEquipmentAndDate(equipmentId, LocalDate.now());
        if (snapshot != null) {
            HealthVO vo = toVO(equipment, snapshot, true);
            List<HealthVO.RiskItem> items = fromJson(snapshot.getRiskDetail());
            vo.setRiskItems(items);
            vo.setSuggestions(suggestionsOf(items));
            return vo;
        }
        // 当日还没算过：实时计算但不落库，保证接口随时可读
        HealthMetrics metrics = collectMetrics(equipmentId);
        HealthScoreCalculator.Result result = HealthScoreCalculator.calculate(metrics);
        EqHealthSnapshot temp = new EqHealthSnapshot();
        temp.setEquipmentId(equipmentId);
        temp.setStatDate(LocalDate.now());
        temp.setHealthScore(result.healthScore());
        temp.setFaultScore(result.faultScore());
        temp.setTimeoutScore(result.timeoutScore());
        temp.setAnomalyScore(result.anomalyScore());
        temp.setOverdueScore(result.overdueScore());
        temp.setBonusScore(result.bonusScore());
        temp.setWorkOrderCount(metrics.getWorkOrderCount());
        temp.setAnomalyCount(metrics.getAnomalyCount());
        temp.setOpenWoCount(metrics.getOpenWorkOrderCount());
        temp.setIdleDays(metrics.getIdleDays());
        temp.setRiskDetail(toJson(buildRiskItems(metrics, result)));
        temp.setLevel(result.level());
        HealthVO vo = toVO(equipment, temp, false);
        vo.setRiskItems(buildRiskItems(metrics, result));
        vo.setSuggestions(suggestionsOf(vo.getRiskItems()));
        return vo;
    }

    @Override
    public int recalculateAll() {
        List<EqEquipment> equipmentList = equipmentMapper.selectList(Wrappers.<EqEquipment>lambdaQuery()
                .ne(EqEquipment::getStatus, EquipmentStatus.SCRAPPED.getCode()));
        int count = 0;
        for (EqEquipment equipment : equipmentList) {
            try {
                recalculate(equipment.getId()); // 单台独立事务：一台失败不影响其他设备
                count++;
            } catch (RuntimeException e) {
                log.warn("设备健康分计算失败 equipmentId={}", equipment.getId(), e);
            }
        }
        log.info("设备健康分批量重算完成，共 {} 台", count);
        return count;
    }

    @Override
    public List<HealthVO> atRisk(int threshold, int limit) {
        int safeThreshold = threshold <= 0 || threshold > 100 ? 75 : threshold;
        int safeLimit = limit <= 0 || limit > 200 ? 20 : limit;
        List<EqEquipment> equipmentList = equipmentMapper.selectList(Wrappers.<EqEquipment>lambdaQuery()
                .lt(EqEquipment::getHealthScore, safeThreshold)
                .ne(EqEquipment::getStatus, EquipmentStatus.SCRAPPED.getCode())
                .orderByAsc(EqEquipment::getHealthScore)
                .last("LIMIT " + safeLimit));
        List<HealthVO> list = new ArrayList<>();
        for (EqEquipment equipment : equipmentList) {
            EqHealthSnapshot snapshot = snapshotMapper.selectByEquipmentAndDate(equipment.getId(), LocalDate.now());
            HealthVO vo = new HealthVO();
            vo.setEquipmentId(equipment.getId());
            vo.setEquipmentName(equipment.getEquipmentName());
            vo.setAssetNo(equipment.getAssetNo());
            vo.setHealthScore(equipment.getHealthScore());
            String level = HealthScoreCalculator.level(
                    equipment.getHealthScore() == null ? 100 : equipment.getHealthScore().intValue());
            vo.setLevel(level);
            vo.setLevelLabel(HealthScoreCalculator.levelLabel(level));
            if (snapshot != null) {
                vo.setStatDate(snapshot.getStatDate());
                vo.setFaultScore(snapshot.getFaultScore());
                vo.setTimeoutScore(snapshot.getTimeoutScore());
                vo.setAnomalyScore(snapshot.getAnomalyScore());
                vo.setOverdueScore(snapshot.getOverdueScore());
                vo.setBonusScore(snapshot.getBonusScore());
                vo.setWorkOrderCount(snapshot.getWorkOrderCount());
                vo.setAnomalyCount(snapshot.getAnomalyCount());
                vo.setOpenWoCount(snapshot.getOpenWoCount());
                vo.setIdleDays(snapshot.getIdleDays());
            }
            list.add(vo);
        }
        return list;
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    private HealthMetrics collectMetrics(Long equipmentId) {
        HealthMetrics workOrder = statMapper.selectWorkOrderMetrics(equipmentId, WINDOW_DAYS);
        HealthMetrics anomaly = statMapper.selectAnomalyMetrics(equipmentId, WINDOW_DAYS);
        HealthMetrics inspect = statMapper.selectInspectMetrics(equipmentId, WINDOW_DAYS);
        HealthMetrics open = statMapper.selectOpenOrderMetrics(equipmentId);
        HealthMetrics merged = new HealthMetrics();
        merged.merge(workOrder).merge(anomaly).merge(inspect);
        merged.setOpenWorkOrderCount(open.getOpenWorkOrderCount() == null ? 0 : open.getOpenWorkOrderCount());
        LocalDateTime lastFinish = statMapper.selectLastFinishTime(equipmentId);
        merged.setIdleDays(lastFinish == null ? null
                : (int) ChronoUnit.DAYS.between(lastFinish.toLocalDate(), LocalDate.now()));
        return merged;
    }

    // ==================================================================
    // v2：逐项扣分明细与建议
    // ==================================================================

    /**
     * 逐项扣分明细：只列出「真正发生了扣分 / 加分」的因素，
     * 每条带原始计数、计算规则与建议措施——分数必须可解释。
     */
    private List<HealthVO.RiskItem> buildRiskItems(HealthMetrics m, HealthScoreCalculator.Result r) {
        List<HealthVO.RiskItem> items = new ArrayList<>();
        int workOrders = nvl(m.getWorkOrderCount());
        int urgent = nvl(m.getUrgentWorkOrderCount());
        if (r.faultScore().signum() > 0) {
            items.add(item("FAULT", "故障频次", r.faultScore(),
                    "近 30 天工单 " + workOrders + " 张（其中紧急 " + urgent + " 张），按「工单×2 + 紧急×3」计扣（上限 30）。",
                    "排查重复故障根因，对高发部件安排预防性维护。"));
        }
        if (r.timeoutScore().signum() > 0) {
            items.add(item("TIMEOUT", "维修超时", r.timeoutScore(),
                    "近 30 天超时完结工单 " + nvl(m.getOverdueWorkOrderCount()) + " 张，每张扣 5 分（上限 25）。",
                    "核对派单时限与人力安排，超时工单优先复排。"));
        }
        if (r.anomalyScore().signum() > 0) {
            items.add(item("ANOMALY", "巡检异常", r.anomalyScore(),
                    "近 30 天巡检异常 " + nvl(m.getAnomalyCount()) + " 次（严重 " + nvl(m.getHighAnomalyCount())
                            + " 次），每次扣 2 分（上限 20）。",
                    "复查异常项对应的运行参数，必要时缩短巡检周期。"));
        }
        if (r.overdueScore().signum() > 0) {
            items.add(item("OVERDUE", "巡检逾期", r.overdueScore(),
                    "近 30 天逾期巡检任务 " + nvl(m.getOverdueInspectCount()) + " 个，每个扣 5 分（上限 15）。",
                    "调整巡检计划或执行人力，避免任务过期。"));
        }
        if (r.openOrderScore().signum() > 0) {
            items.add(item("OPEN_ORDER", "未完成工单", r.openOrderScore(),
                    "当前在途工单 " + nvl(m.getOpenWorkOrderCount()) + " 张，每张扣 3 分（上限 15）。",
                    "推进在途工单闭环；长期滞留的工单重新评估派单。"));
        }
        if (r.idleScore().signum() > 0) {
            items.add(item("IDLE", "久未维修", r.idleScore(),
                    "最近一次完成维修距今 " + m.getIdleDays() + " 天（>180 天扣 5，>365 天扣 10）。",
                    "安排一次全面保养并复核设备状态。"));
        }
        if (r.statusScore().signum() > 0) {
            items.add(item("STATUS", "设备闲置", r.statusScore(),
                    "设备当前处于「闲置」状态，扣 5 分。",
                    "确认闲置原因：待修、待调拨或待报废，并更新处置计划。"));
        }
        if (r.bonusScore().signum() > 0) {
            items.add(item("BONUS", "巡检加分", r.bonusScore().negate(),
                    "近 30 天正常完成巡检 " + nvl(m.getNormalInspectCount()) + " 次，每次加 1 分（上限 10）。",
                    null));
        }
        return items;
    }

    private HealthVO.RiskItem item(String factor, String label, BigDecimal deduction,
                                   String detail, String suggestion) {
        HealthVO.RiskItem riskItem = new HealthVO.RiskItem();
        riskItem.setFactor(factor);
        riskItem.setLabel(label);
        riskItem.setDeduction(deduction);
        riskItem.setDetail(detail);
        riskItem.setSuggestion(suggestion);
        return riskItem;
    }

    private List<String> suggestionsOf(List<HealthVO.RiskItem> items) {
        Set<String> suggestions = new LinkedHashSet<>();
        for (HealthVO.RiskItem riskItem : items) {
            if (riskItem.getSuggestion() != null && !riskItem.getSuggestion().isBlank()) {
                suggestions.add(riskItem.getSuggestion());
            }
        }
        return new ArrayList<>(suggestions);
    }

    private String toJson(List<HealthVO.RiskItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("风险明细序列化失败", e);
            return null;
        }
    }

    private List<HealthVO.RiskItem> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, HealthVO.RiskItem.class));
        } catch (Exception e) {
            log.warn("风险明细反序列化失败", e);
            return new ArrayList<>();
        }
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private HealthVO toVO(EqEquipment equipment, EqHealthSnapshot snapshot, boolean withTrend) {
        HealthVO vo = new HealthVO();
        vo.setEquipmentId(equipment.getId());
        vo.setEquipmentName(equipment.getEquipmentName());
        vo.setAssetNo(equipment.getAssetNo());
        vo.setStatDate(snapshot.getStatDate());
        vo.setHealthScore(snapshot.getHealthScore());
        vo.setLevel(snapshot.getLevel());
        vo.setLevelLabel(HealthScoreCalculator.levelLabel(snapshot.getLevel()));
        vo.setFaultScore(snapshot.getFaultScore());
        vo.setTimeoutScore(snapshot.getTimeoutScore());
        vo.setAnomalyScore(snapshot.getAnomalyScore());
        vo.setOverdueScore(snapshot.getOverdueScore());
        vo.setBonusScore(snapshot.getBonusScore());
        vo.setWorkOrderCount(snapshot.getWorkOrderCount());
        vo.setAnomalyCount(snapshot.getAnomalyCount());
        vo.setOpenWoCount(snapshot.getOpenWoCount());
        vo.setIdleDays(snapshot.getIdleDays());
        if (withTrend) {
            for (EqHealthSnapshot item : snapshotMapper.selectRecent(equipment.getId(), TREND_LIMIT)) {
                HealthVO.TrendPoint point = new HealthVO.TrendPoint();
                point.setStatDate(item.getStatDate());
                point.setHealthScore(item.getHealthScore());
                point.setLevel(item.getLevel());
                vo.getTrend().add(point);
            }
        }
        return vo;
    }
}
