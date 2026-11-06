package com.equipcare.asset.service.impl;

import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.mapper.EquipmentHistoryMapper;
import com.equipcare.asset.service.EquipmentHistoryService;
import com.equipcare.asset.vo.EquipmentInspectHistoryVO;
import com.equipcare.asset.vo.EquipmentRepairHistoryVO;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备档案历史查询实现。
 *
 * <p>limit 只允许 1-100，防止前端一次拉全表拖慢设备详情。
 */
@Service
public class EquipmentHistoryServiceImpl implements EquipmentHistoryService {

    private static final int MAX_LIMIT = 100;

    private final EquipmentHistoryMapper historyMapper;
    private final EqEquipmentMapper equipmentMapper;

    public EquipmentHistoryServiceImpl(EquipmentHistoryMapper historyMapper, EqEquipmentMapper equipmentMapper) {
        this.historyMapper = historyMapper;
        this.equipmentMapper = equipmentMapper;
    }

    @Override
    public List<EquipmentRepairHistoryVO> repairHistory(Long equipmentId, int limit) {
        requireEquipment(equipmentId);
        return historyMapper.selectRepairHistory(equipmentId, normalizeLimit(limit));
    }

    @Override
    public List<EquipmentInspectHistoryVO> inspectHistory(Long equipmentId, int limit) {
        requireEquipment(equipmentId);
        return historyMapper.selectInspectHistory(equipmentId, normalizeLimit(limit));
    }

    @Override
    public int countRepairHistory(Long equipmentId) {
        return historyMapper.countRepairHistory(equipmentId);
    }

    private void requireEquipment(Long equipmentId) {
        EqEquipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            throw new BizException(ResultCode.VALIDATION_FAILED, "条数必须大于 0");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
