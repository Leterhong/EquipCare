package com.equipcare.inventory.event;

import java.util.List;

/**
 * 领域事件：备件库存低于安全库存（当天首次，去重由消息表当天计数保证）。
 * 监听方向 receiverIds（ADMIN / EQUIP_ADMIN）逐个发站内消息并推送 SSE。
 */
public record StockLowEvent(
        long partId,
        String partName,
        String partNo,
        int currentStock,
        String unit,
        int safetyStock,
        List<Long> receiverIds,
        String msgTitle,
        String msgContent,
        String msgType,
        String bizType,
        Long bizId) {
}
