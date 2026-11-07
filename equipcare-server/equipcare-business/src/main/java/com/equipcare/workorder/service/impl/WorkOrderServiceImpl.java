package com.equipcare.workorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.enums.EquipmentStatus;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.common.event.DomainEventPublisher;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.inventory.mapper.SpWorkOrderPartMapper;
import com.equipcare.notification.service.MessageService;
import com.equipcare.common.security.LoginUser;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.workorder.dto.DispatchDTO;
import com.equipcare.workorder.dto.EvaluateDTO;
import com.equipcare.workorder.dto.ReasonDTO;
import com.equipcare.workorder.dto.RepairRecordDTO;
import com.equipcare.workorder.dto.StartRepairDTO;
import com.equipcare.workorder.dto.SubmitVerifyDTO;
import com.equipcare.workorder.dto.VerifyDTO;
import com.equipcare.workorder.dto.WorkOrderCreateDTO;
import com.equipcare.workorder.dto.WorkOrderQuery;
import com.equipcare.workorder.entity.WoDispatchLog;
import com.equipcare.workorder.entity.WoEvaluation;
import com.equipcare.workorder.entity.WoRepairRecord;
import com.equipcare.workorder.entity.WoStatusLog;
import com.equipcare.workorder.entity.WoVerifyRecord;
import com.equipcare.workorder.entity.WoWorkOrder;
import com.equipcare.workorder.entity.SlaPolicy;
import com.equipcare.workorder.enums.DispatchType;
import com.equipcare.workorder.enums.RepairRecordType;
import com.equipcare.workorder.enums.SlaStatus;
import com.equipcare.workorder.enums.VerifyResult;
import com.equipcare.workorder.enums.WoSource;
import com.equipcare.workorder.enums.WorkOrderAction;
import com.equipcare.workorder.enums.WorkOrderLevel;
import com.equipcare.workorder.enums.WorkOrderPriority;
import com.equipcare.workorder.WorkOrderAccessPolicy;
import com.equipcare.workorder.enums.WorkOrderStatus;
import com.equipcare.workorder.event.WorkOrderCreatedEvent;
import com.equipcare.workorder.event.WorkOrderStatusChangedEvent;
import com.equipcare.workorder.mapper.WoDispatchLogMapper;
import com.equipcare.workorder.mapper.WoEvaluationMapper;
import com.equipcare.workorder.mapper.WoInspectionSourceMapper;
import com.equipcare.workorder.mapper.WoPartQueryMapper;
import com.equipcare.workorder.mapper.WoRepairRecordMapper;
import com.equipcare.workorder.mapper.SlaPolicyMapper;
import com.equipcare.workorder.mapper.WoStatusLogMapper;
import com.equipcare.workorder.mapper.WoVerifyRecordMapper;
import com.equipcare.workorder.mapper.WoWorkOrderMapper;
import com.equipcare.workorder.service.WorkOrderService;
import com.equipcare.workorder.statemachine.WorkOrderStateMachine;
import com.equipcare.workorder.statemachine.WorkOrderTransition;
import com.equipcare.workorder.vo.DispatchRecordVO;
import com.equipcare.workorder.vo.InspectionSourceVO;
import com.equipcare.workorder.vo.RepairRecordVO;
import com.equipcare.workorder.vo.SourceAttachmentVO;
import com.equipcare.workorder.vo.StatusCountVO;
import com.equipcare.workorder.vo.VerifyRecordVO;
import com.equipcare.workorder.vo.WorkOrderAssigneeVO;
import com.equipcare.workorder.vo.WorkOrderDetailVO;
import com.equipcare.workorder.vo.WorkOrderEvaluationVO;
import com.equipcare.workorder.vo.WorkOrderPartVO;
import com.equipcare.workorder.vo.WorkOrderTimelineItemVO;
import com.equipcare.workorder.vo.WorkOrderTimelineVO;
import com.equipcare.workorder.vo.WorkOrderVO;
import com.equipcare.workorder.vo.WorkOrderWorkbenchVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 维修工单实现。
 *
 * <p>所有状态变更统一走 {@link #apply(WoWorkOrder, WorkOrderAction, String, Consumer, Consumer)}：
 * <ol>
 *   <li>状态机校验 (from, action) 是否合法 —— 非法抛 3001；</li>
 *   <li>角色 / 本人校验 —— 无权限抛 403；</li>
 *   <li>原因长度校验；</li>
 *   <li>CAS 落库 {@code WHERE status=from AND version=version} —— 冲突抛 3002；</li>
 *   <li>写 {@code wo_status_log} 轨迹（操作人、时间、原因三要素齐全）；</li>
 *   <li>执行业务副作用（改设备状态、写派单轨迹、发通知等）。</li>
 * </ol>
 *
 * <p>本类不提供、也不允许出现「直接改 status」的入口：全库唯一写 {@code wo_work_order.status}
 * 的 SQL 是 {@code WoWorkOrderMapper.casUpdateStatus}。
 */
@Service
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderServiceImpl.class);

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String MSG_TYPE_WORK_ORDER = "WORK_ORDER";
    private static final String BIZ_WORK_ORDER = "WORK_ORDER";
    private static final int DEFAULT_LIMIT = 10;

    private final WoWorkOrderMapper orderMapper;
    private final WoStatusLogMapper statusLogMapper;
    private final WoDispatchLogMapper dispatchLogMapper;
    private final WoRepairRecordMapper repairRecordMapper;
    private final WoVerifyRecordMapper verifyRecordMapper;
    private final WoEvaluationMapper evaluationMapper;
    private final WoPartQueryMapper partQueryMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final SysUserMapper userMapper;
    private final SpWorkOrderPartMapper workOrderPartMapper;
    private final MessageService messageService;
    private final WorkOrderStateMachine stateMachine;
    private final WoInspectionSourceMapper inspectionSourceMapper;
    private final SlaPolicyMapper slaPolicyMapper;
    private final DomainEventPublisher domainEventPublisher;

    public WorkOrderServiceImpl(WoWorkOrderMapper orderMapper, WoStatusLogMapper statusLogMapper,
                                WoDispatchLogMapper dispatchLogMapper, WoRepairRecordMapper repairRecordMapper,
                                WoVerifyRecordMapper verifyRecordMapper, WoEvaluationMapper evaluationMapper,
                                WoPartQueryMapper partQueryMapper, EqEquipmentMapper equipmentMapper,
                                SysUserMapper userMapper, SpWorkOrderPartMapper workOrderPartMapper,
                                MessageService messageService, WorkOrderStateMachine stateMachine,
                                WoInspectionSourceMapper inspectionSourceMapper,
                                SlaPolicyMapper slaPolicyMapper,
                                DomainEventPublisher domainEventPublisher) {
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
        this.dispatchLogMapper = dispatchLogMapper;
        this.repairRecordMapper = repairRecordMapper;
        this.verifyRecordMapper = verifyRecordMapper;
        this.evaluationMapper = evaluationMapper;
        this.partQueryMapper = partQueryMapper;
        this.equipmentMapper = equipmentMapper;
        this.userMapper = userMapper;
        this.workOrderPartMapper = workOrderPartMapper;
        this.messageService = messageService;
        this.stateMachine = stateMachine;
        this.inspectionSourceMapper = inspectionSourceMapper;
        this.slaPolicyMapper = slaPolicyMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public PageResult<WorkOrderVO> page(WorkOrderQuery query) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        query.setCurrentUserId(loginUser.getUserId());
        IPage<WorkOrderVO> page = orderMapper.selectWorkOrderPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        for (WorkOrderVO vo : page.getRecords()) {
            decorate(vo, loginUser);
        }
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public WorkOrderDetailVO detail(Long id) {
        WoWorkOrder order = requireOrder(id);
        LoginUser loginUser = SecurityUtils.getLoginUser();
        // 渗透整改 P3：详情可见性与列表数据范围对齐——SELF 角色仅本单相关人员可见
        if (!WorkOrderAccessPolicy.isVisible(order, loginUser.getUserId(), new java.util.HashSet<>(loginUser.getRoles()))) {
            throw BizException.forbidden("无权查看该工单详情");
        }
        WorkOrderDetailVO vo = new WorkOrderDetailVO();
        fillBase(vo, order);
        vo.setAnomalyId(order.getAnomalyId());
        vo.setSourceId(order.getSourceId());
        vo.setDispatcherId(order.getDispatcherId());
        vo.setStartRepairTime(order.getStartRepairTime());
        vo.setRepairStarted(order.getStartRepairTime() != null);
        vo.setSubmitVerifyTime(order.getSubmitVerifyTime());
        vo.setRepairSummary(order.getRepairSummary());
        vo.setLaborHours(order.getLaborHours());
        vo.setVerifyUserId(order.getVerifyUserId());
        vo.setVerifyTime(order.getVerifyTime());
        vo.setVerifyResult(order.getVerifyResult());
        vo.setVerifyRemark(order.getVerifyRemark());
        vo.setQualityScore(order.getQualityScore());
        vo.setActualFinishTime(order.getActualFinishTime());
        vo.setPrevEquipmentStatus(order.getPrevEquipmentStatus());
        vo.setCancelReason(order.getCancelReason());
        vo.setVersion(order.getVersion());
        vo.setUpdateTime(order.getUpdateTime());
        vo.setEvaluated(evaluationMapper.selectByOrder(id) != null);

        if (order.getDispatcherId() != null) {
            vo.setDispatcherName(realName(order.getDispatcherId()));
        }
        if (order.getVerifyUserId() != null) {
            vo.setVerifyUserName(realName(order.getVerifyUserId()));
        }

        // 故障现场照片：只存文件ID，按ID回查 sys_file，不复制文件
        vo.setFaultImageIds(order.getFaultImageIds());
        if (StringUtils.hasText(order.getFaultImageIds())) {
            vo.setFaultImages(inspectionSourceMapper.selectAttachments(order.getFaultImageIds()));
        }

        fillTimelineNodes(vo, order);
        fillRepairRecords(vo, id);
        fillVerifyRecords(vo, id);
        fillDispatchRecords(vo, id);

        vo.setParts(partQueryMapper.selectByOrder(id));
        WoEvaluation evaluation = evaluationMapper.selectByOrder(id);
        if (evaluation != null) {
            vo.setEvaluation(toEvaluationVO(evaluation));
        }
        vo.setHistory(buildTimeline(order));

        vo.setAllowedActions(allowedActionsFor(WorkOrderStatus.of(order.getStatus()), loginUser, vo));

        // 巡检来源：实时联查，工单表里只有 anomaly_id，不存设备/检查项/照片副本
        if (order.getAnomalyId() != null) {
            InspectionSourceVO source = inspectionSourceMapper.selectByAnomalyId(order.getAnomalyId());
            if (source != null) {
                if (source.getTaskId() == null) {
                    source.setTaskId(order.getSourceId());
                }
                fillSourceAttachments(source);
                vo.setSourceInspection(source);
            }
            // 同设备同故障在途合并时，后续异常靠 insp_anomaly.work_order_id 归属本工单，
            // 一并查出来，避免合并后的现场描述与照片在工单侧丢失。
            List<InspectionSourceVO> merged = inspectionSourceMapper.selectByWorkOrderId(order.getId());
            merged.removeIf(item -> Objects.equals(item.getAnomalyId(), order.getAnomalyId()));
            merged.forEach(this::fillSourceAttachments);
            vo.setMergedInspections(merged);
        }
        return vo;
    }

    @Override
    public List<WorkOrderTimelineItemVO> timeline(Long id) {
        return buildTimeline(requireOrder(id));
    }

    @Override
    public List<WorkOrderPartVO> parts(Long id) {
        requireOrder(id);
        List<WorkOrderPartVO> parts = partQueryMapper.selectByOrder(id);
        parts.forEach(p -> p.setIssueStatusLabel(partStatusLabel(p.getIssueStatus())));
        return parts;
    }

    @Override
    public WorkOrderWorkbenchVO workbench(Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 50);
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long me = loginUser.getUserId();
        WorkOrderWorkbenchVO vo = new WorkOrderWorkbenchVO();
        vo.setCounts(orderMapper.countGroupByStatus(null));

        // 待派单 / 已驳回：派单人的活；非派单角色也返回，前端按权限决定是否显示
        vo.setPendingAssign(fetch(size, q -> q.setStatus(WorkOrderStatus.PENDING_ASSIGN.getCode()), true));
        vo.setRejected(fetch(size, q -> q.setStatus(WorkOrderStatus.REJECTED.getCode()), true));
        // 待我接单 / 我维修中：维修人的活
        vo.setPendingAccept(fetch(size, q -> {
            q.setStatus(WorkOrderStatus.PENDING_ACCEPT.getCode());
            q.setAssigneeId(me);
        }, false));
        vo.setRepairing(fetch(size, q -> {
            q.setStatus(WorkOrderStatus.IN_PROGRESS.getCode());
            q.setAssigneeId(me);
        }, false));
        // 待我验收：验收人的活（不含自己维修的工单）
        List<WorkOrderVO> review = fetch(size, q -> q.setStatus(WorkOrderStatus.PENDING_REVIEW.getCode()), false)
                .stream()
                .filter(o -> !Objects.equals(o.getAssigneeId(), me))
                .collect(Collectors.toList());
        vo.setPendingReview(review);
        // 我最近完成
        vo.setCompleted(fetch(size, q -> {
            q.setStatus(WorkOrderStatus.COMPLETED.getCode());
            q.setAssigneeId(me);
        }, false));
        return vo;
    }

    @Override
    public List<WorkOrderAssigneeVO> assignees() {
        return orderMapper.selectAssignees();
    }

    @Override
    public List<StatusCountVO> statusCounts(Long assigneeId) {
        return orderMapper.countGroupByStatus(assigneeId);
    }

    // ==================================================================
    // 建单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createManual(WorkOrderCreateDTO dto) {
        EqEquipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        if (EquipmentStatus.SCRAPPED.getCode().equals(equipment.getStatus())) {
            throw BizException.forbidden("已报废设备不能报修");
        }
        Long reporterId = SecurityUtils.getCurrentUserId();
        WoWorkOrder order = buildOrder(equipment, WoSource.MANUAL, dto.getTitle(), dto.getFaultType(),
                dto.getFaultDesc(), dto.getLevel(), reporterId);
        order.setFaultImageIds(dto.getFaultImageIds());
        insertWithOrderNoRetry(order);
        writeCreateLog(order, reporterId);
        notify(reporterId, "报修已提交", "工单 " + order.getOrderNo() + " 已提交，等待派单", order.getId());
        // 第七阶段：新工单同步推送给可派单的管理角色
        notifyRoles(List.of("ADMIN", "EQUIP_ADMIN"), "新工单待派单",
                "新报修工单 " + order.getOrderNo() + "（" + order.getTitle() + "）等待派单", order.getId());
        log.info("人工报修建单成功 orderNo={} equipmentId={}", order.getOrderNo(), equipment.getId());
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromAnomaly(Long anomalyId, Long taskId, Long equipmentId, String faultType, String faultDesc,
                                  String level, String title) {
        if (anomalyId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "异常ID不能为空");
        }
        // 幂等：同一异常只允许生成一张工单
        WoWorkOrder existing = orderMapper.selectByAnomalyId(anomalyId);
        if (existing != null) {
            log.info("异常 {} 已存在工单 {}，跳过建单", anomalyId, existing.getOrderNo());
            return existing.getId();
        }
        // 合并：同设备同故障类型已有在途工单，直接挂到该工单，避免工单风暴
        WoWorkOrder inflight = orderMapper.selectInflightSameFault(equipmentId, faultType);
        if (inflight != null) {
            log.info("异常 {} 合并至在途工单 {}", anomalyId, inflight.getOrderNo());
            return inflight.getId();
        }
        EqEquipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
        WoWorkOrder order = buildOrder(equipment, taskId == null ? WoSource.ANOMALY : WoSource.INSPECT,
                title, faultType, faultDesc, level, SecurityUtils.getCurrentUserId());
        order.setAnomalyId(anomalyId);
        // 来源单据ID存巡检任务，工单详情据此联查原始巡检记录
        order.setSourceId(taskId);
        insertWithOrderNoRetry(order);
        writeCreateLog(order, SecurityUtils.getCurrentUserId());
        // 第七阶段：异常转工单同样推送新工单通知
        notifyRoles(List.of("ADMIN", "EQUIP_ADMIN"), "新工单待派单",
                "巡检异常已转工单 " + order.getOrderNo() + "（" + order.getTitle() + "）等待派单", order.getId());
        log.info("异常转工单成功 anomalyId={} taskId={} orderNo={}", anomalyId, taskId, order.getOrderNo());
        return order.getId();
    }

    // ==================================================================
    // 状态动作
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long id, DispatchDTO dto) {
        WoWorkOrder order = requireOrder(id);
        SysUser assignee = requireAssignableUser(dto.getAssigneeId());
        boolean redispatch = WorkOrderStatus.REJECTED.getCode().equals(order.getStatus());
        WorkOrderPriority priority = StringUtils.hasText(dto.getPriority())
                ? WorkOrderPriority.of(dto.getPriority())
                : WorkOrderPriority.of(order.getPriority());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resolveDue = dto.getResolveHours() == null
                ? dueTime(order.getLevel(), false)
                : now.plusHours(dto.getResolveHours());

        apply(order, WorkOrderAction.DISPATCH, dto.getReason(), patch -> patch
                        .set(WoWorkOrder::getAssigneeId, assignee.getId())
                        .set(WoWorkOrder::getDispatcherId, currentUserId())
                        .set(WoWorkOrder::getDispatchTime, now)
                        .set(WoWorkOrder::getPriority, (priority == null
                                ? WorkOrderPriority.NORMAL : priority).getCode())
                        .set(WoWorkOrder::getResponseDueTime, dueTime(order.getLevel(), true))
                        .set(WoWorkOrder::getResolveDueTime, resolveDue),
                after -> {
                    writeDispatchLog(after, redispatch ? DispatchType.REDISPATCH : DispatchType.ASSIGN,
                            null, assignee.getId(), dto.getReason());
                    notify(assignee.getId(), "新工单待接单",
                            "工单 " + after.getOrderNo() + "（" + after.getTitle() + "）已派给你，请及时接单",
                            after.getId());
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id) {
        WoWorkOrder order = requireOrder(id);
        LocalDateTime now = LocalDateTime.now();
        boolean overdue = order.getResponseDueTime() != null && now.isAfter(order.getResponseDueTime());
        apply(order, WorkOrderAction.ACCEPT, null, patch -> {
            patch.set(WoWorkOrder::getAcceptTime, now);
            patch.set(WoWorkOrder::getStartRepairTime, null); // 维修时长为空，需显式「开始维修」
            if (overdue) {
                patch.set(WoWorkOrder::getOverdueFlag, 1);
                patch.set(WoWorkOrder::getSlaStatus, SlaStatus.OVERDUE.getCode());
            }
        }, after -> {
            if (after.getDispatcherId() != null) {
                notify(after.getDispatcherId(), "工单已被接单",
                        "工单 " + after.getOrderNo() + " 已由 " + realName(after.getAssigneeId()) + " 接单",
                        after.getId());
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectDispatch(Long id, ReasonDTO dto) {
        WoWorkOrder order = requireOrder(id);
        Long previousAssignee = order.getAssigneeId();
        apply(order, WorkOrderAction.REJECT_DISPATCH, dto.getReason(), patch -> patch
                        .set(WoWorkOrder::getAssigneeId, null)
                        .set(WoWorkOrder::getRejectDispatchCount, nvl(order.getRejectDispatchCount()) + 1),
                after -> {
                    writeDispatchLog(after, DispatchType.REVOKE, previousAssignee, null, dto.getReason(), true);
                    if (after.getDispatcherId() != null) {
                        notify(after.getDispatcherId(), "工单被拒接",
                                "工单 " + after.getOrderNo() + " 被拒接，原因：" + dto.getReason(), after.getId());
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, DispatchDTO dto) {
        WoWorkOrder order = requireOrder(id);
        if (Objects.equals(order.getAssigneeId(), dto.getAssigneeId())) {
            throw BizException.forbidden("新维修人与当前维修人相同，无需转派");
        }
        SysUser assignee = requireAssignableUser(dto.getAssigneeId());
        Long previousAssignee = order.getAssigneeId();
        apply(order, WorkOrderAction.TRANSFER, dto.getReason(), patch -> patch
                        .set(WoWorkOrder::getAssigneeId, assignee.getId())
                        .set(WoWorkOrder::getDispatcherId, currentUserId())
                        .set(WoWorkOrder::getResponseDueTime, dueTime(order.getLevel(), true)),
                after -> {
                    writeDispatchLog(after, DispatchType.TRANSFER, previousAssignee, assignee.getId(),
                            dto.getReason());
                    notify(assignee.getId(), "工单已转派给你",
                            "工单 " + after.getOrderNo() + " 已转派给你，请及时接单", after.getId());
                    if (previousAssignee != null) {
                        notify(previousAssignee, "工单已转派",
                                "工单 " + after.getOrderNo() + " 已转派给他人", after.getId());
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, ReasonDTO dto) {
        WoWorkOrder order = requireOrder(id);
        if (order.getAcceptTime() != null) {
            throw BizException.forbidden("维修人已接单，不能撤回，请改用转派或取消");
        }
        Long previousAssignee = order.getAssigneeId();
        apply(order, WorkOrderAction.REVOKE, dto.getReason(),
                patch -> patch.set(WoWorkOrder::getAssigneeId, null),
                after -> {
                    writeDispatchLog(after, DispatchType.REVOKE, previousAssignee, null, dto.getReason());
                    if (previousAssignee != null) {
                        notify(previousAssignee, "派单已被撤回",
                                "工单 " + after.getOrderNo() + " 的派单已被撤回", after.getId());
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startRepair(Long id, StartRepairDTO dto) {
        WoWorkOrder order = requireOrder(id);
        if (!WorkOrderStatus.IN_PROGRESS.getCode().equals(order.getStatus())) {
            throw BizException.forbidden("只有维修中的工单可以开始维修");
        }
        Long operatorId = currentUserId();
        requireAssigneeOrAdmin(order, "开始维修");
        if (order.getStartRepairTime() != null) {
            throw new BizException(ResultCode.OPERATION_FORBIDDEN, "该工单已开始维修，请勿重复提交");
        }
        LocalDateTime now = LocalDateTime.now();
        // 以「起始时间为空」做条件更新：两个并发「开始维修」只有一个能成功
        int affected = orderMapper.update(null, Wrappers.<WoWorkOrder>lambdaUpdate()
                .eq(WoWorkOrder::getId, id)
                .isNull(WoWorkOrder::getStartRepairTime)
                .set(WoWorkOrder::getStartRepairTime, now)
                .set(WoWorkOrder::getUpdateBy, operatorId)
                .set(WoWorkOrder::getUpdateTime, now));
        if (affected == 0) {
            throw new BizException(ResultCode.OPERATION_FORBIDDEN, "该工单已开始维修，请勿重复提交");
        }
        // 维修原因 + 故障诊断各落一条记录，时间线上能分开读，不做拼接字符串
        insertRecord(id, RepairRecordType.START, "维修原因：" + dto.getReason(), dto.getBeforeImageIds(),
                null, operatorId);
        insertRecord(id, RepairRecordType.DIAGNOSIS, dto.getDiagnosis(), null, null, operatorId);
        log.info("工单开始维修 orderNo={} operator={}", order.getOrderNo(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitVerify(Long id, SubmitVerifyDTO dto) {
        WoWorkOrder order = requireOrder(id);
        // 先判状态合法性：状态不对时应当报「非法流转（3001）」，而不是先报某个前置条件
        stateMachine.require(WorkOrderStatus.of(order.getStatus()), WorkOrderAction.SUBMIT_VERIFY);
        // 前置条件①：必须先「开始维修」，否则工单没有维修时长，验收无从判断工期
        if (order.getStartRepairTime() == null) {
            throw BizException.forbidden("请先「开始维修」并填写维修原因与故障诊断，再提交验收");
        }
        // 前置条件②：至少 1 条 FINISH 记录
        List<WoRepairRecord> records = repairRecordMapper.selectByOrder(id);
        if (records.stream().noneMatch(r -> RepairRecordType.FINISH.getCode().equals(r.getRecordType()))) {
            throw BizException.forbidden("请先填写完工说明（FINISH 记录）再提交验收");
        }
        // 前置条件③：完工照片（FINISH 记录必须带维修后照片或附件）
        boolean hasPhoto = records.stream()
                .filter(r -> RepairRecordType.FINISH.getCode().equals(r.getRecordType()))
                .anyMatch(r -> StringUtils.hasText(r.getAfterImageIds()) || StringUtils.hasText(r.getAttachmentIds()));
        if (!hasPhoto) {
            throw BizException.forbidden("完工说明需上传至少 1 张维修后照片");
        }
        // 前置条件④：不能存在待审批的备件申请
        int applied = workOrderPartMapper.countApplied(id);
        if (applied > 0) {
            throw BizException.forbidden("存在 " + applied + " 条待审批的备件申请，请先处理后再提交验收");
        }
        // 前置条件⑤：维修总结非空（DTO 已校验，此处兜底）
        if (!StringUtils.hasText(dto.getRepairSummary())) {
            throw new BizException(ResultCode.BAD_REQUEST, "维修总结不能为空");
        }
        apply(order, WorkOrderAction.SUBMIT_VERIFY, null, patch -> patch
                        .set(WoWorkOrder::getSubmitVerifyTime, LocalDateTime.now())
                        .set(WoWorkOrder::getVerifyDueTime, verifyDueTime(order.getLevel()))
                        .set(WoWorkOrder::getRepairSummary, dto.getRepairSummary())
                        .set(WoWorkOrder::getLaborHours, dto.getLaborHours()),
                after -> notify(after.getDispatcherId(), "工单待验收",
                        "工单 " + after.getOrderNo() + " 已提交验收，请安排验收", after.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyPass(Long id, VerifyDTO dto) {
        WoWorkOrder order = requireOrder(id);
        Long operatorId = currentUserId();
        // 禁止自验收：维修人不得验收自己的工单
        if (Objects.equals(order.getAssigneeId(), operatorId)) {
            throw new BizException(ResultCode.WO_SELF_VERIFY_FORBIDDEN);
        }
        if (dto.getQualityScore() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "验收通过时需给出质量评分（1-5）");
        }
        int applied = workOrderPartMapper.countApplied(id);
        if (applied > 0) {
            throw BizException.forbidden("存在 " + applied + " 条待审批的备件申请，请先处理后再验收");
        }
        BigDecimal spareCost = workOrderPartMapper.sumIssuedAmount(id);
        LocalDateTime now = LocalDateTime.now();
        apply(order, WorkOrderAction.VERIFY_PASS, dto.getRemark(), patch -> patch
                        .set(WoWorkOrder::getVerifyUserId, operatorId)
                        .set(WoWorkOrder::getVerifyTime, now)
                        .set(WoWorkOrder::getVerifyResult, VerifyResult.PASS.getCode())
                        .set(WoWorkOrder::getVerifyRemark, dto.getRemark())
                        .set(WoWorkOrder::getQualityScore, dto.getQualityScore())
                        .set(WoWorkOrder::getActualFinishTime, now)
                        .set(WoWorkOrder::getCloseTime, now)
                        .set(WoWorkOrder::getSpareCost, spareCost == null ? BigDecimal.ZERO : spareCost),
                after -> {
                    writeVerifyRecord(after, operatorId, VerifyResult.PASS, dto);
                    // 设备回到在用状态
                    updateEquipmentStatus(after.getEquipmentId(), EquipmentStatus.IN_USE.getCode());
                    notify(after.getAssigneeId(), "工单验收通过",
                            "工单 " + after.getOrderNo() + " 验收通过，辛苦了", after.getId());
                    notify(after.getReporterId(), "工单已完成",
                            "你报修的工单 " + after.getOrderNo() + " 已验收完成，可对本次服务作出评价",
                            after.getId());
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyReject(Long id, VerifyDTO dto) {
        WoWorkOrder order = requireOrder(id);
        Long operatorId = currentUserId();
        if (Objects.equals(order.getAssigneeId(), operatorId)) {
            throw new BizException(ResultCode.WO_SELF_VERIFY_FORBIDDEN);
        }
        apply(order, WorkOrderAction.VERIFY_REJECT, dto.getRemark(), patch -> patch
                        .set(WoWorkOrder::getVerifyUserId, operatorId)
                        .set(WoWorkOrder::getVerifyTime, LocalDateTime.now())
                        .set(WoWorkOrder::getVerifyResult, VerifyResult.REJECT.getCode())
                        .set(WoWorkOrder::getVerifyRemark, dto.getRemark())
                        .set(WoWorkOrder::getRejectCount, nvl(order.getRejectCount()) + 1)
                        .set(WoWorkOrder::getSubmitVerifyTime, null),
                after -> {
                    writeVerifyRecord(after, operatorId, VerifyResult.REJECT, dto);
                    notify(after.getAssigneeId(), "工单验收不通过",
                            "工单 " + after.getOrderNo() + " 验收未通过，请整改：" + dto.getRemark(), after.getId());
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, ReasonDTO dto) {
        WoWorkOrder order = requireOrder(id);
        apply(order, WorkOrderAction.CANCEL, dto.getReason(), patch -> patch
                        .set(WoWorkOrder::getCancelReason, dto.getReason())
                        .set(WoWorkOrder::getCloseTime, LocalDateTime.now()),
                after -> {
                    // 设备状态回滚到建单前
                    if (StringUtils.hasText(after.getPrevEquipmentStatus())) {
                        updateEquipmentStatus(after.getEquipmentId(), after.getPrevEquipmentStatus());
                    }
                    if (after.getAssigneeId() != null) {
                        notify(after.getAssigneeId(), "工单已取消",
                                "工单 " + after.getOrderNo() + " 已取消，原因：" + dto.getReason(), after.getId());
                    }
                    notify(after.getReporterId(), "工单已取消",
                            "工单 " + after.getOrderNo() + " 已取消，原因：" + dto.getReason(), after.getId());
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceTransfer(Long id, DispatchDTO dto) {
        WoWorkOrder order = requireOrder(id);
        if (Objects.equals(order.getAssigneeId(), dto.getAssigneeId())) {
            throw BizException.forbidden("新维修人与当前维修人相同，无需转派");
        }
        SysUser assignee = requireAssignableUser(dto.getAssigneeId());
        Long previousAssignee = order.getAssigneeId();
        apply(order, WorkOrderAction.FORCE_TRANSFER, dto.getReason(),
                patch -> patch.set(WoWorkOrder::getAssigneeId, assignee.getId()), after -> {
                    writeDispatchLog(after, DispatchType.REASSIGN, previousAssignee, assignee.getId(),
                            dto.getReason());
                    notify(assignee.getId(), "工单已强制转派给你",
                            "工单 " + after.getOrderNo() + " 已转派给你，请继续处理", after.getId());
                    if (previousAssignee != null) {
                        notify(previousAssignee, "工单已被强制转派",
                                "工单 " + after.getOrderNo() + " 已由管理员转派给他人", after.getId());
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addRepairRecord(Long id, RepairRecordDTO dto) {
        WoWorkOrder order = requireOrder(id);
        WorkOrderStatus status = WorkOrderStatus.of(order.getStatus());
        if (status != WorkOrderStatus.IN_PROGRESS) {
            throw BizException.forbidden("只有维修中的工单可以添加维修记录");
        }
        Long operatorId = currentUserId();
        requireAssigneeOrAdmin(order, "添加维修记录");
        if (RepairRecordType.START.getCode().equals(dto.getRecordType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "「开始维修」请使用开始维修入口，需同时填写维修原因与故障诊断");
        }
        if (RepairRecordType.FINISH.getCode().equals(dto.getRecordType())
                && !StringUtils.hasText(dto.getAfterImageIds())
                && !StringUtils.hasText(dto.getAttachmentIds())) {
            throw new BizException(ResultCode.BAD_REQUEST, "完工说明需上传至少 1 张维修后照片");
        }
        return insertRecord(id, RepairRecordType.valueOf(dto.getRecordType()), dto.getContent(),
                dto.getBeforeImageIds(), dto.getAfterImageIds(), dto.getCostMinutes(),
                dto.getAttachmentIds(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long evaluate(Long id, EvaluateDTO dto) {
        WoWorkOrder order = requireOrder(id);
        if (!WorkOrderStatus.COMPLETED.getCode().equals(order.getStatus())) {
            throw new BizException(ResultCode.OPERATION_FORBIDDEN, "只有已完成的工单可以评价");
        }
        Long operatorId = currentUserId();
        boolean isReporter = Objects.equals(order.getReporterId(), operatorId);
        boolean isAdmin = SecurityUtils.isAdmin()
                || SecurityUtils.hasRole(WorkOrderStateMachine.ROLE_EQUIP_ADMIN);
        if (!isReporter && !isAdmin) {
            throw BizException.forbidden("只有报修人本人或管理员可以评价该工单");
        }
        if (evaluationMapper.selectByOrder(id) != null) {
            throw new BizException(ResultCode.OPERATION_FORBIDDEN, "该工单已评价，不能重复评价");
        }
        WoEvaluation entity = new WoEvaluation();
        entity.setWorkOrderId(id);
        entity.setEvaluatorId(operatorId);
        entity.setAttitudeScore(dto.getAttitudeScore());
        entity.setQualityScore(dto.getQualityScore());
        entity.setSpeedScore(dto.getSpeedScore());
        entity.setContent(dto.getContent());
        try {
            evaluationMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发下两次提交都通过了上面的查询，唯一索引是最后一道闸
            throw new BizException(ResultCode.OPERATION_FORBIDDEN, "该工单已评价，不能重复评价");
        }
        if (order.getAssigneeId() != null) {
            notify(order.getAssigneeId(), "收到服务评价",
                    "工单 " + order.getOrderNo() + " 收到评价：质量 " + dto.getQualityScore() + " 分", id);
        }
        log.info("工单评价 orderNo={} evaluator={} quality={}", order.getOrderNo(), operatorId,
                dto.getQualityScore());
        return entity.getId();
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 状态机执行模板：校验 → CAS → 轨迹 → 副作用。
     *
     * @param patch 条件更新字段，用 {@code set()} 表达，以便把字段显式置空
     *              （MyBatis-Plus 的实体更新会忽略 null，置空必须走 set）
     */
    private void apply(WoWorkOrder order, WorkOrderAction action, String reason,
                       Consumer<LambdaUpdateWrapper<WoWorkOrder>> patch,
                       Consumer<WoWorkOrder> sideEffect) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        WorkOrderStatus from = WorkOrderStatus.of(order.getStatus());
        if (from == null) {
            throw new BizException(ResultCode.WO_ILLEGAL_TRANSITION,
                    "工单当前状态「" + WorkOrderStatus.labelOf(order.getStatus()) + "」为历史状态，不能再流转");
        }
        WorkOrderTransition transition = stateMachine.require(from, action);
        boolean isAssignee = Objects.equals(order.getAssigneeId(), loginUser.getUserId());
        stateMachine.checkPermission(transition, new HashSet<>(loginUser.getRoles()), isAssignee);
        stateMachine.checkReason(transition, reason);

        Long operatorId = loginUser.getUserId();
        WorkOrderStatus to = transition.to();
        int affected = orderMapper.casUpdateStatus(order.getId(), from.getCode(), to.getCode(),
                order.getVersion(), operatorId);
        if (affected == 0) {
            throw new BizException(ResultCode.WO_STATE_CHANGED);
        }

        // 轨迹：上一状态停留时长
        LocalDateTime lastTime = order.getUpdateTime() == null ? order.getCreateTime() : order.getUpdateTime();
        Integer costMinutes = lastTime == null ? null
                : (int) Math.max(0, Duration.between(lastTime, LocalDateTime.now()).toMinutes());
        WoStatusLog logEntity = new WoStatusLog();
        logEntity.setWorkOrderId(order.getId());
        logEntity.setFromStatus(from.getCode());
        logEntity.setToStatus(to.getCode());
        logEntity.setAction(action.getCode());
        logEntity.setOperatorId(operatorId);
        logEntity.setOperatorRole(String.join(",", loginUser.getRoles()));
        logEntity.setReason(reason);
        logEntity.setCostMinutes(costMinutes);
        statusLogMapper.insert(logEntity);

        // 领域事件：状态流转（outbox 与本事务同提交，投递失败由调度器补投）
        domainEventPublisher.publish(new WorkOrderStatusChangedEvent(order.getId(), order.getOrderNo(),
                order.getTitle(), order.getEquipmentId(), from.getCode(), to.getCode(),
                action.getCode(), operatorId, reason));

        // 副作用字段（显式 set，支持置空）
        if (patch != null) {
            LambdaUpdateWrapper<WoWorkOrder> wrapper = Wrappers.<WoWorkOrder>lambdaUpdate()
                    .eq(WoWorkOrder::getId, order.getId());
            patch.accept(wrapper);
            wrapper.set(WoWorkOrder::getUpdateBy, operatorId)
                    .set(WoWorkOrder::getUpdateTime, LocalDateTime.now());
            orderMapper.update(null, wrapper);
        }

        // 重新读一遍，副作用里拿到的是数据库真实值（含 version、置空的字段）
        WoWorkOrder after = orderMapper.selectById(order.getId());
        if (after != null && sideEffect != null) {
            sideEffect.accept(after);
        }
        log.info("工单状态流转 orderNo={} {} --{}--> {}", order.getOrderNo(),
                from.getCode(), action.getCode(), to.getCode());
    }

    private WoWorkOrder requireOrder(Long id) {
        WoWorkOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("工单");
        }
        return order;
    }

    private SysUser requireAssignableUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("维修人");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("该用户已被禁用，无法派单");
        }
        List<String> roles = userMapper.selectRoleCodes(userId);
        if (roles == null || roles.stream().noneMatch(r ->
                WorkOrderStateMachine.ROLE_REPAIRMAN.equals(r)
                        || WorkOrderStateMachine.ROLE_EQUIP_ADMIN.equals(r))) {
            throw BizException.forbidden("目标用户不具备维修人角色，无法派单");
        }
        return user;
    }

    /** 维修类动作的「本人或管理员」校验，与状态机角色校验互补（状态机只管动作，不管数据归属） */
    private void requireAssigneeOrAdmin(WoWorkOrder order, String actionLabel) {
        Long operatorId = currentUserId();
        boolean isAssignee = Objects.equals(order.getAssigneeId(), operatorId);
        if (!isAssignee && !SecurityUtils.isAdmin()
                && !SecurityUtils.hasRole(WorkOrderStateMachine.ROLE_EQUIP_ADMIN)) {
            throw BizException.forbidden("只有工单维修人可以" + actionLabel);
        }
    }

    private WoWorkOrder buildOrder(EqEquipment equipment, WoSource source, String title, String faultType,
                                   String faultDesc, String level, Long reporterId) {
        WorkOrderLevel levelEnum = WorkOrderLevel.of(level);
        if (levelEnum == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "工单等级取值非法");
        }
        WoWorkOrder order = new WoWorkOrder();
        order.setOrderNo(nextOrderNo());
        order.setTitle(title);
        order.setSource(source.getCode());
        order.setEquipmentId(equipment.getId());
        order.setFaultType(faultType);
        order.setFaultDesc(faultDesc);
        order.setLevel(levelEnum.getCode());
        order.setPriority(WorkOrderPriority.NORMAL.getCode());
        order.setStatus(WorkOrderStatus.PENDING_ASSIGN.getCode());
        order.setReporterId(reporterId);
        order.setReportTime(LocalDateTime.now());
        order.setSpareCost(BigDecimal.ZERO);
        order.setRejectCount(0);
        order.setRejectDispatchCount(0);
        order.setSlaStatus(SlaStatus.NORMAL.getCode());
        order.setOverdueFlag(0);
        order.setPrevEquipmentStatus(equipment.getStatus());
        order.setVersion(0);
        // 响应/修复时限先按等级计算，派单时再按实际派单时间重算
        order.setResponseDueTime(LocalDateTime.now().plusMinutes(slaMinutes(level, true)));
        order.setResolveDueTime(LocalDateTime.now().plusMinutes(slaMinutes(level, false)));
        return order;
    }

    private void writeCreateLog(WoWorkOrder order, Long operatorId) {
        WoStatusLog logEntity = new WoStatusLog();
        logEntity.setWorkOrderId(order.getId());
        logEntity.setFromStatus(null);
        logEntity.setToStatus(WorkOrderStatus.PENDING_ASSIGN.getCode());
        logEntity.setAction("CREATE");
        logEntity.setOperatorId(operatorId);
        logEntity.setReason("创建工单，来源：" + order.getSource());
        logEntity.setCostMinutes(0);
        statusLogMapper.insert(logEntity);

        // 领域事件：工单创建（人工/扫码/巡检转单统一入口）
        domainEventPublisher.publish(new WorkOrderCreatedEvent(order.getId(), order.getOrderNo(),
                order.getTitle(), order.getEquipmentId(), order.getReporterId(), order.getSource()));
    }

    private Long insertRecord(Long orderId, RepairRecordType type, String content, String beforeImages,
                              String afterImages, Long operatorId) {
        return insertRecord(orderId, type, content, beforeImages, afterImages, null, null, operatorId);
    }

    private Long insertRecord(Long orderId, RepairRecordType type, String content, String beforeImages,
                              String afterImages, Integer costMinutes, String attachmentIds, Long operatorId) {
        WoRepairRecord record = new WoRepairRecord();
        record.setWorkOrderId(orderId);
        record.setRecordType(type.getCode());
        record.setContent(content);
        record.setBeforeImageIds(beforeImages);
        record.setAfterImageIds(afterImages);
        record.setCostMinutes(costMinutes);
        record.setAttachmentIds(attachmentIds);
        record.setOperatorId(operatorId);
        repairRecordMapper.insert(record);
        return record.getId();
    }

    private void writeDispatchLog(WoWorkOrder order, DispatchType type, Long fromUserId, Long toUserId,
                                  String remark) {
        writeDispatchLog(order, type, fromUserId, toUserId, remark, false);
    }

    private void writeDispatchLog(WoWorkOrder order, DispatchType type, Long fromUserId, Long toUserId,
                                  String remark, boolean rejected) {
        WoDispatchLog entity = new WoDispatchLog();
        entity.setWorkOrderId(order.getId());
        entity.setDispatchType(type.getCode());
        entity.setFromUserId(fromUserId);
        entity.setToUserId(toUserId);
        entity.setDispatcherId(currentUserId());
        entity.setDispatchTime(LocalDateTime.now());
        entity.setRejectFlag(rejected ? 1 : 0);
        entity.setRejectReason(rejected ? remark : null);
        entity.setRemark(remark);
        dispatchLogMapper.insert(entity);
    }

    private void writeVerifyRecord(WoWorkOrder order, Long verifyUserId, VerifyResult result, VerifyDTO dto) {
        WoVerifyRecord entity = new WoVerifyRecord();
        entity.setWorkOrderId(order.getId());
        entity.setVerifyUserId(verifyUserId);
        entity.setVerifyResult(result.getCode());
        entity.setQualityScore(dto.getQualityScore());
        entity.setRemark(dto.getRemark());
        entity.setAttachmentIds(dto.getAttachmentIds());
        entity.setVerifyTime(LocalDateTime.now());
        verifyRecordMapper.insert(entity);
    }

    private void updateEquipmentStatus(Long equipmentId, String status) {
        if (equipmentId == null || !StringUtils.hasText(status)) {
            return;
        }
        EqEquipment patch = new EqEquipment();
        patch.setId(equipmentId);
        patch.setStatus(status);
        // 设备状态由工单驱动，此处不加版本条件，避免与人工状态变更互相打回
        equipmentMapper.update(patch, Wrappers.<EqEquipment>lambdaUpdate()
                .eq(EqEquipment::getId, equipmentId));
    }

    /**
     * 时限以 sla_policy 表为准（第六阶段起接管，支持在线调整）；
     * 表缺行、停用或数值非法时兜底 {@link WorkOrderLevel} 枚举值。
     */
    private LocalDateTime dueTime(String level, boolean response) {
        return LocalDateTime.now().plusMinutes(slaMinutes(level, response));
    }

    /** 验收时限：提交验收 → 必须完成验收的窗口 */
    private LocalDateTime verifyDueTime(String level) {
        return LocalDateTime.now().plusMinutes(slaVerifyMinutes(level));
    }

    private int slaMinutes(String level, boolean response) {
        int fallback = fallbackMinutes(level, response);
        SlaPolicy policy = slaPolicyMapper.selectEnabledByLevel(level);
        Integer minutes = policy == null ? null
                : (response ? policy.getResponseMinutes() : policy.getResolveMinutes());
        return minutes == null || minutes <= 0 ? fallback : minutes;
    }

    private int slaVerifyMinutes(String level) {
        SlaPolicy policy = slaPolicyMapper.selectEnabledByLevel(level);
        int fallback = 240;
        if (policy == null || policy.getVerifyMinutes() == null || policy.getVerifyMinutes() <= 0) {
            return fallback;
        }
        return policy.getVerifyMinutes();
    }

    private int fallbackMinutes(String level, boolean response) {
        WorkOrderLevel levelEnum = WorkOrderLevel.of(level);
        if (levelEnum == null) {
            levelEnum = WorkOrderLevel.P3;
        }
        return response ? levelEnum.getResponseMinutes() : levelEnum.getResolveMinutes();
    }

    private String nextOrderNo() {
        String prefix = "WO" + LocalDateTime.now().format(DAY_FORMAT);
        String max = orderMapper.selectMaxOrderNoByPrefix(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 编号被人工改过，按 1 重新计数
            }
        }
        return prefix + String.format("%04d", next);
    }

    /**
     * 建单插入：单号由「查最大值+1」生成，并发报修可能撞 uk_wo_order_no 唯一键。
     * 撞键时重新取号重试（最多 3 次），避免用户报修莫名 409（阶段十二整改 Q-05）。
     */
    private void insertWithOrderNoRetry(WoWorkOrder order) {
        for (int attempt = 0; ; attempt++) {
            try {
                orderMapper.insert(order);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (attempt >= 2) {
                    throw e;
                }
                log.warn("工单号并发冲突，重新取号重试 attempt={} old={}", attempt + 1, order.getOrderNo());
                order.setOrderNo(nextOrderNo());
            }
        }
    }

    /**
     * 计算当前用户在该工单上允许的动作，供前端渲染按钮。
     */
    private List<String> allowedActionsFor(WorkOrderStatus status, LoginUser loginUser, WorkOrderVO vo) {
        List<String> actions = new ArrayList<>();
        if (status == null) {
            return actions;
        }
        boolean isAssignee = vo.getAssigneeId() != null && vo.getAssigneeId().equals(loginUser.getUserId());
        for (WorkOrderAction action : stateMachine.allowedActions(status)) {
            WorkOrderTransition t = stateMachine.resolve(status, action).orElse(null);
            if (t == null) {
                continue;
            }
            if (t.assigneeOnly() && !isAssignee) {
                continue;
            }
            if (!t.allowedRoles().isEmpty()
                    && loginUser.getRoles().stream().noneMatch(t.allowedRoles()::contains)) {
                continue;
            }
            actions.add(action.getCode());
        }
        return actions;
    }

    private void fillBase(WorkOrderVO vo, WoWorkOrder order) {
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTitle(order.getTitle());
        vo.setSource(order.getSource());
        vo.setEquipmentId(order.getEquipmentId());
        vo.setFaultType(order.getFaultType());
        vo.setFaultDesc(order.getFaultDesc());
        vo.setLevel(order.getLevel());
        vo.setPriority(order.getPriority());
        vo.setPriorityLabel(WorkOrderPriority.labelOf(order.getPriority()));
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(WorkOrderStatus.labelOf(order.getStatus()));
        vo.setReporterId(order.getReporterId());
        vo.setAssigneeId(order.getAssigneeId());
        vo.setReportTime(order.getReportTime());
        vo.setDispatchTime(order.getDispatchTime());
        vo.setAcceptTime(order.getAcceptTime());
        vo.setResponseDueTime(order.getResponseDueTime());
        vo.setResolveDueTime(order.getResolveDueTime());
        vo.setSlaStatus(order.getSlaStatus());
        vo.setOverdueFlag(order.getOverdueFlag());
        vo.setRejectCount(order.getRejectCount());
        vo.setRejectDispatchCount(order.getRejectDispatchCount());
        vo.setCloseTime(order.getCloseTime());
        vo.setSpareCost(order.getSpareCost());
        if (order.getReporterId() != null) {
            vo.setReporterName(realName(order.getReporterId()));
        }
        if (order.getAssigneeId() != null) {
            vo.setAssigneeName(realName(order.getAssigneeId()));
        }
        EqEquipment equipment = equipmentMapper.selectById(order.getEquipmentId());
        if (equipment != null) {
            vo.setEquipmentName(equipment.getEquipmentName());
            vo.setAssetNo(equipment.getAssetNo());
        }
    }

    private void decorate(WorkOrderVO vo, LoginUser loginUser) {
        vo.setStatusLabel(WorkOrderStatus.labelOf(vo.getStatus()));
        vo.setPriorityLabel(WorkOrderPriority.labelOf(vo.getPriority()));
        vo.setAllowedActions(allowedActionsFor(WorkOrderStatus.of(vo.getStatus()), loginUser, vo));
    }

    private List<WorkOrderVO> fetch(int size, Consumer<WorkOrderQuery> customizer, boolean priorityFirst) {
        WorkOrderQuery query = new WorkOrderQuery();
        query.setPageNum(1);
        query.setPageSize(size);
        query.setPriorityFirst(priorityFirst);
        customizer.accept(query);
        LoginUser loginUser = SecurityUtils.getLoginUser();
        query.setCurrentUserId(loginUser.getUserId());
        IPage<WorkOrderVO> page = orderMapper.selectWorkOrderPage(new Page<>(1, size), query);
        page.getRecords().forEach(vo -> decorate(vo, loginUser));
        return page.getRecords();
    }

    private void fillTimelineNodes(WorkOrderDetailVO vo, WoWorkOrder order) {
        for (WoStatusLog item : statusLogMapper.selectTimeline(order.getId())) {
            WorkOrderTimelineVO node = new WorkOrderTimelineVO();
            node.setFromStatus(item.getFromStatus());
            node.setFromStatusLabel(item.getFromStatus() == null ? null
                    : WorkOrderStatus.labelOf(item.getFromStatus()));
            node.setToStatus(item.getToStatus());
            node.setToStatusLabel(WorkOrderStatus.labelOf(item.getToStatus()));
            node.setAction(item.getAction());
            WorkOrderAction action = WorkOrderAction.of(item.getAction());
            node.setActionLabel(action == null ? item.getAction() : action.getLabel());
            node.setOperatorId(item.getOperatorId());
            node.setOperatorName(item.getOperatorId() == null ? null : realName(item.getOperatorId()));
            node.setOperatorRole(item.getOperatorRole());
            node.setReason(item.getReason());
            node.setCostMinutes(item.getCostMinutes());
            node.setCreateTime(item.getCreateTime());
            vo.getTimeline().add(node);
        }
    }

    private void fillRepairRecords(WorkOrderDetailVO vo, Long orderId) {
        for (WoRepairRecord record : repairRecordMapper.selectByOrder(orderId)) {
            RepairRecordVO r = new RepairRecordVO();
            r.setId(record.getId());
            r.setRecordType(record.getRecordType());
            r.setRecordTypeLabel(RepairRecordType.labelOf(record.getRecordType()));
            r.setContent(record.getContent());
            r.setBeforeImageIds(record.getBeforeImageIds());
            r.setAfterImageIds(record.getAfterImageIds());
            if (StringUtils.hasText(record.getBeforeImageIds())) {
                r.setBeforeImages(inspectionSourceMapper.selectAttachments(record.getBeforeImageIds()));
            }
            if (StringUtils.hasText(record.getAfterImageIds())) {
                r.setAfterImages(inspectionSourceMapper.selectAttachments(record.getAfterImageIds()));
            }
            r.setCostMinutes(record.getCostMinutes());
            r.setAttachmentIds(record.getAttachmentIds());
            r.setOperatorId(record.getOperatorId());
            r.setOperatorName(record.getOperatorId() == null ? null : realName(record.getOperatorId()));
            r.setCreateTime(record.getCreateTime());
            vo.getRepairRecords().add(r);
        }
    }

    private void fillVerifyRecords(WorkOrderDetailVO vo, Long orderId) {
        for (WoVerifyRecord record : verifyRecordMapper.selectByOrder(orderId)) {
            VerifyRecordVO r = new VerifyRecordVO();
            r.setId(record.getId());
            r.setVerifyUserId(record.getVerifyUserId());
            r.setVerifyUserName(record.getVerifyUserId() == null ? null : realName(record.getVerifyUserId()));
            r.setVerifyResult(record.getVerifyResult());
            r.setQualityScore(record.getQualityScore());
            r.setRemark(record.getRemark());
            r.setAttachmentIds(record.getAttachmentIds());
            r.setVerifyTime(record.getVerifyTime());
            vo.getVerifyRecords().add(r);
        }
    }

    private void fillDispatchRecords(WorkOrderDetailVO vo, Long orderId) {
        for (WoDispatchLog record : dispatchLogMapper.selectByOrder(orderId)) {
            DispatchRecordVO r = new DispatchRecordVO();
            r.setId(record.getId());
            r.setDispatchType(record.getDispatchType());
            r.setFromUserId(record.getFromUserId());
            r.setFromUserName(record.getFromUserId() == null ? null : realName(record.getFromUserId()));
            r.setToUserId(record.getToUserId());
            r.setToUserName(record.getToUserId() == null ? null : realName(record.getToUserId()));
            r.setDispatcherId(record.getDispatcherId());
            r.setDispatcherName(record.getDispatcherId() == null ? null : realName(record.getDispatcherId()));
            r.setDispatchTime(record.getDispatchTime());
            r.setAcceptTime(record.getAcceptTime());
            r.setRejectFlag(record.getRejectFlag());
            r.setRejectReason(record.getRejectReason());
            r.setRemark(record.getRemark());
            vo.getDispatchRecords().add(r);
        }
    }

    /**
     * 归并六类原始记录为一条时间线。
     *
     * <p>不新建「时间线表」：每一类记录本就存在自己的表里，再存一份必然会出现
     * 「时间线有、源记录没有」或反之的不一致。这里只做只读聚合，
     * 每条都带 sourceType + sourceId，可回查原记录。
     */
    private List<WorkOrderTimelineItemVO> buildTimeline(WoWorkOrder order) {
        Long orderId = order.getId();
        List<WoStatusLog> statusLogs = statusLogMapper.selectTimeline(orderId);
        List<WoRepairRecord> records = repairRecordMapper.selectByOrder(orderId);
        List<WoDispatchLog> dispatches = dispatchLogMapper.selectByOrder(orderId);
        List<WoVerifyRecord> verifies = verifyRecordMapper.selectByOrder(orderId);
        List<WorkOrderPartVO> parts = partQueryMapper.selectByOrder(orderId);
        WoEvaluation evaluation = evaluationMapper.selectByOrder(orderId);

        Set<Long> userIds = new HashSet<>();
        statusLogs.forEach(l -> userIds.add(l.getOperatorId()));
        records.forEach(r -> userIds.add(r.getOperatorId()));
        dispatches.forEach(d -> {
            userIds.add(d.getDispatcherId());
            userIds.add(d.getFromUserId());
            userIds.add(d.getToUserId());
        });
        verifies.forEach(v -> userIds.add(v.getVerifyUserId()));
        parts.forEach(p -> {
            userIds.add(p.getApplyUserId());
            userIds.add(p.getAuditUserId());
        });
        if (evaluation != null) {
            userIds.add(evaluation.getEvaluatorId());
        }
        userIds.remove(null);
        Map<Long, String> names = loadNames(userIds);

        List<WorkOrderTimelineItemVO> items = new ArrayList<>();

        for (WoStatusLog l : statusLogs) {
            WorkOrderTimelineItemVO item = base("STATUS", l.getCreateTime(), l.getOperatorId(), names);
            item.setSourceType("wo_status_log");
            item.setSourceId(l.getId());
            item.setOperatorRole(l.getOperatorRole());
            item.setCostMinutes(l.getCostMinutes());
            if (l.getFromStatus() == null) {
                item.setEventType("CREATE");
                item.setTitle("创建工单");
                item.setDetail(l.getReason());
            } else {
                WorkOrderAction action = WorkOrderAction.of(l.getAction());
                item.setTitle(action == null ? l.getAction() : action.getLabel());
                item.setFromStatus(l.getFromStatus());
                item.setFromStatusLabel(WorkOrderStatus.labelOf(l.getFromStatus()));
                item.setToStatus(l.getToStatus());
                item.setToStatusLabel(WorkOrderStatus.labelOf(l.getToStatus()));
                item.setDetail(l.getReason());
            }
            items.add(item);
        }

        for (WoDispatchLog d : dispatches) {
            WorkOrderTimelineItemVO item = base("DISPATCH", d.getDispatchTime(), d.getDispatcherId(), names);
            item.setSourceType("wo_dispatch_log");
            item.setSourceId(d.getId());
            String typeLabel = dispatchTypeLabel(d.getDispatchType());
            boolean rejected = d.getRejectFlag() != null && d.getRejectFlag() == 1;
            item.setTitle(rejected ? "拒接" : typeLabel);
            String toName = d.getToUserId() == null ? "—" : names.getOrDefault(d.getToUserId(), "用户" + d.getToUserId());
            String detail = "维修人：" + toName
                    + (StringUtils.hasText(d.getRemark()) ? "；说明：" + d.getRemark() : "");
            if (d.getRejectFlag() != null && d.getRejectFlag() == 1 && StringUtils.hasText(d.getRejectReason())) {
                detail = "拒接原因：" + d.getRejectReason();
            }            item.setDetail(detail);
            items.add(item);
        }

        for (WoRepairRecord r : records) {
            WorkOrderTimelineItemVO item = base("REPAIR", r.getCreateTime(), r.getOperatorId(), names);
            item.setSourceType("wo_repair_record");
            item.setSourceId(r.getId());
            item.setTitle(RepairRecordType.labelOf(r.getRecordType()));
            item.setDetail(r.getContent());
            item.setCostMinutes(r.getCostMinutes());
            List<SourceAttachmentVO> attachments = new ArrayList<>();
            if (StringUtils.hasText(r.getBeforeImageIds())) {
                attachments.addAll(inspectionSourceMapper.selectAttachments(r.getBeforeImageIds()));
            }
            if (StringUtils.hasText(r.getAfterImageIds())) {
                attachments.addAll(inspectionSourceMapper.selectAttachments(r.getAfterImageIds()));
            }
            item.setAttachments(attachments);
            items.add(item);
        }

        for (WoVerifyRecord v : verifies) {
            WorkOrderTimelineItemVO item = base("VERIFY", v.getVerifyTime(), v.getVerifyUserId(), names);
            item.setSourceType("wo_verify_record");
            item.setSourceId(v.getId());
            boolean pass = VerifyResult.PASS.getCode().equals(v.getVerifyResult());
            item.setTitle(pass ? "验收通过" : "验收不通过");
            item.setDetail((pass ? "" : "不通过原因：") + (v.getRemark() == null ? "—" : v.getRemark()));
            items.add(item);
        }

        for (WorkOrderPartVO p : parts) {
            WorkOrderTimelineItemVO apply = base("PART", p.getApplyTime(), p.getApplyUserId(), names);
            apply.setSourceType("sp_work_order_part");
            apply.setSourceId(p.getId());
            apply.setTitle("申请备件");
            apply.setDetail(p.getPartName() + " × " + p.getQuantity() + (p.getUnit() == null ? "" : p.getUnit())
                    + "（" + partStatusLabel(p.getIssueStatus()) + "）");
            items.add(apply);
            if (p.getIssueTime() != null) {
                WorkOrderTimelineItemVO issue = base("PART", p.getIssueTime(), p.getAuditUserId(), names);
                issue.setSourceType("sp_work_order_part");
                issue.setSourceId(p.getId());
                issue.setTitle("备件出库");
                issue.setDetail(p.getPartName() + " × " + p.getQuantity()
                        + "，金额 " + (p.getAmount() == null ? "0" : p.getAmount().toPlainString()));
                items.add(issue);
            }
        }

        if (evaluation != null) {
            WorkOrderTimelineItemVO item = base("EVALUATION", evaluation.getCreateTime(),
                    evaluation.getEvaluatorId(), names);
            item.setSourceType("wo_evaluation");
            item.setSourceId(evaluation.getId());
            item.setTitle("服务评价");
            item.setDetail("态度 " + evaluation.getAttitudeScore() + " 分 / 质量 " + evaluation.getQualityScore()
                    + " 分 / 速度 " + evaluation.getSpeedScore() + " 分"
                    + (StringUtils.hasText(evaluation.getContent()) ? "；" + evaluation.getContent() : ""));
            items.add(item);
        }

        // 稳定排序：同一时刻保持「状态→派单→维修→验收→备件→评价」的插入顺序
        items.sort(Comparator.comparing(WorkOrderTimelineItemVO::getEventTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    private WorkOrderTimelineItemVO base(String eventType, LocalDateTime time, Long operatorId,
                                         Map<Long, String> names) {
        WorkOrderTimelineItemVO item = new WorkOrderTimelineItemVO();
        item.setEventType(eventType);
        item.setEventTime(time);
        item.setOperatorId(operatorId);
        item.setOperatorName(operatorId == null ? null : names.getOrDefault(operatorId, "用户" + operatorId));
        return item;
    }

    private Map<Long, String> loadNames(Set<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            names.put(user.getId(), user.getRealName());
        }
        return names;
    }

    private WorkOrderEvaluationVO toEvaluationVO(WoEvaluation entity) {
        WorkOrderEvaluationVO vo = new WorkOrderEvaluationVO();
        vo.setId(entity.getId());
        vo.setEvaluatorId(entity.getEvaluatorId());
        vo.setEvaluatorName(entity.getEvaluatorId() == null ? null : realName(entity.getEvaluatorId()));
        vo.setAttitudeScore(entity.getAttitudeScore());
        vo.setQualityScore(entity.getQualityScore());
        vo.setSpeedScore(entity.getSpeedScore());
        int sum = nvl(entity.getAttitudeScore()) + nvl(entity.getQualityScore()) + nvl(entity.getSpeedScore());
        vo.setAverageScore(Math.round(sum / 3.0 * 10) / 10.0);
        vo.setContent(entity.getContent());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private static String dispatchTypeLabel(String code) {
        if (code == null) {
            return "派单";
        }
        for (DispatchType t : DispatchType.values()) {
            if (t.getCode().equals(code)) {
                return t.getLabel();
            }
        }
        return code;
    }

    private static String partStatusLabel(String code) {
        if (code == null) {
            return "—";
        }
        return switch (code) {
            case "APPLIED" -> "待审批";
            case "ISSUED" -> "已出库";
            case "REJECTED" -> "已驳回";
            case "RETURNED" -> "已退料";
            default -> code;
        };
    }

    /** 现场照片不复制进工单：按异常记录上的附件 id 回查 sys_file */
    private void fillSourceAttachments(InspectionSourceVO source) {
        if (StringUtils.hasText(source.getAttachmentIds())) {
            source.setAttachments(inspectionSourceMapper.selectAttachments(source.getAttachmentIds()));
        }
    }

    private String realName(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    private void notify(Long receiverId, String title, String content, Long orderId) {
        if (receiverId == null) {
            return;
        }
        try {
            messageService.send(receiverId, title, content, MSG_TYPE_WORK_ORDER, BIZ_WORK_ORDER, orderId);
        } catch (RuntimeException e) {
            // 通知失败不影响主流程：工单状态已经落库
            log.warn("工单通知发送失败 receiverId={} orderId={}", receiverId, orderId, e);
        }
    }

    /**
     * 按角色广播通知（第七阶段「新工单通知」）。
     *
     * <p>用于把新建工单推送给可派单的管理角色（ADMIN / EQUIP_ADMIN），
     * 角色内全员推送（含发起人——派单方同样需要跟进待派工单）；
     * 发起人另收「报修已提交」，两者语义不同不构成重复。
     * 通知失败只记日志，不回滚工单。
     */
    private void notifyRoles(List<String> roleCodes, String title, String content, Long orderId) {
        try {
            Set<Long> receivers = new HashSet<>();
            for (String roleCode : roleCodes) {
                receivers.addAll(userMapper.selectUserIdsByRoleCode(roleCode));
            }
            for (Long receiverId : receivers) {
                notify(receiverId, title, content, orderId);
            }
        } catch (RuntimeException e) {
            log.warn("按角色发送工单通知失败 roles={} orderId={}", roleCodes, orderId, e);
        }
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
