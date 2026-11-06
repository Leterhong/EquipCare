package com.equipcare.inspection.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.asset.entity.EqCategory;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.entity.EqLocation;
import com.equipcare.asset.enums.EquipmentStatus;
import com.equipcare.asset.mapper.EqCategoryMapper;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.mapper.EqLocationMapper;
import com.equipcare.asset.service.EquipmentService;
import com.equipcare.common.event.DomainEventPublisher;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.inspection.dto.AnomalyQuery;
import com.equipcare.inspection.dto.AnomalyWorkOrderDTO;
import com.equipcare.inspection.dto.TaskCreateDTO;
import com.equipcare.inspection.dto.TaskQuery;
import com.equipcare.inspection.dto.TaskStartDTO;
import com.equipcare.inspection.dto.TaskSubmitDTO;
import com.equipcare.inspection.entity.InspAnomaly;
import com.equipcare.inspection.event.InspectionAnomalyFoundEvent;
import com.equipcare.inspection.entity.InspTask;
import com.equipcare.inspection.entity.InspTaskItem;
import com.equipcare.inspection.entity.InspTemplate;
import com.equipcare.inspection.entity.InspTemplateItem;
import com.equipcare.inspection.enums.AnomalyLevel;
import com.equipcare.inspection.enums.AnomalyStatus;
import com.equipcare.inspection.enums.InspItemResult;
import com.equipcare.inspection.enums.InspItemType;
import com.equipcare.inspection.enums.InspTaskStatus;
import com.equipcare.inspection.enums.InspectionResult;
import com.equipcare.inspection.mapper.InspAnomalyMapper;
import com.equipcare.inspection.mapper.InspTaskItemMapper;
import com.equipcare.inspection.mapper.InspTaskMapper;
import com.equipcare.inspection.mapper.InspTemplateItemMapper;
import com.equipcare.inspection.mapper.InspTemplateMapper;
import com.equipcare.inspection.service.InspectionTaskService;
import com.equipcare.inspection.support.InspectionClock;
import com.equipcare.inspection.vo.AnomalyVO;
import com.equipcare.inspection.vo.AttachmentVO;
import com.equipcare.inspection.vo.CalendarDayVO;
import com.equipcare.inspection.vo.EquipmentBriefVO;
import com.equipcare.inspection.vo.InspectionStatsVO;
import com.equipcare.inspection.vo.TaskDetailVO;
import com.equipcare.inspection.vo.TaskItemVO;
import com.equipcare.inspection.vo.TaskVO;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.workorder.service.WorkOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 巡检执行实现。
 *
 * <p>核心闭环：提交巡检 → 逐项三态判定 → 生成异常单 → 配置了自动转单的项立即建工单。
 *
 * <p>判定规则由服务端复算，不信任前端：数值项标「正常」但实测值超区间的提交会被直接拒绝，
 * 而不是默默改成异常——否则异常原因和证据都是空的，等于留下一份无效记录。
 */
@Service
public class InspectionTaskServiceImpl implements InspectionTaskService {

    private static final Logger log = LoggerFactory.getLogger(InspectionTaskServiceImpl.class);

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DEFAULT_MAINTAIN_CYCLE_DAYS = 30;
    /** 手工任务默认 SLA：8 小时 */
    private static final int DEFAULT_SLA_MINUTES = 480;

    private final InspTaskMapper taskMapper;
    private final InspTaskItemMapper taskItemMapper;
    private final InspTemplateItemMapper templateItemMapper;
    private final InspTemplateMapper templateMapper;
    private final InspAnomalyMapper anomalyMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final EqCategoryMapper categoryMapper;
    private final EqLocationMapper locationMapper;
    private final SysUserMapper userMapper;
    private final WorkOrderService workOrderService;
    private final EquipmentService equipmentService;
    private final com.equipcare.notification.service.MessageService messageService;
    private final DomainEventPublisher domainEventPublisher;
    private final InspectionClock clock;

    public InspectionTaskServiceImpl(InspTaskMapper taskMapper, InspTaskItemMapper taskItemMapper,
                                     InspTemplateItemMapper templateItemMapper, InspTemplateMapper templateMapper,
                                     InspAnomalyMapper anomalyMapper,
                                     EqEquipmentMapper equipmentMapper, EqCategoryMapper categoryMapper,
                                     EqLocationMapper locationMapper, SysUserMapper userMapper,
                                     WorkOrderService workOrderService, EquipmentService equipmentService,
                                     com.equipcare.notification.service.MessageService messageService,
                                     InspectionClock clock,
                                     DomainEventPublisher domainEventPublisher) {
        this.taskMapper = taskMapper;
        this.taskItemMapper = taskItemMapper;
        this.templateItemMapper = templateItemMapper;
        this.templateMapper = templateMapper;
        this.anomalyMapper = anomalyMapper;
        this.equipmentMapper = equipmentMapper;
        this.categoryMapper = categoryMapper;
        this.locationMapper = locationMapper;
        this.userMapper = userMapper;
        this.workOrderService = workOrderService;
        this.equipmentService = equipmentService;
        this.messageService = messageService;
        this.clock = clock;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public PageResult<TaskVO> page(TaskQuery query) {
        // assigneeId=0 表示「只看我的任务」，由服务端注入当前用户，避免前端伪造他人ID
        query.setCurrentUserId(SecurityUtils.getCurrentUserId());
        IPage<TaskVO> page = taskMapper.selectTaskPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createManual(TaskCreateDTO dto) {
        EqEquipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        if (EquipmentStatus.SCRAPPED.getCode().equals(equipment.getStatus())) {
            throw BizException.forbidden("已报废设备不能发起巡检");
        }
        Long templateId = dto.getTemplateId();
        if (templateId == null) {
            EqCategory category = categoryMapper.selectById(equipment.getCategoryId());
            templateId = category == null ? null : category.getDefaultTemplateId();
        }
        if (templateId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "该设备类别未配置默认巡检模板，请指定模板");
        }
        InspTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw BizException.notFound("巡检模板");
        }
        if (templateItemMapper.selectByTemplate(templateId).isEmpty()) {
            throw BizException.forbidden("巡检模板「" + template.getTemplateName() + "」尚未配置检查项");
        }
        Long assigneeId = dto.getAssigneeId() == null ? SecurityUtils.getCurrentUserId() : dto.getAssigneeId();

        LocalDate today = clock.today();
        InspTask task = new InspTask();
        task.setTaskCode(nextTaskCode());
        task.setEquipmentId(equipment.getId());
        task.setTemplateId(templateId);
        task.setPlanDate(today);
        task.setAssigneeId(assigneeId);
        task.setStatus(InspTaskStatus.PENDING.getCode());
        task.setSource("MANUAL");
        task.setAbnormalCount(0);
        task.setOverdueFlag(0);
        task.setQrVerified(0);
        task.setRemark(dto.getRemark());
        task.setSlaDueTime(clock.now().plusMinutes(DEFAULT_SLA_MINUTES));
        taskMapper.insert(task);
        // 第七阶段：巡检任务提醒（手工建任务也通知执行人）
        notifyTaskCreated(task, equipment);
        return task.getId();
    }

    @Override
    public TaskDetailVO detail(Long taskId) {
        InspTask task = requireTask(taskId);
        TaskDetailVO vo = taskMapper.selectTaskHeader(taskId);
        if (vo == null) {
            throw BizException.notFound("巡检任务");
        }
        vo.setRemark(task.getRemark());

        // 设备摘要：执行页要展示设备身份与二维码，避免「到错设备上巡检」
        EqEquipment equipment = equipmentMapper.selectById(task.getEquipmentId());
        if (equipment != null) {
            EquipmentBriefVO brief = new EquipmentBriefVO();
            brief.setId(equipment.getId());
            brief.setAssetNo(equipment.getAssetNo());
            brief.setEquipmentName(equipment.getEquipmentName());
            brief.setStatus(equipment.getStatus());
            brief.setStatusLabel(equipmentStatusLabel(equipment.getStatus()));
            EqCategory category = categoryMapper.selectById(equipment.getCategoryId());
            brief.setCategoryName(category == null ? null : category.getCategoryName());
            EqLocation location = equipment.getLocationId() == null ? null
                    : locationMapper.selectById(equipment.getLocationId());
            brief.setLocationPath(location == null ? null : location.getFullPath());
            if (equipment.getResponsibleUserId() != null) {
                SysUser owner = userMapper.selectById(equipment.getResponsibleUserId());
                brief.setResponsibleUserName(owner == null ? null : displayName(owner));
            }
            brief.setQrImageUrl("/api/inspection/tasks/" + taskId + "/equipment-qrcode.png");
            vo.setEquipment(brief);
        }

        // 模板标准与已填结果合并展示（历史任务也要能看到当时的检查标准）
        List<InspTemplateItem> templateItems = templateItemMapper.selectByTemplate(task.getTemplateId());
        Map<Long, InspTaskItem> results = new HashMap<>();
        for (InspTaskItem item : taskItemMapper.selectByTask(taskId)) {
            results.put(item.getTemplateItemId(), item);
        }
        Map<Long, String> itemValues = new HashMap<>();
        int skipped = 0;
        for (InspTemplateItem ti : templateItems) {
            TaskItemVO item = new TaskItemVO();
            item.setTemplateItemId(ti.getId());
            item.setItemName(ti.getItemName());
            item.setItemType(ti.getItemType());
            item.setUnit(ti.getUnit());
            item.setMinValue(ti.getMinValue());
            item.setMaxValue(ti.getMaxValue());
            item.setEnumOptions(ti.getEnumOptions());
            item.setNormalValue(ti.getNormalValue());
            item.setRequired(ti.getRequired());
            item.setCheckMethod(ti.getCheckMethod());
            item.setStandardRef(ti.getStandardRef());
            item.setAbnormalRule(ti.getAbnormalRule());
            item.setPhotoRequired(ti.getPhotoRequired());
            item.setAutoWorkOrder(ti.getAutoWorkOrder());
            InspTaskItem result = results.get(ti.getId());
            if (result != null) {
                item.setId(result.getId());
                item.setItemValue(result.getItemValue());
                item.setResult(resolveStoredResult(result));
                item.setIsAbnormal(result.getIsAbnormal());
                item.setAbnormalLevel(result.getAbnormalLevel());
                item.setRemark(result.getRemark());
                item.setSkipReason(result.getSkipReason());
                item.setAttachmentIds(result.getAttachmentIds());
                itemValues.put(ti.getId(), result.getItemValue());
                if (InspItemResult.SKIPPED.getCode().equals(item.getResult())) {
                    skipped++;
                }
            }
            vo.getItems().add(item);
        }
        vo.setSkippedCount(skipped);

        // 已产生的异常（带现场照片）
        for (InspAnomaly anomaly : anomalyMapper.selectByTask(taskId)) {
            AnomalyVO anomalyVO = toAnomalyVO(anomaly, vo.getEquipmentName(), vo.getAssetNo());
            anomalyVO.setTaskCode(vo.getTaskCode());
            anomalyVO.setItemValue(itemValues.get(anomaly.getTemplateItemId()));
            anomalyVO.setAttachments(loadAttachments(anomaly.getAttachmentIds()));
            vo.getAnomalies().add(anomalyVO);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long taskId, TaskStartDTO dto) {
        InspTask task = requireTask(taskId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!Objects.equals(task.getAssigneeId(), currentUserId) && !SecurityUtils.isAdmin()) {
            throw BizException.forbidden("只有任务执行人可以开始巡检");
        }
        if (!InspTaskStatus.PENDING.getCode().equals(task.getStatus())
                && !InspTaskStatus.EXPIRED.getCode().equals(task.getStatus())) {
            throw BizException.forbidden("当前任务状态不允许开始巡检");
        }
        Integer qrVerified = 0;
        if (dto != null && StringUtils.hasText(dto.getQrToken())) {
            EqEquipment equipment = equipmentMapper.selectByQrToken(dto.getQrToken().trim());
            if (equipment == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "二维码无效，请确认设备标签");
            }
            if (!Objects.equals(equipment.getId(), task.getEquipmentId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "扫码设备与任务设备不一致，疑似代签");
            }
            qrVerified = 1;
        }
        InspTask patch = new InspTask();
        patch.setId(taskId);
        patch.setStatus(InspTaskStatus.IN_PROGRESS.getCode());
        patch.setStartTime(clock.now());
        patch.setQrVerified(qrVerified);
        if (task.getSlaDueTime() != null && clock.now().isAfter(task.getSlaDueTime())) {
            patch.setOverdueFlag(1);
        }
        // CAS 条件更新：仅当状态仍为 PENDING/EXPIRED 时生效，防止并发双开
        // 或与调度器 markPendingExpired 竞态时覆盖过期标记（阶段十二整改 Q-03）。
        int updated = taskMapper.update(patch, Wrappers.<InspTask>lambdaUpdate()
                .eq(InspTask::getId, taskId)
                .in(InspTask::getStatus, java.util.List.of(
                        InspTaskStatus.PENDING.getCode(), InspTaskStatus.EXPIRED.getCode())));
        if (updated == 0) {
            throw BizException.forbidden("当前任务状态不允许开始巡检");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submit(Long taskId, TaskSubmitDTO dto) {
        InspTask task = requireTask(taskId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!Objects.equals(task.getAssigneeId(), currentUserId) && !SecurityUtils.isAdmin()) {
            throw BizException.forbidden("只有任务执行人可以提交巡检");
        }
        if (InspTaskStatus.SUBMITTED.getCode().equals(task.getStatus())) {
            throw BizException.forbidden("该任务已提交，不能重复提交");
        }
        if (taskItemMapper.countByTask(taskId) > 0) {
            throw BizException.forbidden("该任务已存在检查结果，不能重复提交");
        }

        List<InspTemplateItem> templateItems = templateItemMapper.selectByTemplate(task.getTemplateId());
        if (templateItems.isEmpty()) {
            throw BizException.forbidden("巡检模板未配置检查项，无法提交");
        }
        Map<Long, InspTemplateItem> templateMap = new HashMap<>();
        for (InspTemplateItem ti : templateItems) {
            templateMap.put(ti.getId(), ti);
        }
        Map<Long, TaskSubmitDTO.ItemResultDTO> submitted = new HashMap<>();
        for (TaskSubmitDTO.ItemResultDTO item : dto.getItems()) {
            if (!templateMap.containsKey(item.getTemplateItemId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "检查项不属于当前模板：" + item.getTemplateItemId());
            }
            submitted.put(item.getTemplateItemId(), item);
        }
        // 必检项必须表态：不能靠「不提交」绕过
        for (InspTemplateItem ti : templateItems) {
            if (isRequired(ti) && !submitted.containsKey(ti.getId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "必检项未填写：" + ti.getItemName());
            }
        }

        int abnormalCount = 0;
        int skippedCount = 0;
        List<InspAnomaly> anomalies = new ArrayList<>();
        for (InspTemplateItem ti : templateItems) {
            TaskSubmitDTO.ItemResultDTO input = submitted.get(ti.getId());
            if (input == null) {
                continue;
            }
            InspItemResult result = resolveResult(ti, input);
            if (result == InspItemResult.SKIPPED) {
                if (isRequired(ti)) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "「" + ti.getItemName() + "」是必检项，不能跳过；如确实无法检查请联系管理员调整模板");
                }
                if (!StringUtils.hasText(input.getSkipReason()) && !StringUtils.hasText(input.getRemark())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "跳过「" + ti.getItemName() + "」时必须填写跳过原因");
                }
                skippedCount++;
            }
            if (result == InspItemResult.ABNORMAL) {
                if (!StringUtils.hasText(input.getRemark())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "「" + ti.getItemName() + "」判为异常时必须填写异常说明");
                }
                if (ti.getPhotoRequired() != null && ti.getPhotoRequired() == 1
                        && !StringUtils.hasText(input.getAttachmentIds())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "「" + ti.getItemName() + "」要求上传现场照片作为异常证据");
                }
                abnormalCount++;
            }
            if (result == InspItemResult.NORMAL) {
                // 是否/枚举/数值属结构化判定，空值判正常等于没检查，历史记录也失去复核依据
                String type = ti.getItemType() == null ? "" : ti.getItemType().trim().toUpperCase();
                boolean structured = "BOOLEAN".equals(type) || "BOOL".equals(type)
                        || "ENUM".equals(type) || "NUMBER".equals(type);
                if (structured && !StringUtils.hasText(input.getItemValue())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "「" + ti.getItemName() + "」请填写实测值或选择检查结果，空值不能作为正常依据");
                }
            }

            boolean abnormal = result == InspItemResult.ABNORMAL;
            InspTaskItem record = new InspTaskItem();
            record.setTaskId(taskId);
            record.setTemplateItemId(ti.getId());
            record.setItemName(ti.getItemName());
            record.setItemType(ti.getItemType());
            record.setItemValue(input.getItemValue());
            record.setResult(result.getCode());
            record.setIsAbnormal(abnormal ? 1 : 0);
            record.setAbnormalLevel(abnormal ? defaultLevel(ti) : null);
            record.setRemark(input.getRemark());
            record.setSkipReason(result == InspItemResult.SKIPPED
                    ? firstText(input.getSkipReason(), input.getRemark()) : null);
            record.setAttachmentIds(input.getAttachmentIds());
            taskItemMapper.insert(record);

            if (!abnormal) {
                continue;
            }
            InspAnomaly anomaly = new InspAnomaly();
            anomaly.setAnomalyNo(nextAnomalyNo());
            anomaly.setTaskId(taskId);
            anomaly.setEquipmentId(task.getEquipmentId());
            anomaly.setTemplateItemId(ti.getId());
            anomaly.setItemName(ti.getItemName());
            anomaly.setLevel(defaultLevel(ti));
            anomaly.setDescription(buildAnomalyDesc(ti, input));
            anomaly.setAttachmentIds(input.getAttachmentIds());
            anomaly.setStatus(AnomalyStatus.OPEN.getCode());
            anomaly.setReporterId(currentUserId);
            anomaly.setReportTime(clock.now());
            anomalyMapper.insert(anomaly);
            anomalies.add(anomaly);
            // 领域事件：巡检发现异常（SSE 广播提醒；自动转单成功会另行发 WorkOrderCreatedEvent）
            domainEventPublisher.publish(new InspectionAnomalyFoundEvent(anomaly.getId(),
                    anomaly.getAnomalyNo(), taskId, anomaly.getEquipmentId(),
                    anomaly.getItemName(), anomaly.getLevel(), anomaly.getDescription()));
        }

        // 异常自动转工单：仅对模板中配置了 auto_work_order=1 的项建单
        for (InspAnomaly anomaly : anomalies) {
            InspTemplateItem ti = templateMap.get(anomaly.getTemplateItemId());
            if (ti == null || ti.getAutoWorkOrder() == null || ti.getAutoWorkOrder() != 1) {
                continue;
            }
            AnomalyLevel level = AnomalyLevel.of(anomaly.getLevel());
            Long workOrderId = workOrderService.createFromAnomaly(anomaly.getId(), taskId, anomaly.getEquipmentId(),
                    "INSPECT_" + (ti.getItemType() == null ? "OTHER" : ti.getItemType()),
                    anomaly.getDescription(), level.getWorkOrderLevel(),
                    "巡检异常：" + anomaly.getItemName());
            InspAnomaly patch = new InspAnomaly();
            patch.setId(anomaly.getId());
            patch.setWorkOrderId(workOrderId);
            patch.setStatus(AnomalyStatus.WORK_ORDER.getCode());
            anomalyMapper.updateById(patch);
            anomaly.setWorkOrderId(workOrderId);
            anomaly.setStatus(AnomalyStatus.WORK_ORDER.getCode());
            log.info("巡检异常自动转工单 anomalyNo={} workOrderId={}", anomaly.getAnomalyNo(), workOrderId);
        }

        // 更新任务
        LocalDateTime now = clock.now();
        InspTask patch = new InspTask();
        patch.setId(taskId);
        patch.setStatus(InspTaskStatus.SUBMITTED.getCode());
        patch.setSubmitTime(now);
        patch.setFinishTime(now);
        patch.setAbnormalCount(abnormalCount);
        patch.setResult(abnormalCount > 0 ? InspectionResult.ABNORMAL.getCode() : InspectionResult.NORMAL.getCode());
        patch.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : task.getRemark());
        if (task.getSlaDueTime() != null && now.isAfter(task.getSlaDueTime())) {
            patch.setOverdueFlag(1);
        }
        taskMapper.updateById(patch);

        // 回写设备的巡检时间，并按类别保养周期推算下次巡检时间
        updateEquipmentInspectTime(task.getEquipmentId(), now);

        // 第七阶段：巡检异常通知——提交时发现异常则推送管理角色（通知失败不影响提交）
        if (!anomalies.isEmpty()) {
            notifyAnomalies(task, anomalies.size());
        }

        log.info("巡检任务提交 taskCode={} 异常={} 跳过={}", task.getTaskCode(), abnormalCount, skippedCount);
        return abnormalCount;
    }

    @Override
    public PageResult<AnomalyVO> pageAnomaly(AnomalyQuery query) {
        IPage<AnomalyVO> page = anomalyMapper.selectAnomalyPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        for (AnomalyVO vo : page.getRecords()) {
            vo.setAttachments(loadAttachments(vo.getAttachmentIds()));
        }
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeAnomaly(Long anomalyId, String remark) {
        InspAnomaly anomaly = anomalyMapper.selectById(anomalyId);
        if (anomaly == null) {
            throw BizException.notFound("异常单");
        }
        if (AnomalyStatus.CLOSED.getCode().equals(anomaly.getStatus())) {
            throw BizException.forbidden("该异常单已关闭");
        }
        if (anomaly.getWorkOrderId() != null) {
            throw BizException.forbidden("该异常已转工单，请在工单流程中处理并自动关闭");
        }
        InspAnomaly patch = new InspAnomaly();
        patch.setId(anomalyId);
        patch.setStatus(AnomalyStatus.CLOSED.getCode());
        patch.setCloseTime(clock.now());
        patch.setCloseRemark(remark);
        anomalyMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertAnomaly(Long anomalyId, AnomalyWorkOrderDTO dto) {
        InspAnomaly anomaly = anomalyId == null ? null : anomalyMapper.selectById(anomalyId);
        if (anomaly == null) {
            throw BizException.notFound("异常单");
        }
        if (AnomalyStatus.CLOSED.getCode().equals(anomaly.getStatus())) {
            throw BizException.forbidden("该异常已关闭，不能再转工单");
        }
        if (anomaly.getWorkOrderId() != null) {
            throw BizException.forbidden("该异常已转工单，请到工单流程处理");
        }
        EqEquipment equipment = equipmentMapper.selectById(anomaly.getEquipmentId());
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        if (EquipmentStatus.SCRAPPED.getCode().equals(equipment.getStatus())) {
            throw BizException.forbidden("设备已报废，不能创建维修工单");
        }

        String itemType = null;
        if (anomaly.getTemplateItemId() != null) {
            InspTemplateItem item = templateItemMapper.selectById(anomaly.getTemplateItemId());
            if (item != null) {
                itemType = item.getItemType();
            }
        }
        String faultType = StringUtils.hasText(dto.getFaultType()) ? dto.getFaultType().trim()
                : "INSPECT_" + (itemType == null ? "OTHER" : itemType);
        String description = anomaly.getDescription() == null ? "" : anomaly.getDescription();
        if (StringUtils.hasText(dto.getRemark())) {
            description = description + "。补充说明：" + dto.getRemark();
        }
        if (description.length() > 1000) {
            description = description.substring(0, 1000);
        }
        String title = StringUtils.hasText(dto.getTitle()) ? dto.getTitle().trim()
                : "巡检异常：" + (anomaly.getItemName() == null ? "现场异常" : anomaly.getItemName());
        String level = StringUtils.hasText(dto.getLevel()) ? dto.getLevel()
                : AnomalyLevel.of(anomaly.getLevel()).getWorkOrderLevel();

        Long workOrderId = workOrderService.createFromAnomaly(anomaly.getId(), anomaly.getTaskId(),
                anomaly.getEquipmentId(), faultType, description, level, title);

        InspAnomaly patch = new InspAnomaly();
        patch.setId(anomaly.getId());
        patch.setWorkOrderId(workOrderId);
        patch.setStatus(AnomalyStatus.WORK_ORDER.getCode());
        anomalyMapper.updateById(patch);
        log.info("异常 {} 手动转工单 {}", anomaly.getAnomalyNo(), workOrderId);
        return workOrderId;
    }

    @Override
    public byte[] equipmentQrPng(Long taskId, int size, int margin) {
        InspTask task = requireTask(taskId);
        return equipmentService.qrCodePng(task.getEquipmentId(), size, margin);
    }

    @Override
    public List<CalendarDayVO> calendar(YearMonth month, Long assigneeId) {
        YearMonth target = month == null ? YearMonth.now(clock.zone()) : month;
        return taskMapper.selectCalendar(target.atDay(1), target.atEndOfMonth(), assigneeId);
    }

    @Override
    public InspectionStatsVO stats(int days, Long assigneeId) {
        int span = days <= 0 ? 30 : Math.min(days, 365);
        LocalDate to = clock.today();
        LocalDate from = to.minusDays(span - 1L);

        InspectionStatsVO vo = new InspectionStatsVO();
        vo.setDays(span);
        vo.setFrom(from);
        vo.setTo(to);

        Map<String, Object> aggregate = taskMapper.selectTaskAggregate(from, to, assigneeId);
        int total = intOf(aggregate, "total");
        int submitted = intOf(aggregate, "submitted");
        int expired = intOf(aggregate, "expired");
        int pending = intOf(aggregate, "pending");
        int abnormal = intOf(aggregate, "abnormal");
        vo.setTotalTasks(total);
        vo.setSubmittedTasks(submitted);
        vo.setExpiredTasks(expired);
        vo.setPendingTasks(pending);
        vo.setAbnormalTasks(abnormal);
        vo.setCompletionRate(rate(submitted, total));
        vo.setAbnormalRate(rate(abnormal, submitted));

        // 状态分布：枚举全量列出，缺的补 0，前端图表不需要自己补空
        Map<String, Integer> statusCounts = new HashMap<>();
        for (InspectionStatsVO.StatusCount item : taskMapper.selectStatusCounts(from, to, assigneeId)) {
            statusCounts.put(item.getCode(), item.getCount());
        }
        for (InspTaskStatus status : InspTaskStatus.values()) {
            vo.getByStatus().add(new InspectionStatsVO.StatusCount(status.getCode(), status.getLabel(),
                    statusCounts.getOrDefault(status.getCode(), 0)));
        }

        // 异常单状态
        List<InspectionStatsVO.StatusCount> anomalyCounts = anomalyMapper.selectAnomalyStatusCounts(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        int open = 0;
        int converted = 0;
        int closed = 0;
        for (InspectionStatsVO.StatusCount item : anomalyCounts) {
            if (AnomalyStatus.OPEN.getCode().equals(item.getCode())) {
                open = item.getCount();
            } else if (AnomalyStatus.WORK_ORDER.getCode().equals(item.getCode())) {
                converted = item.getCount();
            } else if (AnomalyStatus.CLOSED.getCode().equals(item.getCode())) {
                closed = item.getCount();
            }
        }
        vo.setAnomalyOpen(open);
        vo.setAnomalyConverted(converted);
        vo.setAnomalyClosed(closed);
        vo.setAnomalyTotal(open + converted + closed);

        // 计划维度
        for (InspectionStatsVO.PlanStat stat : taskMapper.selectPlanStats(from, to, 10)) {
            stat.setCompletionRate(rate(stat.getSubmitted() == null ? 0 : stat.getSubmitted(),
                    stat.getTotal() == null ? 0 : stat.getTotal()));
            vo.getByPlan().add(stat);
        }

        // 每日趋势：补齐没有任务的日期，保证折线连续
        Map<LocalDate, CalendarDayVO> dayMap = new HashMap<>();
        for (CalendarDayVO day : taskMapper.selectCalendar(from, to, assigneeId)) {
            dayMap.put(day.getDate(), day);
        }
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            CalendarDayVO day = dayMap.get(date);
            InspectionStatsVO.DailyStat stat = new InspectionStatsVO.DailyStat();
            stat.setDate(date);
            stat.setTotal(day == null ? 0 : value(day.getTotal()));
            stat.setSubmitted(day == null ? 0 : value(day.getSubmitted()));
            stat.setAbnormal(day == null ? 0 : value(day.getAbnormal()));
            vo.getDaily().add(stat);
        }

        vo.getTopAnomalyItems().addAll(anomalyMapper.selectTopItems(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), 10));
        return vo;
    }

    @Override
    public int expireOverdue() {
        LocalDateTime now = clock.now();
        int expired = taskMapper.markPendingExpired(now);
        int overdue = taskMapper.markInProgressOverdue(now);
        if (expired > 0 || overdue > 0) {
            log.info("巡检任务过期处理：标记逾期={} 标记超时未完成={}", expired, overdue);
        }
        return expired + overdue;
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 解析单项结果。
     *
     * <p>优先级：显式 result > 兼容字段 abnormal > 服务端按数值区间判定。
     * 显式标「正常」但数值超区间的，直接拒绝而不是偷偷改判——
     * 改判会留下一条没有原因、没有证据的异常记录，比报错更糟。
     */
    private InspItemResult resolveResult(InspTemplateItem ti, TaskSubmitDTO.ItemResultDTO input) {
        boolean outOfRange = judgeAbnormal(ti, input);
        if (StringUtils.hasText(input.getResult())) {
            InspItemResult result = InspItemResult.of(input.getResult().trim().toUpperCase());
            if (result == null) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "「" + ti.getItemName() + "」的检查结果取值非法：" + input.getResult());
            }
            if (result == InspItemResult.NORMAL && outOfRange) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "「" + ti.getItemName() + "」实测值超出标准区间，请判为异常并填写原因");
            }
            return result;
        }
        if (Boolean.TRUE.equals(input.getAbnormal())) {
            return InspItemResult.ABNORMAL;
        }
        return outOfRange ? InspItemResult.ABNORMAL : InspItemResult.NORMAL;
    }

    /**
     * 判定异常：数值超出模板区间；数值型填了非数字按录入错误处理，视为异常以便人工复核。
     */
    private boolean judgeAbnormal(InspTemplateItem ti, TaskSubmitDTO.ItemResultDTO input) {
        if (Boolean.TRUE.equals(input.getAbnormal())) {
            return true;
        }
        if (!InspItemType.NUMBER.getCode().equalsIgnoreCase(ti.getItemType())
                || !StringUtils.hasText(input.getItemValue())) {
            return false;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(input.getItemValue().trim());
        } catch (NumberFormatException e) {
            return true;
        }
        if (ti.getMinValue() != null && value.compareTo(ti.getMinValue()) < 0) {
            return true;
        }
        return ti.getMaxValue() != null && value.compareTo(ti.getMaxValue()) > 0;
    }

    private boolean isRequired(InspTemplateItem ti) {
        return ti.getRequired() != null && ti.getRequired() == 1;
    }

    private String defaultLevel(InspTemplateItem ti) {
        return StringUtils.hasText(ti.getAbnormalLevel()) ? ti.getAbnormalLevel() : AnomalyLevel.MEDIUM.getCode();
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String resolveStoredResult(InspTaskItem item) {
        if (StringUtils.hasText(item.getResult())) {
            return item.getResult();
        }
        // 兼容第四阶段之前写入的历史记录：只有 is_abnormal 标记
        return item.getIsAbnormal() != null && item.getIsAbnormal() == 1
                ? InspItemResult.ABNORMAL.getCode() : InspItemResult.NORMAL.getCode();
    }

    private String buildAnomalyDesc(InspTemplateItem ti, TaskSubmitDTO.ItemResultDTO input) {
        StringBuilder sb = new StringBuilder();
        sb.append("检查项「").append(ti.getItemName()).append("」");
        if (StringUtils.hasText(input.getItemValue())) {
            sb.append("实测值 ").append(input.getItemValue());
            if (StringUtils.hasText(ti.getUnit())) {
                sb.append(ti.getUnit());
            }
        }
        if (ti.getMinValue() != null && ti.getMaxValue() != null) {
            sb.append("，标准区间 ").append(ti.getMinValue().stripTrailingZeros().toPlainString())
                    .append(" ~ ").append(ti.getMaxValue().stripTrailingZeros().toPlainString());
        }
        if (StringUtils.hasText(input.getRemark())) {
            sb.append("。说明：").append(input.getRemark());
        }
        String desc = sb.toString();
        return desc.length() > 500 ? desc.substring(0, 500) : desc;
    }

    private void updateEquipmentInspectTime(Long equipmentId, LocalDateTime inspectTime) {
        EqEquipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            return;
        }
        int cycleDays = DEFAULT_MAINTAIN_CYCLE_DAYS;
        EqCategory category = categoryMapper.selectById(equipment.getCategoryId());
        if (category != null && category.getMaintainCycleDays() != null && category.getMaintainCycleDays() > 0) {
            cycleDays = category.getMaintainCycleDays();
        }
        EqEquipment patch = new EqEquipment();
        patch.setId(equipmentId);
        patch.setLastInspectTime(inspectTime);
        patch.setNextInspectTime(inspectTime.plusDays(cycleDays));
        equipmentMapper.update(patch, Wrappers.<EqEquipment>lambdaUpdate()
                .eq(EqEquipment::getId, equipmentId));
    }

    private InspTask requireTask(Long taskId) {
        InspTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("巡检任务");
        }
        return task;
    }

    private String nextTaskCode() {
        String prefix = "IT" + clock.today().format(DAY_FORMAT);
        String max = taskMapper.selectMaxTaskCodeByPrefix(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 编号异常时按 1 重新计数
            }
        }
        return prefix + String.format("%04d", next);
    }

    private String nextAnomalyNo() {
        String prefix = "AB" + clock.today().format(DAY_FORMAT);
        String max = anomalyMapper.selectMaxNoByPrefix(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 编号异常时按 1 重新计数
            }
        }
        return prefix + String.format("%04d", next);
    }

    private List<AttachmentVO> loadAttachments(String attachmentIds) {
        if (!StringUtils.hasText(attachmentIds)) {
            return new ArrayList<>();
        }
        return anomalyMapper.selectAttachments(attachmentIds);
    }

    private String equipmentStatusLabel(String code) {
        for (EquipmentStatus status : EquipmentStatus.values()) {
            if (status.getCode().equals(code)) {
                return status.getLabel();
            }
        }
        return code;
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private AnomalyVO toAnomalyVO(InspAnomaly anomaly, String equipmentName, String assetNo) {
        AnomalyVO vo = new AnomalyVO();
        vo.setId(anomaly.getId());
        vo.setAnomalyNo(anomaly.getAnomalyNo());
        vo.setTaskId(anomaly.getTaskId());
        vo.setEquipmentId(anomaly.getEquipmentId());
        vo.setItemName(anomaly.getItemName());
        vo.setLevel(anomaly.getLevel());
        vo.setDescription(anomaly.getDescription());
        vo.setStatus(anomaly.getStatus());
        vo.setWorkOrderId(anomaly.getWorkOrderId());
        vo.setReporterId(anomaly.getReporterId());
        vo.setReportTime(anomaly.getReportTime());
        vo.setCloseTime(anomaly.getCloseTime());
        vo.setEquipmentName(equipmentName);
        vo.setAssetNo(assetNo);
        return vo;
    }

    private int intOf(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    /** 百分比，保留一位小数；分母为 0 返回 0 避免前端出现 NaN */
    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    /** 第七阶段：巡检任务提醒。通知失败只记日志，不回滚任务创建。 */
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

    /** 第七阶段：巡检异常通知。推送给管理角色，发起人本人不重复推送。 */
    private void notifyAnomalies(InspTask task, int anomalyCount) {
        try {
            Set<Long> receivers = new HashSet<>();
            for (String roleCode : List.of("ADMIN", "EQUIP_ADMIN")) {
                receivers.addAll(userMapper.selectUserIdsByRoleCode(roleCode));
            }
            Long initiator = null;
            try {
                initiator = SecurityUtils.getCurrentUserId();
            } catch (RuntimeException ignored) {
                // 调度器等无登录上下文场景：全员推送
            }
            for (Long receiverId : receivers) {
                if (Objects.equals(receiverId, initiator)) {
                    continue;
                }
                try {
                    messageService.send(receiverId, "巡检异常通知",
                            "巡检任务 " + task.getTaskCode() + " 发现 " + anomalyCount + " 项异常，请及时处理",
                            "TASK", "INSPECTION_ANOMALY", task.getId());
                } catch (RuntimeException e) {
                    log.warn("巡检异常通知发送失败 receiverId={} taskId={}", receiverId, task.getId(), e);
                }
            }
        } catch (RuntimeException e) {
            log.warn("巡检异常通知分发失败 taskId={}", task.getId(), e);
        }
    }
}
