package com.equipcare.workorder.event;

/**
 * 领域事件：维修工单创建（人工报修 / 扫码报修 / 巡检异常自动转单）。
 * 在创建事务提交后由 Outbox 投递，用于 SSE 实时刷新工作台看板。
 */
public record WorkOrderCreatedEvent(
        long workOrderId,
        String orderNo,
        String title,
        Long equipmentId,
        Long reporterId,
        String source) {
}
