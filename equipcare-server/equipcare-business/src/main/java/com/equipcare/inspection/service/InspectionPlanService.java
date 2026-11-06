package com.equipcare.inspection.service;

import com.equipcare.common.result.PageResult;
import com.equipcare.inspection.dto.PlanQuery;
import com.equipcare.inspection.dto.PlanSaveDTO;
import com.equipcare.inspection.vo.PlanDetailVO;
import com.equipcare.inspection.vo.PlanVO;

/**
 * 巡检计划管理。
 */
public interface InspectionPlanService {

    PageResult<PlanVO> page(PlanQuery query);

    PlanDetailVO detail(Long id);

    Long create(PlanSaveDTO dto);

    void update(Long id, PlanSaveDTO dto);

    void delete(Long id);

    /** 启用 / 暂停 */
    void changeStatus(Long id, String status);

    /**
     * 立即生成任务（不等定时器）。
     *
     * @return 本次新建的任务数
     */
    int generateNow(Long id);
}
