package com.equipcare.workorder.event;

import java.util.List;

/**
 * 领域事件：SLA 超时预警触发（当天首次，去重已由 sla_alert_record 唯一键保证）。
 * 监听方向 receiverIds 逐个发站内消息并推送 SSE 提醒。
 *
 * @param msgTitle/msgContent/msgType/bizType/bizId MessageService.send 的入参
 */
public record SlaAlertTriggeredEvent(
        long workOrderId,
        String orderNo,
        String stage,
        int overdueMinutes,
        List<Long> receiverIds,
        String msgTitle,
        String msgContent,
        String msgType,
        String bizType,
        Long bizId) {
}
