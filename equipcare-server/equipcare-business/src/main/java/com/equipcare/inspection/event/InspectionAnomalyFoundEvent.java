package com.equipcare.inspection.event;

/**
 * 领域事件：巡检提交发现异常项（是否自动转工单由模板项配置决定，
 * 转单成功会另行发出 WorkOrderCreatedEvent）。
 * 监听方负责 SSE 广播，提示管理者关注。
 */
public record InspectionAnomalyFoundEvent(
        long anomalyId,
        String anomalyNo,
        Long taskId,
        Long equipmentId,
        String itemName,
        String level,
        String description) {
}
