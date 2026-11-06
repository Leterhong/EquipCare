package com.equipcare.inspection.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.common.enums.EnumCode;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.inspection.dto.PlanQuery;
import com.equipcare.inspection.dto.PlanSaveDTO;
import com.equipcare.inspection.entity.InspPlan;
import com.equipcare.inspection.entity.InspPlanEquipment;
import com.equipcare.inspection.entity.InspTemplate;
import com.equipcare.inspection.enums.ExecutorStrategy;
import com.equipcare.inspection.enums.InspectionCycleType;
import com.equipcare.inspection.enums.PlanScopeType;
import com.equipcare.inspection.enums.PlanStatus;
import com.equipcare.inspection.enums.TemplateStatus;
import com.equipcare.inspection.mapper.InspPlanEquipmentMapper;
import com.equipcare.inspection.mapper.InspPlanMapper;
import com.equipcare.inspection.mapper.InspTaskMapper;
import com.equipcare.inspection.mapper.InspTemplateItemMapper;
import com.equipcare.inspection.mapper.InspTemplateMapper;
import com.equipcare.inspection.service.InspectionGenerationService;
import com.equipcare.inspection.service.InspectionPlanService;
import com.equipcare.inspection.support.CycleConfig;
import com.equipcare.inspection.support.InspectionClock;
import com.equipcare.inspection.vo.PlanDetailVO;
import com.equipcare.inspection.vo.PlanVO;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.asset.entity.EqCategory;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.entity.EqLocation;
import com.equipcare.asset.mapper.EqCategoryMapper;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.mapper.EqLocationMapper;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 巡检计划管理实现。
 *
 * <p>计划保存时把作用域展开成设备快照写入 {@code insp_plan_equipment}，
 * 后续生成任务只读快照，不再解析分类树——生成链路因此变得简单且可重复。
 * 分类/位置型计划在每次生成前会重新同步快照，新装设备自动纳入、调拨走的设备自动剔除。
 */
@Service
public class InspectionPlanServiceImpl implements InspectionPlanService {

    private static final Logger log = LoggerFactory.getLogger(InspectionPlanServiceImpl.class);

    private static final String CODE_PREFIX = "PLAN";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int MAX_LABEL_ITEMS = 3;

    private final InspPlanMapper planMapper;
    private final InspPlanEquipmentMapper planEquipmentMapper;
    private final InspTemplateMapper templateMapper;
    private final InspTemplateItemMapper templateItemMapper;
    private final InspTaskMapper taskMapper;
    private final InspectionGenerationService generationService;
    private final InspectionClock clock;
    private final SysUserMapper userMapper;
    private final EqCategoryMapper categoryMapper;
    private final EqLocationMapper locationMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final ObjectMapper json = new ObjectMapper();

    public InspectionPlanServiceImpl(InspPlanMapper planMapper, InspPlanEquipmentMapper planEquipmentMapper,
                                     InspTemplateMapper templateMapper, InspTemplateItemMapper templateItemMapper,
                                     InspTaskMapper taskMapper, InspectionGenerationService generationService,
                                     InspectionClock clock, SysUserMapper userMapper,
                                     EqCategoryMapper categoryMapper, EqLocationMapper locationMapper,
                                     EqEquipmentMapper equipmentMapper) {
        this.planMapper = planMapper;
        this.planEquipmentMapper = planEquipmentMapper;
        this.templateMapper = templateMapper;
        this.templateItemMapper = templateItemMapper;
        this.taskMapper = taskMapper;
        this.generationService = generationService;
        this.clock = clock;
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.locationMapper = locationMapper;
        this.equipmentMapper = equipmentMapper;
    }

    @Override
    public PageResult<PlanVO> page(PlanQuery query) {
        IPage<PlanVO> page = planMapper.selectPlanPage(new Page<>(query.getPageNum(), query.getPageSize()), query);
        LabelContext ctx = buildContext(page.getRecords());
        page.getRecords().forEach(vo -> fillLabels(vo, ctx));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PlanDetailVO detail(Long id) {
        PlanVO base = planMapper.selectPlanDetail(id);
        if (base == null) {
            throw BizException.notFound("巡检计划");
        }
        PlanDetailVO vo = copy(base);
        LabelContext ctx = buildContext(List.of(base));
        fillLabels(vo, ctx);
        vo.setEquipment(planEquipmentMapper.selectByPlan(id));
        vo.setRecentTasks(taskMapper.selectRecentByPlan(id, 10));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PlanSaveDTO dto) {
        validate(dto);
        InspTemplate template = requirePublishedTemplate(dto.getTemplateId());

        String code = StringUtils.hasText(dto.getPlanCode()) ? dto.getPlanCode().trim() : nextCode();
        if (planMapper.selectCount(Wrappers.<InspPlan>lambdaQuery().eq(InspPlan::getPlanCode, code)) > 0) {
            throw BizException.duplicate("计划编码 " + code);
        }

        String status = EnumCode.contains(PlanStatus.class, dto.getStatus())
                ? dto.getStatus() : PlanStatus.DISABLED.getCode();

        InspPlan plan = new InspPlan();
        plan.setPlanCode(code);
        plan.setPlanName(dto.getPlanName().trim());
        plan.setTemplateId(template.getId());
        plan.setScopeType(dto.getScopeType());
        plan.setScopeConfig(scopeJson(dto.getScopeType(), dto.getScopeIds()));
        plan.setExecutorStrategy(ExecutorStrategy.of(dto.getExecutorStrategy()).getCode());
        plan.setExecutorIds(executorJson(dto.getExecutorIds()));
        plan.setCycleType(dto.getCycleType());
        plan.setCycleConfig(CycleConfig.toJson(dto.getExecuteTime(), dto.getWeekDays(), dto.getMonthDays()));
        plan.setStartDate(dto.getStartDate());
        plan.setEndDate(dto.getEndDate());
        plan.setSlaMinutes(dto.getSlaMinutes());
        plan.setGenerateAheadHours(dto.getGenerateAheadHours());
        plan.setCreatorId(SecurityUtils.getCurrentUserId());
        plan.setStatus(status);
        planMapper.insert(plan);

        int added = generationService.syncScopeEquipment(plan);
        if (planEquipmentMapper.countByPlan(plan.getId()) == 0) {
            // 抛出后事务回滚，不会留下一个永远生成不出任务的空计划
            throw new BizException(ResultCode.BAD_REQUEST, "所选范围内没有可巡检设备（已报废设备不参与巡检）");
        }
        LocalDateTime next = generationService.nextOccurrence(plan, clock.now());
        planMapper.update(null, Wrappers.<InspPlan>lambdaUpdate()
                .set(InspPlan::getNextGenerateTime, next)
                .eq(InspPlan::getId, plan.getId()));

        log.info("创建巡检计划 code={} 覆盖设备 {} 台（新增 {}）周期={} 状态={}",
                code, planEquipmentMapper.countByPlan(plan.getId()), added, plan.getCycleType(), status);
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PlanSaveDTO dto) {
        InspPlan plan = requirePlan(id);
        validate(dto);
        InspTemplate template = requirePublishedTemplate(dto.getTemplateId());

        if (StringUtils.hasText(dto.getPlanCode()) && !dto.getPlanCode().trim().equals(plan.getPlanCode())) {
            String code = dto.getPlanCode().trim();
            if (planMapper.selectCount(Wrappers.<InspPlan>lambdaQuery()
                    .eq(InspPlan::getPlanCode, code).ne(InspPlan::getId, id)) > 0) {
                throw BizException.duplicate("计划编码 " + code);
            }
            plan.setPlanCode(code);
        }
        plan.setPlanName(dto.getPlanName().trim());
        plan.setTemplateId(template.getId());
        plan.setScopeType(dto.getScopeType());
        plan.setScopeConfig(scopeJson(dto.getScopeType(), dto.getScopeIds()));
        plan.setExecutorStrategy(ExecutorStrategy.of(dto.getExecutorStrategy()).getCode());
        plan.setExecutorIds(executorJson(dto.getExecutorIds()));
        plan.setCycleType(dto.getCycleType());
        plan.setCycleConfig(CycleConfig.toJson(dto.getExecuteTime(), dto.getWeekDays(), dto.getMonthDays()));
        plan.setStartDate(dto.getStartDate());
        plan.setEndDate(dto.getEndDate());
        plan.setSlaMinutes(dto.getSlaMinutes());
        plan.setGenerateAheadHours(dto.getGenerateAheadHours());
        if (EnumCode.contains(PlanStatus.class, dto.getStatus())) {
            plan.setStatus(dto.getStatus());
        }
        // 用显式 set 而不是 updateById：结束日期可能被清空，updateById 会跳过 null 字段
        planMapper.update(null, Wrappers.<InspPlan>lambdaUpdate()
                .set(InspPlan::getPlanCode, plan.getPlanCode())
                .set(InspPlan::getPlanName, plan.getPlanName())
                .set(InspPlan::getTemplateId, plan.getTemplateId())
                .set(InspPlan::getScopeType, plan.getScopeType())
                .set(InspPlan::getScopeConfig, plan.getScopeConfig())
                .set(InspPlan::getExecutorStrategy, plan.getExecutorStrategy())
                .set(InspPlan::getExecutorIds, plan.getExecutorIds())
                .set(InspPlan::getCycleType, plan.getCycleType())
                .set(InspPlan::getCycleConfig, plan.getCycleConfig())
                .set(InspPlan::getStartDate, plan.getStartDate())
                .set(InspPlan::getEndDate, plan.getEndDate())
                .set(InspPlan::getSlaMinutes, plan.getSlaMinutes())
                .set(InspPlan::getGenerateAheadHours, plan.getGenerateAheadHours())
                .set(InspPlan::getStatus, plan.getStatus())
                .eq(InspPlan::getId, id));

        // 作用域可能变化：整组重建快照，避免残留已移出的设备
        planEquipmentMapper.deleteByPlan(id);
        generationService.syncScopeEquipment(plan);
        if (planEquipmentMapper.countByPlan(id) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "所选范围内没有可巡检设备（已报废设备不参与巡检）");
        }
        LocalDateTime next = generationService.nextOccurrence(plan, clock.now());
        planMapper.update(null, Wrappers.<InspPlan>lambdaUpdate()
                .set(InspPlan::getNextGenerateTime, next)
                .eq(InspPlan::getId, id));
        log.info("更新巡检计划 id={} 覆盖设备 {} 台", id, planEquipmentMapper.countByPlan(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InspPlan plan = requirePlan(id);
        if (PlanStatus.ENABLED.getCode().equals(plan.getStatus())) {
            throw BizException.forbidden("请先暂停计划再删除");
        }
        planEquipmentMapper.deleteByPlan(id);
        planMapper.deleteById(id);
        log.info("删除巡检计划 id={} code={}", id, plan.getPlanCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        if (!EnumCode.contains(PlanStatus.class, status)) {
            throw new BizException(ResultCode.BAD_REQUEST, "计划状态取值非法：" + status);
        }
        InspPlan plan = requirePlan(id);
        if (PlanStatus.ENABLED.getCode().equals(status)) {
            requirePublishedTemplate(plan.getTemplateId());
            if (templateItemMapper.selectByTemplate(plan.getTemplateId()).isEmpty()) {
                throw BizException.forbidden("模板没有检查项，无法启用计划");
            }
            generationService.syncScopeEquipment(plan);
            if (planEquipmentMapper.countByPlan(id) == 0) {
                throw BizException.forbidden("计划范围内没有可巡检设备，无法启用");
            }
            LocalDateTime next = generationService.nextOccurrence(plan, clock.now());
            planMapper.update(null, Wrappers.<InspPlan>lambdaUpdate()
                    .set(InspPlan::getStatus, PlanStatus.ENABLED.getCode())
                    .set(InspPlan::getNextGenerateTime, next)
                    .eq(InspPlan::getId, id));
            log.info("启用巡检计划 id={} 下次生成时间={}", id, next);
        } else {
            planMapper.update(null, Wrappers.<InspPlan>lambdaUpdate()
                    .set(InspPlan::getStatus, PlanStatus.DISABLED.getCode())
                    .set(InspPlan::getNextGenerateTime, null)
                    .eq(InspPlan::getId, id));
            log.info("暂停巡检计划 id={}", id);
        }
    }

    @Override
    public int generateNow(Long id) {
        InspPlan plan = requirePlan(id);
        if (!PlanStatus.ENABLED.getCode().equals(plan.getStatus())) {
            throw BizException.forbidden("计划已暂停，请先启用再生成任务");
        }
        int created = generationService.generateForPlan(id, clock.now(), true).created();
        log.info("手动生成巡检任务 planId={} created={}", id, created);
        return created;
    }

    // ==================================================================
    // 校验与工具
    // ==================================================================

    private void validate(PlanSaveDTO dto) {
        if (dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        InspectionCycleType cycleType = InspectionCycleType.of(dto.getCycleType());
        if (cycleType == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "执行周期取值非法");
        }
        if (cycleType == InspectionCycleType.WEEKLY
                && (dto.getWeekDays() == null || dto.getWeekDays().isEmpty())) {
            throw new BizException(ResultCode.BAD_REQUEST, "每周计划需要选择至少一个星期几");
        }
        if (cycleType == InspectionCycleType.MONTHLY
                && (dto.getMonthDays() == null || dto.getMonthDays().isEmpty())) {
            throw new BizException(ResultCode.BAD_REQUEST, "每月计划需要选择至少一个日期");
        }
        if (dto.getWeekDays() != null && dto.getWeekDays().stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw new BizException(ResultCode.BAD_REQUEST, "星期几取值应在 1-7 之间");
        }
        if (dto.getMonthDays() != null && dto.getMonthDays().stream().anyMatch(d -> d == null || d < 1 || d > 31)) {
            throw new BizException(ResultCode.BAD_REQUEST, "每月执行日取值应在 1-31 之间");
        }
        ExecutorStrategy strategy = ExecutorStrategy.of(dto.getExecutorStrategy());
        boolean needsExecutor = strategy != ExecutorStrategy.DEPT_OWNER;
        if (needsExecutor && (dto.getExecutorIds() == null || dto.getExecutorIds().isEmpty())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定巡检人员；选择「设备负责人」策略可留空");
        }
        if (dto.getExecutorIds() != null && !dto.getExecutorIds().isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(dto.getExecutorIds());
            if (users.size() != new HashSet<>(dto.getExecutorIds()).size()) {
                throw new BizException(ResultCode.BAD_REQUEST, "巡检人员中包含不存在的用户");
            }
        }
        if (PlanScopeType.of(dto.getScopeType()) == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "作用域类型取值非法");
        }
    }

    private InspTemplate requirePublishedTemplate(Long templateId) {
        InspTemplate template = templateId == null ? null : templateMapper.selectById(templateId);
        if (template == null) {
            throw BizException.notFound("巡检模板");
        }
        if (!TemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
            throw BizException.forbidden("模板「" + template.getTemplateName() + "」尚未发布，不能用于计划");
        }
        if (templateItemMapper.selectByTemplate(templateId).isEmpty()) {
            throw BizException.forbidden("模板「" + template.getTemplateName() + "」没有检查项");
        }
        return template;
    }

    private InspPlan requirePlan(Long id) {
        InspPlan plan = id == null ? null : planMapper.selectById(id);
        if (plan == null) {
            throw BizException.notFound("巡检计划");
        }
        return plan;
    }

    private String scopeJson(String scopeType, List<Long> ids) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(scopeKey(scopeType), ids == null ? List.of() : ids);
        try {
            return json.writeValueAsString(map);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "作用域配置序列化失败");
        }
    }

    private String executorJson(List<Long> ids) {
        try {
            return json.writeValueAsString(ids == null ? List.of() : ids);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "执行人配置序列化失败");
        }
    }

    private String scopeKey(String scopeType) {
        PlanScopeType type = PlanScopeType.of(scopeType);
        if (type == null) {
            return "equipmentIds";
        }
        switch (type) {
            case CATEGORY:
                return "categoryIds";
            case LOCATION:
                return "locationIds";
            case EQUIPMENT:
            default:
                return "equipmentIds";
        }
    }

    private String nextCode() {
        String prefix = CODE_PREFIX + clock.today().format(DAY);
        String max = planMapper.selectMaxCodeByPrefix(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        return prefix + String.format("%04d", next);
    }

    private PlanDetailVO copy(PlanVO base) {
        PlanDetailVO vo = new PlanDetailVO();
        vo.setId(base.getId());
        vo.setPlanCode(base.getPlanCode());
        vo.setPlanName(base.getPlanName());
        vo.setTemplateId(base.getTemplateId());
        vo.setTemplateName(base.getTemplateName());
        vo.setScopeType(base.getScopeType());
        vo.setScopeConfig(base.getScopeConfig());
        vo.setScopeLabel(base.getScopeLabel());
        vo.setExecutorStrategy(base.getExecutorStrategy());
        vo.setExecutorIds(base.getExecutorIds());
        vo.setExecutorNames(base.getExecutorNames());
        vo.setCycleType(base.getCycleType());
        vo.setCycleConfig(base.getCycleConfig());
        vo.setCycleLabel(base.getCycleLabel());
        vo.setStartDate(base.getStartDate());
        vo.setEndDate(base.getEndDate());
        vo.setSlaMinutes(base.getSlaMinutes());
        vo.setGenerateAheadHours(base.getGenerateAheadHours());
        vo.setStatus(base.getStatus());
        vo.setNextGenerateTime(base.getNextGenerateTime());
        vo.setLastGenerateTime(base.getLastGenerateTime());
        vo.setEquipmentCount(base.getEquipmentCount());
        vo.setTaskCount(base.getTaskCount());
        vo.setCreatorId(base.getCreatorId());
        vo.setCreatorName(base.getCreatorName());
        vo.setUpdateTime(base.getUpdateTime());
        return vo;
    }

    // ==================================================================
    // 展示字段填充
    // ==================================================================

    private void fillLabels(PlanVO vo, LabelContext ctx) {
        InspectionCycleType type = InspectionCycleType.of(vo.getCycleType());
        vo.setCycleLabel(CycleConfig.parse(vo.getCycleConfig()).describe(type));

        List<Long> scopeIds = parseIds(vo.getScopeConfig(), scopeKey(vo.getScopeType()));
        vo.setScopeIds(scopeIds);
        PlanScopeType scopeType = PlanScopeType.of(vo.getScopeType());
        String prefix = "设备：";
        List<String> names = new ArrayList<>();
        if (scopeType == PlanScopeType.CATEGORY) {
            prefix = "分类：";
            scopeIds.forEach(id -> names.add(ctx.categoryNames.getOrDefault(id, "#" + id)));
        } else if (scopeType == PlanScopeType.LOCATION) {
            prefix = "位置：";
            scopeIds.forEach(id -> names.add(ctx.locationNames.getOrDefault(id, "#" + id)));
        } else {
            scopeIds.forEach(id -> names.add(ctx.equipmentNames.getOrDefault(id, "#" + id)));
        }
        vo.setScopeLabel(names.isEmpty() ? "未设置范围" : prefix + joinNames(names));

        List<Long> executorIds = parseIdArray(vo.getExecutorIds());
        vo.setExecutorIdList(executorIds);
        if (executorIds.isEmpty()) {
            ExecutorStrategy strategy = ExecutorStrategy.of(vo.getExecutorStrategy());
            vo.setExecutorNames(strategy == ExecutorStrategy.DEPT_OWNER
                    ? "设备负责人" : "未指定（回退设备负责人）");
        } else {
            List<String> userNames = new ArrayList<>();
            executorIds.forEach(id -> userNames.add(ctx.userNames.getOrDefault(id, "#" + id)));
            vo.setExecutorNames(joinNames(userNames));
        }
    }

    private String joinNames(List<String> names) {
        if (names.size() <= MAX_LABEL_ITEMS) {
            return String.join("、", names);
        }
        return String.join("、", names.subList(0, MAX_LABEL_ITEMS)) + " 等 " + names.size() + " 个";
    }

    /**
     * 一次性把列表里用到的分类/位置/设备/用户名批量查出来，避免 N+1。
     */
    private LabelContext buildContext(List<PlanVO> rows) {
        LabelContext ctx = new LabelContext();
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        Set<Long> equipmentIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (PlanVO vo : rows) {
            List<Long> scopeIds = parseIds(vo.getScopeConfig(), scopeKey(vo.getScopeType()));
            PlanScopeType type = PlanScopeType.of(vo.getScopeType());
            if (type == PlanScopeType.CATEGORY) {
                categoryIds.addAll(scopeIds);
            } else if (type == PlanScopeType.LOCATION) {
                locationIds.addAll(scopeIds);
            } else {
                equipmentIds.addAll(scopeIds);
            }
            userIds.addAll(parseIdArray(vo.getExecutorIds()));
            if (vo.getCreatorId() != null) {
                userIds.add(vo.getCreatorId());
            }
        }
        if (!categoryIds.isEmpty()) {
            for (EqCategory category : categoryMapper.selectBatchIds(categoryIds)) {
                ctx.categoryNames.put(category.getId(), category.getCategoryName());
            }
        }
        if (!locationIds.isEmpty()) {
            for (EqLocation location : locationMapper.selectBatchIds(locationIds)) {
                ctx.locationNames.put(location.getId(), location.getLocationName());
            }
        }
        if (!equipmentIds.isEmpty()) {
            for (EqEquipment equipment : equipmentMapper.selectBatchIds(equipmentIds)) {
                ctx.equipmentNames.put(equipment.getId(), equipment.getEquipmentName());
            }
        }
        if (!userIds.isEmpty()) {
            for (SysUser user : userMapper.selectBatchIds(userIds)) {
                ctx.userNames.put(user.getId(), user.getRealName() == null ? user.getUsername() : user.getRealName());
            }
        }
        return ctx;
    }

    private List<Long> parseIds(String jsonText, String key) {
        if (!StringUtils.hasText(jsonText)) {
            return List.of();
        }
        try {
            JsonNode node = json.readTree(jsonText).path(key);
            if (node.isArray()) {
                return collect(node);
            }
        } catch (Exception ignored) {
            // 配置损坏时按空范围展示，不影响列表其它字段
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
                return collect(node);
            }
            JsonNode inner = node.path("ids");
            if (inner.isArray()) {
                return collect(inner);
            }
        } catch (Exception ignored) {
            // 忽略损坏的执行人配置
        }
        return List.of();
    }

    private List<Long> collect(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.canConvertToLong()) {
                ids.add(item.asLong());
            }
        }
        return ids;
    }

    private static class LabelContext {
        private final Map<Long, String> categoryNames = new HashMap<>();
        private final Map<Long, String> locationNames = new HashMap<>();
        private final Map<Long, String> equipmentNames = new HashMap<>();
        private final Map<Long, String> userNames = new HashMap<>();
    }
}
