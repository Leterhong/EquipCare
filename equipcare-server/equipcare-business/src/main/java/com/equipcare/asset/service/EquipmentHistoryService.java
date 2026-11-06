package com.equipcare.asset.service;

import com.equipcare.asset.vo.EquipmentInspectHistoryVO;
import com.equipcare.asset.vo.EquipmentRepairHistoryVO;

import java.util.List;

/**
 * 设备档案历史（维修 / 巡检）只读查询。
 */
public interface EquipmentHistoryService {

    /** 维修历史，按报修时间倒序 */
    List<EquipmentRepairHistoryVO> repairHistory(Long equipmentId, int limit);

    /** 巡检历史，按计划日期倒序 */
    List<EquipmentInspectHistoryVO> inspectHistory(Long equipmentId, int limit);

    int countRepairHistory(Long equipmentId);
}
