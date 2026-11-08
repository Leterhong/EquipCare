package com.equipcare.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.ai.client.OpenAiCompatClient;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.security.LoginUser;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.workorder.WorkOrderAccessPolicy;
import com.equipcare.analytics.entity.EqHealthSnapshot;
import com.equipcare.analytics.mapper.EqHealthSnapshotMapper;
import com.equipcare.asset.entity.EqCategory;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.mapper.EqCategoryMapper;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.workorder.entity.WoEvaluation;
import com.equipcare.workorder.entity.WoRepairRecord;
import com.equipcare.workorder.entity.WoStatusLog;
import com.equipcare.workorder.entity.WoWorkOrder;
import com.equipcare.workorder.enums.WorkOrderLevel;
import com.equipcare.workorder.enums.WorkOrderStatus;
import com.equipcare.workorder.mapper.WoEvaluationMapper;
import com.equipcare.workorder.mapper.WoRepairRecordMapper;
import com.equipcare.workorder.mapper.WoStatusLogMapper;
import com.equipcare.workorder.mapper.WoWorkOrderMapper;
import com.equipcare.workorder.vo.WorkOrderPartVO;
import com.equipcare.workorder.mapper.WoPartQueryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 上下文组装：把既有业务数据（工单/轨迹/维修记录/备件/评价/设备/健康分/维修工负荷）
 * 压缩成供大模型阅读的中文文本上下文。只读，不写任何业务表。
 *
 * <p>提示词构建（buildXxxPrompt）为纯静态函数，便于单元测试断言关键事实在列。
 */
@Service
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiContextService {

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WoWorkOrderMapper orderMapper;
    private final WoStatusLogMapper statusLogMapper;
    private final WoRepairRecordMapper repairRecordMapper;
    private final WoEvaluationMapper evaluationMapper;
    private final WoPartQueryMapper partQueryMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final EqCategoryMapper categoryMapper;
    private final EqHealthSnapshotMapper healthSnapshotMapper;
    private final SysUserMapper userMapper;

    public AiContextService(WoWorkOrderMapper orderMapper, WoStatusLogMapper statusLogMapper,
                            WoRepairRecordMapper repairRecordMapper, WoEvaluationMapper evaluationMapper,
                            WoPartQueryMapper partQueryMapper, EqEquipmentMapper equipmentMapper,
                            EqCategoryMapper categoryMapper, EqHealthSnapshotMapper healthSnapshotMapper,
                            SysUserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
        this.repairRecordMapper = repairRecordMapper;
        this.evaluationMapper = evaluationMapper;
        this.partQueryMapper = partQueryMapper;
        this.equipmentMapper = equipmentMapper;
        this.categoryMapper = categoryMapper;
        this.healthSnapshotMapper = healthSnapshotMapper;
        this.userMapper = userMapper;
    }

    // ==================================================================
    // 故障诊断上下文
    // ==================================================================

    /** 诊断上下文：工单本体 + 设备档案 + 健康分 + 状态轨迹 + 历史维修记录 */
    public String buildDiagnosisContext(long workOrderId) {
        WoWorkOrder order = requireOrder(workOrderId);
        EqEquipment equipment = equipmentMapper.selectById(order.getEquipmentId());
        StringBuilder sb = new StringBuilder();
        sb.append("## 工单信息\n");
        sb.append("- 工单号：").append(order.getOrderNo())
                .append("，标题：").append(nz(order.getTitle())).append('\n');
        sb.append("- 设备故障类型：").append(nz(order.getFaultType()))
                .append("，等级：").append(levelLabel(order.getLevel())).append('\n');
        sb.append("- 故障描述：").append(nz(order.getFaultDesc())).append('\n');
        sb.append("- 当前状态：").append(WorkOrderStatus.labelOf(order.getStatus()))
                .append("，SLA：").append(nz(order.getSlaStatus()))
                .append("，超时标记：").append(order.getOverdueFlag() != null && order.getOverdueFlag() == 1 ? "已超时" : "未超时")
                .append('\n');
        if (equipment != null) {
            sb.append("\n## 设备档案\n");
            sb.append("- 设备：").append(nz(equipment.getEquipmentName()))
                    .append("（资产编号 ").append(nz(equipment.getAssetNo())).append("）\n");
            EqCategory category = equipment.getCategoryId() == null ? null
                    : categoryMapper.selectById(equipment.getCategoryId());
            if (category != null) {
                sb.append("- 类别：").append(nz(category.getCategoryName())).append('\n');
            }
            sb.append("- 品牌型号：").append(nz(equipment.getBrand())).append(" ").append(nz(equipment.getModel()))
                    .append("，状态：").append(nz(equipment.getStatus())).append('\n');
            EqHealthSnapshot snap = latestSnapshot(equipment.getId());
            if (snap != null) {
                sb.append("- 最近健康分：").append(snap.getHealthScore()).append(" 分（").append(snap.getStatDate())
                        .append("，等级 ").append(nz(snap.getLevel()))
                        .append("，30天工单 ").append(snap.getWorkOrderCount())
                        .append(" 单 / 异常 ").append(snap.getAnomalyCount()).append(" 项）\n");
                if (snap.getRiskDetail() != null && !snap.getRiskDetail().isBlank()) {
                    sb.append("- 扣分明细：").append(snap.getRiskDetail()).append('\n');
                }
            }
        }
        sb.append("\n## 状态轨迹\n");
        List<WoStatusLog> timeline = statusLogMapper.selectTimeline(workOrderId);
        for (WoStatusLog log : timeline) {
            sb.append("- ").append(log.getCreateTime() == null ? "" : log.getCreateTime().format(T))
                    .append(" ").append(label(log.getFromStatus())).append(" → ").append(label(log.getToStatus()))
                    .append("（").append(nz(log.getAction())).append("）")
                    .append(log.getReason() == null ? "" : " 原因：" + log.getReason())
                    .append('\n');
        }
        List<WoRepairRecord> repairs = repairRecordMapper.selectByOrder(workOrderId);
        if (!repairs.isEmpty()) {
            sb.append("\n## 本次已录入的维修记录\n");
            for (WoRepairRecord r : repairs) {
                sb.append("- ").append(r.getCreateTime() == null ? "" : r.getCreateTime().format(T))
                        .append("【").append(nz(r.getRecordType())).append("】").append(nz(r.getContent())).append('\n');
            }
        }
        List<WorkOrderPartVO> parts = partQueryMapper.selectByOrder(workOrderId);
        if (!parts.isEmpty()) {
            sb.append("\n## 已领用备件\n");
            for (WorkOrderPartVO p : parts) {
                sb.append("- ").append(nz(p.getPartName())).append("（").append(nz(p.getPartNo()))
                        .append("）× ").append(p.getQuantity()).append('\n');
            }
        }
        sb.append("\n## 同设备历史维修工单（最近 5 张，供规律参考）\n");
        for (WoWorkOrder past : pastOrders(order.getEquipmentId(), workOrderId, 5)) {
            sb.append("- ").append(past.getOrderNo()).append("【").append(nz(past.getFaultType())).append("】")
                    .append(nz(past.getFaultDesc()))
                    .append("（").append(WorkOrderStatus.labelOf(past.getStatus())).append("）\n");
        }
        return sb.toString();
    }

    public List<OpenAiCompatClient.ChatMessage> buildDiagnosisPrompt(String context) {
        return List.of(
                OpenAiCompatClient.ChatMessage.system("""
                        你是资深的设备维修专家助手，服务于工厂设备巡检与维修管理平台。请基于给定的工单与设备数据输出维修诊断建议，要求：
                        1. 使用简体中文，输出 Markdown；
                        2. 结构固定为：## 可能的根因分析（按可能性排序，每条注明依据）、## 建议的维修步骤（编号列表，具体可执行）、## 需要准备的备件与工具、## 安全注意事项；
                        3. 只依据给定数据推断，不确定处明确标注「推测」；不得编造设备不存在的部件；
                        4. 简洁专业，总长不超过 600 字。"""),
                OpenAiCompatClient.ChatMessage.user("以下是工单与设备数据：\n\n" + context));
    }

    // ==================================================================
    // 智能派单上下文与候选
    // ==================================================================

    /** 派单候选（算法预打分 + LLM 写推荐理由） */
    public record DispatchCandidate(long userId, String realName,
                                    int categoryRepaired, int totalRepaired, int activeLoad, double score) {
    }

    public record DispatchContext(long workOrderId, String orderNo, String title, String faultType,
                                  String categoryName, List<DispatchCandidate> candidates) {
    }

    /** 组装派单上下文：候选维修工 = REPAIRMAN / EQUIP_ADMIN 角色，按 历史同类别修复数×2 + 总修复数 − 活跃负荷×1.5 预打分 */
    public DispatchContext buildDispatchContext(long workOrderId) {
        WoWorkOrder order = requireOrder(workOrderId);
        EqEquipment equipment = order.getEquipmentId() == null ? null
                : equipmentMapper.selectById(order.getEquipmentId());
        Long categoryId = equipment == null ? null : equipment.getCategoryId();

        Set<Long> userIds = new LinkedHashSet<>();
        for (String role : List.of("REPAIRMAN", "EQUIP_ADMIN")) {
            List<Long> ids = userMapper.selectUserIdsByRoleCode(role);
            if (ids != null) {
                userIds.addAll(ids);
            }
        }
        List<WoWorkOrder> allByAssignee = userIds.isEmpty() ? List.of()
                : orderMapper.selectList(Wrappers.<WoWorkOrder>lambdaQuery()
                .in(WoWorkOrder::getAssigneeId, userIds));
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (Long userId : userIds) {
            SysUser user = userMapper.selectById(userId);
            String realName = user == null ? ("用户" + userId) : nz(user.getRealName());
            int totalRepaired = 0;
            int categoryRepaired = 0;
            int activeLoad = 0;
            for (WoWorkOrder o : allByAssignee) {
                if (!userId.equals(o.getAssigneeId())) {
                    continue;
                }
                WorkOrderStatus st = WorkOrderStatus.of(o.getStatus());
                if (st == WorkOrderStatus.COMPLETED || st == WorkOrderStatus.PENDING_REVIEW) {
                    totalRepaired++;
                    if (categoryId != null && categoryId.equals(orderCategoryId(o.getEquipmentId()))) {
                        categoryRepaired++;
                    }
                } else if (st == WorkOrderStatus.PENDING_ACCEPT || st == WorkOrderStatus.IN_PROGRESS) {
                    activeLoad++;
                }
            }
            double score = categoryRepaired * 2.0 + totalRepaired - activeLoad * 1.5;
            candidates.add(new DispatchCandidate(userId, realName, categoryRepaired, totalRepaired, activeLoad, score));
        }
        candidates.sort(Comparator.comparingDouble(DispatchCandidate::score).reversed());
        String categoryName = categoryId == null ? "未知" : nz(categoryMapper.selectById(categoryId) == null
                ? null : categoryMapper.selectById(categoryId).getCategoryName());
        return new DispatchContext(workOrderId, order.getOrderNo(), nz(order.getTitle()),
                nz(order.getFaultType()), categoryName, candidates.size() > 6
                ? candidates.subList(0, 6) : candidates);
    }

    public List<OpenAiCompatClient.ChatMessage> buildDispatchPrompt(DispatchContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 待派工单\n- ").append(ctx.orderNo()).append("【").append(ctx.faultType())
                .append("】").append(ctx.title()).append("，设备类别：").append(ctx.categoryName()).append('\n');
        sb.append("\n## 候选维修工（算法预打分降序）\n");
        for (DispatchCandidate c : ctx.candidates()) {
            sb.append("- ").append(c.realName()).append("：同类别修复 ").append(c.categoryRepaired())
                    .append(" 单，累计修复 ").append(c.totalRepaired()).append(" 单，当前在办 ").append(c.activeLoad())
                    .append(" 单，预评分 ").append(String.format("%.1f", c.score())).append('\n');
        }
        return List.of(
                OpenAiCompatClient.ChatMessage.system("""
                        你是设备维修派的智能派单助手。基于给定的候选维修工统计数据，推荐最合适的 1~3 人。要求：
                        1. 简体中文输出 JSON 数组，不要输出任何其他文字；
                        2. 数组元素形如 {"userId":123,"realName":"张三","reason":"不超过40字的推荐理由，结合同类别经验与当前负荷"}；
                        3. 推荐人数最多 3 人，按推荐度降序。"""),
                OpenAiCompatClient.ChatMessage.user(sb.toString()));
    }

    /** 从模型输出中稳健提取 JSON 数组（容忍 ```json 包裹与前后杂文本） */
    public static String extractJsonArray(String raw) {
        if (raw == null) {
            return "[]";
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return "[]";
    }

    // ==================================================================
    // 维修报告上下文
    // ==================================================================

    public String buildReportContext(long workOrderId) {
        WoWorkOrder order = requireOrder(workOrderId);
        EqEquipment equipment = order.getEquipmentId() == null ? null
                : equipmentMapper.selectById(order.getEquipmentId());
        StringBuilder sb = new StringBuilder();
        sb.append("## 工单全生命周期数据\n");
        sb.append("- 工单号：").append(order.getOrderNo()).append("，标题：").append(nz(order.getTitle()))
                .append("，来源：").append(nz(order.getSource()))
                .append("，等级：").append(levelLabel(order.getLevel())).append('\n');
        sb.append("- 故障类型：").append(nz(order.getFaultType())).append("，故障描述：").append(nz(order.getFaultDesc())).append('\n');
        sb.append("- 报修时间：").append(order.getReportTime() == null ? "" : order.getReportTime().format(T))
                .append("，派单时间：").append(order.getDispatchTime() == null ? "" : order.getDispatchTime().format(T))
                .append("，实际完成：").append(order.getActualFinishTime() == null ? "" : order.getActualFinishTime().format(T)).append('\n');
        sb.append("- 最终状态：").append(WorkOrderStatus.labelOf(order.getStatus()))
                .append("，工时：").append(order.getLaborHours() == null ? 0 : order.getLaborHours()).append(" 小时")
                .append("，备件成本：").append(order.getSpareCost() == null ? 0 : order.getSpareCost()).append(" 元\n");
        if (order.getRepairSummary() != null && !order.getRepairSummary().isBlank()) {
            sb.append("- 维修总结：").append(order.getRepairSummary()).append('\n');
        }
        if (equipment != null) {
            sb.append("- 设备：").append(nz(equipment.getEquipmentName()))
                    .append("（").append(nz(equipment.getAssetNo())).append("）\n");
        }
        sb.append("\n## 状态轨迹（含各阶段停留时长）\n");
        for (WoStatusLog log : statusLogMapper.selectTimeline(workOrderId)) {
            sb.append("- ").append(log.getCreateTime() == null ? "" : log.getCreateTime().format(T))
                    .append(" ").append(label(log.getFromStatus())).append(" → ").append(label(log.getToStatus()))
                    .append("，停留 ").append(log.getCostMinutes() == null ? 0 : log.getCostMinutes()).append(" 分钟")
                    .append(log.getReason() == null ? "" : "，" + log.getReason()).append('\n');
        }
        List<WoRepairRecord> repairs = repairRecordMapper.selectByOrder(workOrderId);
        if (!repairs.isEmpty()) {
            sb.append("\n## 维修过程记录\n");
            for (WoRepairRecord r : repairs) {
                sb.append("- 【").append(nz(r.getRecordType())).append("】")
                        .append(r.getCreateTime() == null ? "" : r.getCreateTime().format(T))
                        .append("：").append(nz(r.getContent()))
                        .append(r.getCostMinutes() == null ? "" : "（耗时 " + r.getCostMinutes() + " 分钟）").append('\n');
            }
        }
        List<WorkOrderPartVO> parts = partQueryMapper.selectByOrder(workOrderId);
        if (!parts.isEmpty()) {
            sb.append("\n## 备件领用\n");
            for (WorkOrderPartVO p : parts) {
                sb.append("- ").append(nz(p.getPartName())).append("（").append(nz(p.getSpec()))
                        .append("）× ").append(p.getQuantity())
                        .append(p.getAmount() == null ? "" : "，金额 " + p.getAmount() + " 元").append('\n');
            }
        }
        WoEvaluation evaluation = evaluationMapper.selectByOrder(workOrderId);
        if (evaluation != null) {
            sb.append("\n## 验收评价\n- 态度 ").append(evaluation.getAttitudeScore())
                    .append(" / 质量 ").append(evaluation.getQualityScore())
                    .append(" / 速度 ").append(evaluation.getSpeedScore())
                    .append("，评语：").append(nz(evaluation.getContent())).append('\n');
        }
        return sb.toString();
    }

    public List<OpenAiCompatClient.ChatMessage> buildReportPrompt(String context) {
        return List.of(
                OpenAiCompatClient.ChatMessage.system("""
                        你是设备管理部的报告撰写助手。基于给定的工单全生命周期数据，输出一份结构完整的维修报告。要求：
                        1. 简体中文 Markdown，结构固定为：# 维修报告、## 一、故障概况、## 二、处理过程（按时间线）、## 三、备件与成本、## 四、结论与改进建议；
                        2. 数据必须忠于给定材料，不得虚构；缺失数据写「未记录」；
                        3. 改进建议不超过 3 条，务实可落地。"""),
                OpenAiCompatClient.ChatMessage.user(context));
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private WoWorkOrder requireOrder(long workOrderId) {
        WoWorkOrder order = orderMapper.selectById(workOrderId);
        if (order == null) {
            throw BizException.notFound("工单");
        }
        // 渗透整改第二轮（AI IDOR）：AI 端点与工单详情数据范围对齐——
        // SELF 角色仅本单相关人员可触发 AI 能力，防止遍历工单 id 借 AI 读取他人工单内容
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (!WorkOrderAccessPolicy.isVisible(order, loginUser.getUserId(),
                new java.util.HashSet<>(loginUser.getRoles()))) {
            throw BizException.forbidden("无权对该工单使用 AI 能力");
        }
        return order;
    }

    private EqHealthSnapshot latestSnapshot(Long equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        return healthSnapshotMapper.selectOne(Wrappers.<EqHealthSnapshot>lambdaQuery()
                .eq(EqHealthSnapshot::getEquipmentId, equipmentId)
                .orderByDesc(EqHealthSnapshot::getStatDate)
                .last("LIMIT 1"));
    }

    private List<WoWorkOrder> pastOrders(Long equipmentId, long excludeOrderId, int limit) {
        if (equipmentId == null) {
            return List.of();
        }
        return orderMapper.selectList(Wrappers.<WoWorkOrder>lambdaQuery()
                        .eq(WoWorkOrder::getEquipmentId, equipmentId)
                        .ne(WoWorkOrder::getId, excludeOrderId)
                        .orderByDesc(WoWorkOrder::getId))
                .stream().limit(limit).toList();
    }

    private Long orderCategoryId(Long equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        EqEquipment e = equipmentMapper.selectById(equipmentId);
        return e == null ? null : e.getCategoryId();
    }

    private String label(String code) {
        WorkOrderStatus st = WorkOrderStatus.of(code);
        return st == null ? nz(code) : st.getLabel();
    }

    private String levelLabel(String code) {
        WorkOrderLevel level = WorkOrderLevel.of(code);
        return level == null ? nz(code) : level.getLabel();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
