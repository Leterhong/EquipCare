package com.equipcare.workorder.service;

import com.equipcare.common.result.PageResult;
import com.equipcare.workorder.dto.DispatchDTO;
import com.equipcare.workorder.dto.EvaluateDTO;
import com.equipcare.workorder.dto.ReasonDTO;
import com.equipcare.workorder.dto.RepairRecordDTO;
import com.equipcare.workorder.dto.StartRepairDTO;
import com.equipcare.workorder.dto.SubmitVerifyDTO;
import com.equipcare.workorder.dto.VerifyDTO;
import com.equipcare.workorder.dto.WorkOrderCreateDTO;
import com.equipcare.workorder.dto.WorkOrderQuery;
import com.equipcare.workorder.vo.StatusCountVO;
import com.equipcare.workorder.vo.WorkOrderAssigneeVO;
import com.equipcare.workorder.vo.WorkOrderDetailVO;
import com.equipcare.workorder.vo.WorkOrderPartVO;
import com.equipcare.workorder.vo.WorkOrderTimelineItemVO;
import com.equipcare.workorder.vo.WorkOrderVO;
import com.equipcare.workorder.vo.WorkOrderWorkbenchVO;

import java.util.List;

/**
 * 维修工单服务。
 *
 * <p>所有改变工单状态的方法都必须经过
 * {@link com.equipcare.workorder.statemachine.WorkOrderStateMachine} 校验，
 * 对外只暴露「动作」，不接受目标状态参数。系统里不存在「直接改 status」的接口。
 */
public interface WorkOrderService {

    /** 工单分页。每条记录会带上当前用户可执行的动作码 */
    PageResult<WorkOrderVO> page(WorkOrderQuery query);

    /** 工单详情，含流转轨迹、维修记录（含前后照片）、验收记录、派单轨迹、备件用料、评价、全量时间线 */
    WorkOrderDetailVO detail(Long id);

    /** 全量操作时间线（六个来源归并，按时间正序） */
    List<WorkOrderTimelineItemVO> timeline(Long id);

    /** 工单备件使用记录（验收核对用） */
    List<WorkOrderPartVO> parts(Long id);

    /** 工作台聚合：待派单 / 已驳回 / 待我接单 / 我维修中 / 待我验收 / 我已完成 */
    WorkOrderWorkbenchVO workbench(Integer limit);

    /** 可选维修人（含在办工单数），派单下拉用 */
    List<WorkOrderAssigneeVO> assignees();

    /** 我的待办数量统计 */
    List<StatusCountVO> statusCounts(Long assigneeId);

    /** 人工报修建单 */
    Long createManual(WorkOrderCreateDTO dto);

    /**
     * 巡检异常转工单（供巡检模块调用）。
     *
     * <p>工单只记录 anomaly_id 与 source_id（巡检任务ID），
     * 设备、检查项、现场照片、异常描述均通过异常单实时联查，不复制副本，
     * 避免后续巡检记录补正后两边数据对不上。
     *
     * @param anomalyId 异常ID，用于幂等与唯一约束
     * @param taskId    来源巡检任务ID，写入 source_id
     * @return 工单ID；若同设备同故障已有在途工单，则返回该工单ID（合并建单）
     */
    Long createFromAnomaly(Long anomalyId, Long taskId, Long equipmentId, String faultType, String faultDesc,
                           String level, String title);

    /** 派单 / 重新派单 */
    void dispatch(Long id, DispatchDTO dto);

    /** 接单 */
    void accept(Long id);

    /** 拒接（→ 已驳回，等待重新派单） */
    void rejectDispatch(Long id, ReasonDTO dto);

    /** 转派（未接单前换人） */
    void transfer(Long id, DispatchDTO dto);

    /** 撤回派单 */
    void revoke(Long id, ReasonDTO dto);

    /** 开始维修：记录维修原因 + 故障诊断 + 维修前照片，并起算维修时长（不改变状态） */
    void startRepair(Long id, StartRepairDTO dto);

    /** 提交验收 */
    void submitVerify(Long id, SubmitVerifyDTO dto);

    /** 验收通过 */
    void verifyPass(Long id, VerifyDTO dto);

    /** 验收不通过（退回维修中） */
    void verifyReject(Long id, VerifyDTO dto);

    /** 取消工单（仅管理角色） */
    void cancel(Long id, ReasonDTO dto);

    /** 强制转派 */
    void forceTransfer(Long id, DispatchDTO dto);

    /** 新增维修过程记录 */
    Long addRepairRecord(Long id, RepairRecordDTO dto);

    /** 服务评价（仅已完成工单，一张工单一次） */
    Long evaluate(Long id, EvaluateDTO dto);
}
