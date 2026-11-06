package com.equipcare.inspection.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.asset.entity.EqCategory;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.entity.EqLocation;
import com.equipcare.asset.enums.EquipmentStatus;
import com.equipcare.asset.mapper.EqCategoryMapper;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.mapper.EqLocationMapper;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import com.equipcare.inspection.entity.InspPlan;
import com.equipcare.inspection.entity.InspTask;
import com.equipcare.inspection.entity.InspTemplate;
import com.equipcare.inspection.entity.InspTemplateItem;
import com.equipcare.inspection.enums.ExecutorStrategy;
import com.equipcare.inspection.enums.InspTaskStatus;
import com.equipcare.inspection.enums.InspectionCycleType;
import com.equipcare.inspection.enums.PlanScopeType;
import com.equipcare.inspection.enums.TemplateStatus;
import com.equipcare.inspection.mapper.InspPlanEquipmentMapper;
import com.equipcare.inspection.mapper.InspPlanMapper;
import com.equipcare.inspection.mapper.InspTaskMapper;
import com.equipcare.inspection.mapper.InspTemplateItemMapper;
import com.equipcare.inspection.mapper.InspTemplateMapper;
import com.equipcare.inspection.service.InspectionGenerationService;
import com.equipcare.inspection.support.CycleConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 巡检任务自动生成实现。
 *
 * <p>不维护任何内存状态：每次生成都从数据库读计划、算窗口、逐条 INSERT IGNORE，
 * 因此服务重启、临时停机、双实例并行都不会产生重复任务，停机期间漏掉的日期也会在下一轮补齐。
 * 单条任务独立提交，某台设备生成失败不会影响同批其它设备，下一轮会自然补上。
 */
@Service
public class InspectionGenerationServiceImpl implements InspectionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(InspectionGenerationServiceImpl.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DEFAULT_SLA_MINUTES = 480;
    private static final int DEFAULT_AHEAD_HOURS = 24;
    /** 周期扫描上限：防止 endDate 配置成 2099 年时死循环 */
    private static final int MAX_SCAN_DAYS = 400;

    private final InspPlanMapper planMapper;
    private final InspPlanEquipmentMapper planEquipmentMapper;
    private final InspTemplateMapper templateMapper;
    private final InspTemplateItemMapper templateItemMapper;
    private final InspTaskMapper taskMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final EqCategoryMapper categoryMapper;
    private final EqLocationMapper locationMapper;
    private final com.equipcare.notification.service.MessageService messageService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final ObjectMapper json = new ObjectMapper();

    /** 重启后向前回补的天数：覆盖短时间停机，避免一次补出半年的任务 */
    private final int backfillDays;

    public InspectionGenerationServiceImpl(InspPlanMapper planMapper, InspPlanEquipmentMapper planEquipmentMapper,
                                           InspTemplateMapper templateMapper,
                                           InspTemplateItemMapper templateItemMapper,
                                           InspTaskMapper taskMapper, EqEquipmentMapper equipmentMapper,
                                           EqCategoryMapper categoryMapper, EqLocationMapper locationMapper,
                                           com.equipcare.notification.service.MessageService messageService,
                                           org.springframework.transaction.support.TransactionTemplate transactionTemplate,
                                           @Value("${equipcare.inspection.scheduler.backfill-days:7}") int backfillDays) {
        this.planMapper = planMapper;
        this.planEquipmentMapper = planEquipmentMapper;
        this.templateMapper = templateMapper;
        this.templateItemMapper = templateItemMapper;
        this.taskMapper = taskMapper;
        this.equipmentMapper = equipmentMapper;
        this.categoryMapper = categoryMapper;
        this.locationMapper = locationMapper;
        this.messageService = messageService;
        this.transactionTemplate = transactionTemplate;
        this.backfillDays = Math.max(0, backfillDays);
    }

    @Override
    public Summary generateDue(LocalDateTime now) {
        List<InspPlan> plans = planMapper.selectEnabled();
        Summary total = new Summary(0, 0, 0, 0, 0);
        for (InspPlan plan : plans) {
            try {
                Summary one = generateForPlan(plan, now, false);
                total = total.plus(one);
            } catch (Exception e) {
                // 单计划失败隔离：一个计划的模板或配置有问题，不能拖垮整批巡检任务
                log.error("巡检计划生成失败 planCode={} reason={}", plan.getPlanCode(), e.getMessage(), e);
                total = total.plus(new Summary(1, 0, 0, 0, 1));
            }
        }
        if (total.created() > 0 || total.errors() > 0) {
            log.info("巡检任务生成完成 plans={} created={} duplicate={} noExecutor={} errors={}",
                    total.plans(), total.created(), total.duplicate(), total.noExecutor(), total.errors());
        }
        return total;
    }

    @Override
    public Summary generateForPlan(Long planId, LocalDateTime now, boolean force) {
        InspPlan plan = planId == null ? null : planMapper.selectById(planId);
        if (plan == null) {
            throw BizException.notFound("巡检计划");
        }
        return generateForPlan(plan, now, force);
    }

    // ==================================================================
    // 核心生成逻辑
    // ==================================================================

    private Summary generateForPlan(InspPlan plan, LocalDateTime now, boolean force) {
        InspectionCycleType cycleType = InspectionCycleType.of(plan.getCycleType());
        if (cycleType == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "计划周期类型非法：" + plan.getCycleType());
        }
        InspTemplate template = templateMapper.selectById(plan.getTemplateId());
        if (template == null) {
            throw BizException.notFound("巡检模板");
        }
        if (!TemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
            throw BizException.forbidden("计划「" + plan.getPlanName() + "」绑定的模板未发布，暂不生成任务");
        }
        List<InspTemplateItem> items = templateItemMapper.selectByTemplate(template.getId());
        if (items.isEmpty()) {
            throw BizException.forbidden("计划「" + plan.getPlanName() + "」的模板没有检查项");
        }

        // 分类/位置型计划每次生成前同步设备快照，覆盖新装设备、剔除调拨走的设备
        syncScopeEquipment(plan);
        List<Long> equipmentIds = planEquipmentMapper.selectEquipmentIds(plan.getId());
        if (equipmentIds.isEmpty()) {
            throw BizException.forbidden("计划「" + plan.getPlanName() + "」范围内没有可巡检设备");
        }
        Map<Long, EqEquipment> equipments = equipmentMapper.selectBatchIds(equipmentIds).stream()
                .collect(Collectors.toMap(EqEquipment::getId, Function.identity(), (a, b) -> a));

        CycleConfig cycle = CycleConfig.parse(plan.getCycleConfig());
        int aheadHours = plan.getGenerateAheadHours() == null ? DEFAULT_AHEAD_HOURS : plan.getGenerateAheadHours();
        LocalDateTime windowEnd = now.plusHours(aheadHours);
        if (force) {
            // 「立即生成」：即便下一次执行还在提前窗口之外，也把它生成出来，便于现场验证配置
            LocalDateTime next = nextOccurrence(plan, now);
            if (next != null && next.isAfter(windowEnd)) {
                windowEnd = next;
            }
        }

        LocalDate from = now.toLocalDate().minusDays(backfillDays);
        if (plan.getStartDate() != null && plan.getStartDate().isAfter(from)) {
            from = plan.getStartDate();
        }
        LocalDate to = windowEnd.toLocalDate();
        if (plan.getEndDate() != null && plan.getEndDate().isBefore(to)) {
            to = plan.getEndDate();
        }
        if (to.isBefore(from)) {
            updateWatermark(plan, now);
            return new Summary(1, 0, 0, 0, 0);
        }

        int sla = plan.getSlaMinutes() == null ? DEFAULT_SLA_MINUTES : plan.getSlaMinutes();
        Map<LocalDate, Integer> codeSeq = new HashMap<>();
        int created = 0;
        int duplicate = 0;
        int noExecutor = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!cycle.matches(date, cycleType)) {
                continue;
            }
            LocalDateTime occurrence = cycle.occurrenceOn(date);
            if (occurrence.isAfter(windowEnd)) {
                continue;
            }
            for (int index = 0; index < equipmentIds.size(); index++) {
                Long equipmentId = equipmentIds.get(index);
                EqEquipment equipment = equipments.get(equipmentId);
                if (equipment == null) {
                    continue;
                }
                Long assigneeId = resolveAssignee(plan, equipment, index, date);
                if (assigneeId == null) {
                    noExecutor++;
                    continue;
                }
                if (taskMapper.countPlanEquipmentDate(plan.getId(), equipmentId, date) > 0) {
                    duplicate++;
                    continue;
                }
                InspTask task = buildTask(plan, equipmentId, assigneeId, date, occurrence, sla);
                if (insertWithRetry(task, plan.getId(), equipmentId, date, codeSeq)) {
                    created++;
                    // 第七阶段：巡检任务提醒（通知失败不影响生成主流程）
                    notifyTaskCreated(task, equipment);
                } else {
                    duplicate++;
                }
            }
        }

        updateWatermark(plan, now);
        if (created > 0) {
            log.info("计划 {} 生成任务 {} 条（跳过重复 {}，无执行人 {}）",
                    plan.getPlanCode(), created, duplicate, noExecutor);
        }
        return new Summary(1, created, duplicate, noExecutor, 0);
    }

    /** 第七阶段：巡检任务提醒。通知失败只记日志，不回滚任务生成。 */
    private void notifyTaskCreated(InspTask task, EqEquipment equipment) {
        try {
            String equipmentName = equipment == null ? String.valueOf(task.getEquipmentId())
                    : equipment.getEquipmentName();
            messageService.send(task.getAssigneeId(), "巡检任务提醒",
                    "设备「" + equipmentName + "」有新的巡检任务 " + task.getTaskCode()
                            + "（计划日期 " + task.getPlanDate() + "），请按时执行",
                    "TASK", "INSPECTION_TASK", task.getId());
        } catch (RuntimeException e) {
            log.warn("巡检任务提醒发送失败 taskId={}", task.getId(), e);
        }
    }

    /**
     * 插入任务并在任务编号冲突时重试。
     *
     * <p>INSERT IGNORE 会把「唯一键冲突」统一吞掉，只返回 0。这里用一次存在性查询区分：
     * 若 (plan,equipment,date) 已存在 —— 正常的重复防护，直接跳过；
     * 若不存在 —— 说明撞的是 task_code 唯一键（手工任务与定时任务同秒生成），换下一个编号重试。
     */
    private boolean insertWithRetry(InspTask task, Long planId, Long equipmentId, LocalDate date,
                                    Map<LocalDate, Integer> codeSeq) {
        for (int attempt = 0; attempt < 3; attempt++) {
            task.setTaskCode(nextTaskCode(date, codeSeq));
            if (taskMapper.insertIgnore(task) > 0) {
                return true;
            }
            if (taskMapper.countPlanEquipmentDate(planId, equipmentId, date) > 0) {
                return false;
            }
        }
        log.warn("任务编号连续冲突，放弃生成 planId={} equipmentId={} date={}", planId, equipmentId, date);
        return false;
    }

    private InspTask buildTask(InspPlan plan, Long equipmentId, Long assigneeId, LocalDate date,
                               LocalDateTime occurrence, int slaMinutes) {
        InspTask task = new InspTask();
        task.setPlanId(plan.getId());
        task.setEquipmentId(equipmentId);
        task.setTemplateId(plan.getTemplateId());
        task.setPlanDate(date);
        task.setAssigneeId(assigneeId);
        task.setStatus(InspTaskStatus.PENDING.getCode());
        task.setSource("AUTO");
        task.setSlaDueTime(occurrence.plusMinutes(slaMinutes));
        String remark = "计划生成：" + plan.getPlanName();
        task.setRemark(remark.length() > 300 ? remark.substring(0, 300) : remark);
        task.setCreateBy(plan.getCreatorId());
        return task;
    }

    private String nextTaskCode(LocalDate date, Map<LocalDate, Integer> cache) {
        String prefix = "IT" + date.format(DAY);
        Integer next = cache.get(date);
        if (next == null) {
            String max = taskMapper.selectMaxTaskCodeByPrefix(prefix);
            int value = 1;
            if (max != null && max.length() > prefix.length()) {
                try {
                    value = Integer.parseInt(max.substring(prefix.length())) + 1;
                } catch (NumberFormatException ignored) {
                    value = 1;
                }
            }
            next = value;
        }
        cache.put(date, next + 1);
        return prefix + String.format("%04d", next);
    }

    private void updateWatermark(InspPlan plan, LocalDateTime now) {
        LocalDateTime next = nextOccurrence(plan, now);
        // 用 UpdateWrapper 而不是 updateById：next 可能为 null（计划已过期），需要能写回空值
        planMapper.update(null, Wrappers.<InspPlan>lambdaUpdate()
                .set(InspPlan::getLastGenerateTime, now)
                .set(InspPlan::getNextGenerateTime, next)
                .eq(InspPlan::getId, plan.getId()));
        plan.setLastGenerateTime(now);
        plan.setNextGenerateTime(next);
    }

    @Override
    public LocalDateTime nextOccurrence(InspPlan plan, LocalDateTime now) {
        InspectionCycleType cycleType = InspectionCycleType.of(plan.getCycleType());
        if (cycleType == null) {
            return null;
        }
        CycleConfig cycle = CycleConfig.parse(plan.getCycleConfig());
        LocalDate cursor = now.toLocalDate();
        if (plan.getStartDate() != null && plan.getStartDate().isAfter(cursor)) {
            cursor = plan.getStartDate();
        }
        for (int i = 0; i < MAX_SCAN_DAYS; i++) {
            LocalDate date = cursor.plusDays(i);
            if (plan.getEndDate() != null && date.isAfter(plan.getEndDate())) {
                return null;
            }
            if (!cycle.matches(date, cycleType)) {
                continue;
            }
            LocalDateTime occurrence = cycle.occurrenceOn(date);
            if (occurrence.isAfter(now)) {
                return occurrence;
            }
        }
        return null;
    }

    // ==================================================================
    // 作用域展开
    // ==================================================================

    @Override
    public List<Long> resolveScopeEquipmentIds(String scopeType, String scopeConfig) {
        PlanScopeType type = PlanScopeType.of(scopeType);
        if (type == null) {
            return List.of();
        }
        switch (type) {
            case EQUIPMENT: {
                List<Long> ids = parseIds(scopeConfig, "equipmentIds");
                if (ids.isEmpty()) {
                    return List.of();
                }
                return equipmentMapper.selectBatchIds(ids).stream()
                        .filter(e -> !EquipmentStatus.SCRAPPED.getCode().equals(e.getStatus()))
                        .map(EqEquipment::getId)
                        .sorted()
                        .toList();
            }
            case CATEGORY: {
                Set<Long> categoryIds = expandCategories(parseIds(scopeConfig, "categoryIds"));
                if (categoryIds.isEmpty()) {
                    return List.of();
                }
                return equipmentMapper.selectList(Wrappers.<EqEquipment>lambdaQuery()
                                .in(EqEquipment::getCategoryId, categoryIds)
                                .ne(EqEquipment::getStatus, EquipmentStatus.SCRAPPED.getCode())
                                .orderByAsc(EqEquipment::getId))
                        .stream().map(EqEquipment::getId).toList();
            }
            case LOCATION: {
                Set<Long> locationIds = expandLocations(parseIds(scopeConfig, "locationIds"));
                if (locationIds.isEmpty()) {
                    return List.of();
                }
                return equipmentMapper.selectList(Wrappers.<EqEquipment>lambdaQuery()
                                .in(EqEquipment::getLocationId, locationIds)
                                .ne(EqEquipment::getStatus, EquipmentStatus.SCRAPPED.getCode())
                                .orderByAsc(EqEquipment::getId))
                        .stream().map(EqEquipment::getId).toList();
            }
            default:
                return List.of();
        }
    }

    @Override
    public int syncScopeEquipment(InspPlan plan) {
        List<Long> resolved = resolveScopeEquipmentIds(plan.getScopeType(), plan.getScopeConfig());
        if (resolved.isEmpty()) {
            return 0;
        }
        List<Long> existing = planEquipmentMapper.selectEquipmentIds(plan.getId());
        Set<Long> existingSet = new HashSet<>(existing);
        // 事务边界收敛：插新 + 删旧必须同事务，防止中途失败留下「旧设备已删、新设备未插全」的半同步快照
        //（阶段十二整改 Q-04；TransactionTemplate 规避同类自调用导致 @Transactional 失效）
        Integer added = transactionTemplate.execute(status -> {
            int addedCount = 0;
            int seq = existing.size();
            for (Long equipmentId : resolved) {
                if (existingSet.add(equipmentId)) {
                    seq++;
                    addedCount += planEquipmentMapper.insertIgnore(plan.getId(), equipmentId, seq);
                }
            }
            // 移出作用域的设备从快照中剔除；历史任务不受影响
            if (existingSet.size() > addedCount) {
                planEquipmentMapper.deleteNotIn(plan.getId(), new ArrayList<>(existingSet));
            }
            return addedCount;
        });
        return added == null ? 0 : added;
    }

    private Set<Long> expandCategories(List<Long> seeds) {
        if (seeds.isEmpty()) {
            return Set.of();
        }
        Map<Long, List<Long>> children = new HashMap<>();
        for (EqCategory category : categoryMapper.selectList(null)) {
            children.computeIfAbsent(category.getParentId(), k -> new ArrayList<>()).add(category.getId());
        }
        return bfs(seeds, children);
    }

    private Set<Long> expandLocations(List<Long> seeds) {
        if (seeds.isEmpty()) {
            return Set.of();
        }
        Map<Long, List<Long>> children = new HashMap<>();
        for (EqLocation location : locationMapper.selectList(null)) {
            children.computeIfAbsent(location.getParentId(), k -> new ArrayList<>()).add(location.getId());
        }
        return bfs(seeds, children);
    }

    private Set<Long> bfs(List<Long> seeds, Map<Long, List<Long>> children) {
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (id == null || !visited.add(id)) {
                continue;
            }
            queue.addAll(children.getOrDefault(id, List.of()));
        }
        return visited;
    }

    // ==================================================================
    // 执行人分配
    // ==================================================================

    private Long resolveAssignee(InspPlan plan, EqEquipment equipment, int index, LocalDate date) {
        ExecutorStrategy strategy = ExecutorStrategy.of(plan.getExecutorStrategy());
        List<Long> executors = parseIdArray(plan.getExecutorIds());
        if (strategy == ExecutorStrategy.DEPT_OWNER) {
            if (equipment.getResponsibleUserId() != null) {
                return equipment.getResponsibleUserId();
            }
            return executors.isEmpty() ? plan.getCreatorId() : executors.get(0);
        }
        if (executors.isEmpty()) {
            // 没配执行人时退回设备负责人，再退回计划创建人；都没有就只能跳过并计数
            if (equipment.getResponsibleUserId() != null) {
                return equipment.getResponsibleUserId();
            }
            return plan.getCreatorId();
        }
        int idx = strategy == ExecutorStrategy.ROUND_ROBIN
                ? Math.floorMod(date.getDayOfYear() + index, executors.size())
                : Math.floorMod(index, executors.size());
        return executors.get(idx);
    }

    // ==================================================================
    // JSON 工具
    // ==================================================================

    private List<Long> parseIds(String jsonText, String key) {
        if (!StringUtils.hasText(jsonText)) {
            return List.of();
        }
        try {
            JsonNode node = json.readTree(jsonText);
            JsonNode array = node.path(key);
            if (array.isArray()) {
                return collectIds(array);
            }
        } catch (Exception e) {
            log.warn("作用域配置解析失败：{}", jsonText);
        }
        return List.of();
    }

    private List<Long> parseIdArray(String jsonText) {
        if (!StringUtils.hasText(jsonText)) {
            return List.of();
        }
        try {
            JsonNode node = json.readTree(jsonText);
            if (node.isArray()) {
                return collectIds(node);
            }
            JsonNode inner = node.path("ids");
            if (inner.isArray()) {
                return collectIds(inner);
            }
        } catch (Exception e) {
            log.warn("执行人配置解析失败：{}", jsonText);
        }
        return List.of();
    }

    private List<Long> collectIds(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.canConvertToLong()) {
                ids.add(item.asLong());
            }
        }
        return ids;
    }
}
