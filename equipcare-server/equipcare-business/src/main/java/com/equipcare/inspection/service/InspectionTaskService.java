package com.equipcare.inspection.service;

import com.equipcare.common.result.PageResult;
import com.equipcare.inspection.dto.AnomalyQuery;
import com.equipcare.inspection.dto.AnomalyWorkOrderDTO;
import com.equipcare.inspection.dto.TaskCreateDTO;
import com.equipcare.inspection.dto.TaskQuery;
import com.equipcare.inspection.dto.TaskStartDTO;
import com.equipcare.inspection.dto.TaskSubmitDTO;
import com.equipcare.inspection.vo.AnomalyVO;
import com.equipcare.inspection.vo.CalendarDayVO;
import com.equipcare.inspection.vo.InspectionStatsVO;
import com.equipcare.inspection.vo.TaskDetailVO;
import com.equipcare.inspection.vo.TaskVO;

import java.time.YearMonth;
import java.util.List;

/**
 * 巡检执行服务。
 */
public interface InspectionTaskService {

    /** 巡检任务分页 */
    PageResult<TaskVO> page(TaskQuery query);

    /**
     * 手工发起巡检任务（扫码巡检 / 临时补检）。
     *
     * @return 任务ID
     */
    Long createManual(TaskCreateDTO dto);

    /** 任务详情：设备摘要 + 检查项清单 + 已产生异常 */
    TaskDetailVO detail(Long taskId);

    /** 开始巡检；传入二维码令牌时校验设备一致性，防止远程代签 */
    void start(Long taskId, TaskStartDTO dto);

    /**
     * 提交巡检。
     *
     * <p>逐项按 正常/异常/跳过 三态落库：必检项不允许跳过，异常必须说明原因，
     * 模板要求留证的异常项必须上传现场照片；配置了「异常自动转工单」的检查项会立即建单。
     *
     * @return 本次产生的异常数量
     */
    int submit(Long taskId, TaskSubmitDTO dto);

    /** 异常单分页 */
    PageResult<AnomalyVO> pageAnomaly(AnomalyQuery query);

    /** 关闭异常单（无需维修或已线下处理） */
    void closeAnomaly(Long anomalyId, String remark);

    /**
     * 巡检异常转维修工单。
     *
     * <p>工单只落 anomaly_id 与 source_id，设备 / 检查项 / 现场照片 / 异常描述由异常单实时联查。
     *
     * @return 工单ID
     */
    Long convertAnomaly(Long anomalyId, AnomalyWorkOrderDTO dto);

    /** 任务所属设备的二维码 PNG（执行页展示，按任务权限校验，不要求设备二维码权限） */
    byte[] equipmentQrPng(Long taskId, int size, int margin);

    /** 巡检日历：某月每天的任务分布 */
    List<CalendarDayVO> calendar(YearMonth month, Long assigneeId);

    /** 巡检统计：完成率、异常率、状态分布、计划维度与趋势 */
    InspectionStatsVO stats(int days, Long assigneeId);

    /**
     * 过期处理：未开始的任务标记 EXPIRED，执行中的只打逾期标记。
     *
     * @return 受影响任务数
     */
    int expireOverdue();
}
