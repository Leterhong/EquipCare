package com.equipcare.workorder.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.query.PageQuery;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.event.DomainEventPublisher;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.workorder.dto.SlaPolicyUpdateDTO;
import com.equipcare.workorder.entity.SlaAlertRecord;
import com.equipcare.workorder.entity.SlaPolicy;
import com.equipcare.workorder.entity.WoWorkOrder;
import com.equipcare.workorder.event.SlaAlertTriggeredEvent;
import com.equipcare.workorder.enums.SlaStatus;
import com.equipcare.workorder.enums.WorkOrderLevel;
import com.equipcare.workorder.enums.WorkOrderStatus;
import com.equipcare.workorder.mapper.SlaAlertRecordMapper;
import com.equipcare.workorder.mapper.SlaPolicyMapper;
import com.equipcare.workorder.mapper.WoWorkOrderMapper;
import com.equipcare.workorder.service.SlaService;
import com.equipcare.workorder.vo.SlaActiveOrderVO;
import com.equipcare.workorder.vo.SlaAlertVO;
import com.equipcare.workorder.vo.SlaPolicyVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SLA 服务实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>时限以政策表为准</b>：表缺行或停用时兜底 {@link WorkOrderLevel} 枚举值，
 *       保证测试库（无种子数据）与全新环境也能运转；</li>
 *   <li><b>sla_status 由本服务全量重算</b>：四个进行中状态各自有「阶段起点 → 阶段截止」窗口，
 *       按政策 warn_percent 标 WARN、过期标 OVERDUE、换阶段后归位 NORMAL——
 *       只允许「NORMAL→WARN→OVERDUE」前进与「换阶段归位」，不回退同阶段状态；</li>
 *   <li><b>通知去重落在数据库</b>：sla_alert_record 唯一键 (工单, 阶段, 日期)，
 *       upsert 返回 1 才发通知，返回 2 表示今天已提醒过。</li>
 * </ul>
 */
@Service
public class SlaServiceImpl implements SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaServiceImpl.class);

    private static final String MSG_TYPE_SLA = "SLA";
    private static final String BIZ_TYPE_SLA_ALERT = "SLA_ALERT";
    private static final String FALLBACK_ROLE = "ADMIN";

    /** 四个进行中状态按处理顺序排列 */
    private static final List<String> ACTIVE_STATUSES = List.of(
            WorkOrderStatus.PENDING_ASSIGN.getCode(),
            WorkOrderStatus.PENDING_ACCEPT.getCode(),
            WorkOrderStatus.IN_PROGRESS.getCode(),
            WorkOrderStatus.PENDING_REVIEW.getCode());

    private final SlaPolicyMapper policyMapper;
    private final SlaAlertRecordMapper alertMapper;
    private final WoWorkOrderMapper orderMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final SysUserMapper userMapper;
    private final DomainEventPublisher domainEventPublisher;

    public SlaServiceImpl(SlaPolicyMapper policyMapper, SlaAlertRecordMapper alertMapper,
                          WoWorkOrderMapper orderMapper, EqEquipmentMapper equipmentMapper,
                          SysUserMapper userMapper, DomainEventPublisher domainEventPublisher) {
        this.policyMapper = policyMapper;
        this.alertMapper = alertMapper;
        this.orderMapper = orderMapper;
        this.equipmentMapper = equipmentMapper;
        this.userMapper = userMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ==================================================================
    // 政策配置
    // ==================================================================

    @Override
    public List<SlaPolicyVO> policies() {
        List<SlaPolicy> policies = policyMapper.selectList(
                Wrappers.<SlaPolicy>lambdaQuery().orderByAsc(SlaPolicy::getLevel));
        List<SlaPolicyVO> list = new ArrayList<>(policies.size());
        for (SlaPolicy policy : policies) {
            WorkOrderLevel level = WorkOrderLevel.of(policy.getLevel());
            SlaPolicyVO vo = new SlaPolicyVO();
            vo.setId(policy.getId());
            vo.setPolicyName(policy.getPolicyName());
            vo.setLevel(policy.getLevel());
            vo.setLevelLabel(level == null ? policy.getLevel() : level.getLabel());
            vo.setResponseMinutes(policy.getResponseMinutes());
            vo.setResolveMinutes(policy.getResolveMinutes());
            vo.setVerifyMinutes(policy.getVerifyMinutes());
            vo.setWarnPercent(policy.getWarnPercent());
            vo.setEscalateRoleCode(policy.getEscalateRoleCode());
            vo.setEnabled(policy.getEnabled() != null && policy.getEnabled() == 1);
            vo.setRemark(policy.getRemark());
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePolicy(String level, SlaPolicyUpdateDTO dto) {
        SlaPolicy policy = policyMapper.selectOne(Wrappers.<SlaPolicy>lambdaQuery()
                .eq(SlaPolicy::getLevel, level).last("LIMIT 1"));
        if (policy == null) {
            throw BizException.notFound("SLA 政策（等级 " + level + "）");
        }
        SlaPolicy patch = new SlaPolicy();
        patch.setId(policy.getId());
        if (dto.getPolicyName() != null) {
            patch.setPolicyName(dto.getPolicyName());
        }
        patch.setResponseMinutes(dto.getResponseMinutes());
        patch.setResolveMinutes(dto.getResolveMinutes());
        patch.setVerifyMinutes(dto.getVerifyMinutes());
        patch.setWarnPercent(dto.getWarnPercent());
        patch.setEscalateRoleCode(dto.getEscalateRoleCode());
        patch.setEnabled(dto.getEnabled() == null ? null : (dto.getEnabled() ? 1 : 0));
        patch.setRemark(dto.getRemark());
        patch.setUpdateBy(SecurityUtils.getCurrentUserIdOrNull());
        policyMapper.updateById(patch);
        log.info("SLA 政策更新 level={} response={} resolve={} verify={} warnPercent={}",
                level, dto.getResponseMinutes(), dto.getResolveMinutes(),
                dto.getVerifyMinutes(), dto.getWarnPercent());
    }

    // ==================================================================
    // 扫描与预警
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scan() {
        List<WoWorkOrder> orders = orderMapper.selectList(Wrappers.<WoWorkOrder>lambdaQuery()
                .in(WoWorkOrder::getStatus, ACTIVE_STATUSES));
        if (orders.isEmpty()) {
            return 0;
        }
        Map<String, SlaPolicy> policyMap = policyMap();
        Map<Long, String> equipmentNames = loadEquipmentNames(orders);
        LocalDateTime now = LocalDateTime.now();
        int notified = 0;
        for (WoWorkOrder order : orders) {
            StageWindow window = windowOf(order);
            if (window == null) {
                continue;
            }
            SlaPolicy policy = policyMap.get(order.getLevel());
            int warnPercent = policy == null || policy.getWarnPercent() == null
                    ? 80 : policy.getWarnPercent();

            String target = evaluate(window, now, warnPercent);
            applySlaStatus(order, target);

            if (SlaStatus.OVERDUE.getCode().equals(target)) {
                // 每轮扫描都登记：唯一键 (工单, 阶段, 日期) 保证一天只有一条记录，
                // upsert 返回 1（今天首次）才发通知，返回 2 仅累加 alert_count 不再通知
                if (registerAlert(order, window, now, policy)) {
                    notified++;
                }
            }
        }
        if (notified > 0) {
            log.info("SLA 扫描：本轮新超时工单 {} 张", notified);
        }
        return notified;
    }

    @Override
    public List<SlaActiveOrderVO> activeOrders() {
        List<WoWorkOrder> orders = orderMapper.selectList(Wrappers.<WoWorkOrder>lambdaQuery()
                .in(WoWorkOrder::getStatus, ACTIVE_STATUSES)
                .orderByAsc(WoWorkOrder::getResponseDueTime));
        Map<String, SlaPolicy> policyMap = policyMap();
        Map<Long, String> equipmentNames = loadEquipmentNames(orders);
        LocalDateTime now = LocalDateTime.now();
        List<SlaActiveOrderVO> list = new ArrayList<>(orders.size());
        for (WoWorkOrder order : orders) {
            StageWindow window = windowOf(order);
            if (window == null || window.due == null || window.start == null) {
                continue;
            }
            long total = Math.max(1, Duration.between(window.start, window.due).toMinutes());
            long elapsed = Math.max(0, Duration.between(window.start, now).toMinutes());
            SlaActiveOrderVO vo = new SlaActiveOrderVO();
            vo.setWorkOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTitle(order.getTitle());
            vo.setEquipmentName(equipmentNames.get(order.getEquipmentId()));
            vo.setLevel(order.getLevel());
            WorkOrderLevel level = WorkOrderLevel.of(order.getLevel());
            vo.setLevelLabel(level == null ? order.getLevel() : level.getLabel());
            vo.setStage(window.stage);
            vo.setStageLabel(stageLabel(window.stage));
            vo.setSlaStatus(order.getSlaStatus());
            vo.setDueTime(window.due);
            vo.setElapsedMinutes(elapsed);
            vo.setTotalMinutes(total);
            vo.setPercent((int) Math.min(999, elapsed * 100 / total));
            list.add(vo);
        }
        return list;
    }

    @Override
    public PageResult<SlaAlertVO> pageAlerts(PageQuery query, String stage, String level) {
        var page = alertMapper.selectAlertPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                query.getPageNum(), query.getPageSize()), stage, level);
        List<SlaAlertVO> records = new ArrayList<>(page.getRecords().size());
        for (SlaAlertVO vo : page.getRecords()) {
            vo.setStageLabel(stageLabel(vo.getStage()));
            WorkOrderLevel levelEnum = WorkOrderLevel.of(vo.getOrderLevel());
            vo.setLevelLabel(levelEnum == null ? vo.getOrderLevel() : levelEnum.getLabel());
            records.add(vo);
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /** 阶段窗口：该阶段的起点与截止时间，由工单上已落的时间列推导，不做任何猜测 */
    private record StageWindow(String stage, LocalDateTime start, LocalDateTime due) {
    }

    private StageWindow windowOf(WoWorkOrder order) {
        return switch (order.getStatus() == null ? "" : order.getStatus()) {
            case "PENDING_ASSIGN" -> new StageWindow(WorkOrderStatus.PENDING_ASSIGN.getCode(),
                    order.getReportTime(), order.getResponseDueTime());
            case "PENDING_ACCEPT" -> new StageWindow(WorkOrderStatus.PENDING_ACCEPT.getCode(),
                    firstNonNull(order.getDispatchTime(), order.getReportTime()),
                    order.getResponseDueTime());
            case "IN_PROGRESS" -> new StageWindow(WorkOrderStatus.IN_PROGRESS.getCode(),
                    firstNonNull(order.getStartRepairTime(), order.getAcceptTime(),
                            order.getDispatchTime(), order.getReportTime()),
                    order.getResolveDueTime());
            case "PENDING_REVIEW" -> new StageWindow(WorkOrderStatus.PENDING_REVIEW.getCode(),
                    firstNonNull(order.getSubmitVerifyTime(), order.getResolveDueTime()),
                    order.getVerifyDueTime());
            default -> null;
        };
    }

    /** 目标 SLA 状态：超时 OVERDUE、达到预警线 WARN、其余 NORMAL */
    private String evaluate(StageWindow window, LocalDateTime now, int warnPercent) {
        if (window.start == null || window.due == null) {
            return SlaStatus.NORMAL.getCode();
        }
        if (now.isAfter(window.due)) {
            return SlaStatus.OVERDUE.getCode();
        }
        long total = Duration.between(window.start, window.due).toMinutes();
        long elapsed = Duration.between(window.start, now).toMinutes();
        if (total > 0 && elapsed * 100 >= total * warnPercent) {
            return SlaStatus.WARN.getCode();
        }
        return SlaStatus.NORMAL.getCode();
    }

    /** 只允许前进（NORMAL→WARN→OVERDUE）与换阶段归位，绝不回退同阶段状态 */
    private void applySlaStatus(WoWorkOrder order, String target) {
        String current = order.getSlaStatus();
        if (target.equals(current)) {
            return;
        }
        if (SlaStatus.NORMAL.getCode().equals(target)
                && SlaStatus.NORMAL.getCode().equals(current)) {
            return;
        }
        orderMapper.update(null, Wrappers.<WoWorkOrder>lambdaUpdate()
                .eq(WoWorkOrder::getId, order.getId())
                .eq(WoWorkOrder::getSlaStatus, current)
                .set(WoWorkOrder::getSlaStatus, target));
        order.setSlaStatus(target);
    }

    /**
     * 登记当日预警：upsert 返回 1 表示今天首次，向升级角色与 ADMIN 发通知；
     * 返回 2 表示今天已提醒过，跳过。
     */
    private boolean registerAlert(WoWorkOrder order, StageWindow window, LocalDateTime now,
                                  SlaPolicy policy) {
        SlaAlertRecord record = new SlaAlertRecord();
        record.setWorkOrderId(order.getId());
        record.setStage(window.stage);
        record.setAlertDate(LocalDate.now());
        record.setOrderLevel(order.getLevel());
        record.setOverdueMinutes((int) Duration.between(window.due, now).toMinutes());
        int affected = alertMapper.upsertToday(record);
        if (affected != 1) {
            return false;
        }
        Set<Long> receivers = new LinkedHashSet<>();
        if (policy != null && policy.getEscalateRoleCode() != null
                && !policy.getEscalateRoleCode().isBlank()) {
            addRoleUsers(receivers, policy.getEscalateRoleCode());
        }
        addRoleUsers(receivers, FALLBACK_ROLE);
        if (order.getAssigneeId() != null) {
            receivers.add(order.getAssigneeId());
        }
        // 领域事件：SLA 预警（消息投递与 SSE 推送由 notification 监听器异步完成；
        // 当天首次判定已由 sla_alert_record 唯一键保证，事件只负责投递）
        domainEventPublisher.publish(new SlaAlertTriggeredEvent(order.getId(), order.getOrderNo(),
                window.stage, record.getOverdueMinutes(), List.copyOf(receivers),
                "工单超时提醒",
                "工单 " + order.getOrderNo() + "（" + order.getTitle() + "）在「"
                        + stageLabel(window.stage) + "」阶段已超时 "
                        + record.getOverdueMinutes() + " 分钟，请尽快处理。",
                MSG_TYPE_SLA, BIZ_TYPE_SLA_ALERT, order.getId()));
        return true;
    }

    private void addRoleUsers(Set<Long> receivers, String roleCode) {
        List<Long> ids = userMapper.selectUserIdsByRoleCode(roleCode);
        if (ids != null) {
            receivers.addAll(ids);
        }
    }

    /** 政策表快照：level → policy（只取启用的行） */
    private Map<String, SlaPolicy> policyMap() {
        Map<String, SlaPolicy> map = new HashMap<>();
        for (SlaPolicy policy : policyMapper.selectList(Wrappers.<SlaPolicy>lambdaQuery()
                .eq(SlaPolicy::getEnabled, 1))) {
            map.put(policy.getLevel(), policy);
        }
        return map;
    }

    private Map<Long, String> loadEquipmentNames(List<WoWorkOrder> orders) {
        Set<Long> ids = new HashSet<>();
        for (WoWorkOrder order : orders) {
            if (order.getEquipmentId() != null) {
                ids.add(order.getEquipmentId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (EqEquipment equipment : equipmentMapper.selectBatchIds(ids)) {
            names.put(equipment.getId(), equipment.getEquipmentName());
        }
        return names;
    }

    private static String stageLabel(String stage) {
        return switch (stage == null ? "" : stage) {
            case "PENDING_ASSIGN" -> "待派单";
            case "PENDING_ACCEPT" -> "待接单";
            case "IN_PROGRESS" -> "维修中";
            case "PENDING_REVIEW" -> "待验收";
            default -> stage;
        };
    }

    private static LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
