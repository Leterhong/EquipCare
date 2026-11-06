package com.equipcare.analytics.service;

import com.equipcare.analytics.vo.HealthVO;

import java.util.List;

/**
 * 设备健康分服务。
 */
public interface HealthService {

    /** 计算并保存某设备今日健康分 */
    HealthVO recalculate(Long equipmentId);

    /** 查询设备健康分；当日无快照则实时计算（不落库） */
    HealthVO current(Long equipmentId);

    /** 批量重算全部在用设备，返回重算数量 */
    int recalculateAll();

    /** 健康分预警列表：低于阈值的设备 */
    List<HealthVO> atRisk(int threshold, int limit);
}
