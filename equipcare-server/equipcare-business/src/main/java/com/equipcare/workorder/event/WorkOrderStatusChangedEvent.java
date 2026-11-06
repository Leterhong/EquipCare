package com.equipcare.workorder.event;

/**
 * 领域事件：维修工单状态流转（七态状态机的每一次 CAS 流转）。
 * 在状态变更事务提交后由 Outbox 投递，用于 SSE 广播刷新看板与详情页。
 */
public record WorkOrderStatusChangedEvent(
        long workOrderId,
        String orderNo,
        String title,
        Long equipmentId,
        String fromStatus,
        String toStatus,
        String action,
        Long operatorId,
        String reason) {
}
