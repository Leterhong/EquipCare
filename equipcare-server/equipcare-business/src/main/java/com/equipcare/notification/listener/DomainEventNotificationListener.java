package com.equipcare.notification.listener;

import com.equipcare.common.event.OutboxEventArrived;
import com.equipcare.common.realtime.SseHub;
import com.equipcare.inspection.event.InspectionAnomalyFoundEvent;
import com.equipcare.inventory.event.StockLowEvent;
import com.equipcare.notification.service.MessageService;
import com.equipcare.workorder.event.SlaAlertTriggeredEvent;
import com.equipcare.workorder.event.WorkOrderCreatedEvent;
import com.equipcare.workorder.event.WorkOrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 领域事件 → 站内消息 / SSE 实时推送 桥接监听器。
 *
 * <p>Outbox 调度器把事件反序列化后以 {@link OutboxEventArrived} 信封发布，
 * 本类是<b>唯一</b>的信封消费者，按事件类型分发：
 * <ul>
 *   <li>SLA 预警 / 低库存 → 给每个收件人发站内消息（替代旧的调度器直调）；</li>
 *   <li>工单创建 / 状态流转 / 巡检异常 / SLA / 库存 → SSE 推送（实时大屏与铃铛）。</li>
 * </ul>
 *
 * <p><b>异常策略</b>：整体吞掉并记日志、不向上抛 —— Outbox 是「至少一次」语义，
 * 若这里抛出会导致整条事件重投、已成功发送的消息重复。站内消息允许
 * 单条丢失重试（SLA / 库存侧各自有当日去重兜底），SSE 本就是尽力而为。
 */
@Component
public class DomainEventNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventNotificationListener.class);

    private final MessageService messageService;
    private final SseHub sseHub;

    public DomainEventNotificationListener(MessageService messageService, SseHub sseHub) {
        this.messageService = messageService;
        this.sseHub = sseHub;
    }

    @EventListener
    public void on(OutboxEventArrived arrived) {
        Object payload = arrived.payload();
        try {
            if (payload instanceof SlaAlertTriggeredEvent evt) {
                handleSlaAlert(arrived, evt);
            } else if (payload instanceof StockLowEvent evt) {
                handleStockLow(arrived, evt);
            } else if (payload instanceof WorkOrderCreatedEvent evt) {
                sseHub.sendToAll("workorder:changed", String.valueOf(arrived.outboxId()), evt);
            } else if (payload instanceof WorkOrderStatusChangedEvent evt) {
                sseHub.sendToAll("workorder:changed", String.valueOf(arrived.outboxId()), evt);
            } else if (payload instanceof InspectionAnomalyFoundEvent evt) {
                sseHub.sendToAll("anomaly:found", String.valueOf(arrived.outboxId()), evt);
            }
        } catch (Exception e) {
            log.error("领域事件桥接处理失败 outboxId={} type={}", arrived.outboxId(), arrived.eventType(), e);
        }
    }

    /** SLA 预警：逐个收件人发站内消息 + 定点 SSE 推送 */
    private void handleSlaAlert(OutboxEventArrived arrived, SlaAlertTriggeredEvent evt) {
        String sseId = String.valueOf(arrived.outboxId());
        for (Long receiverId : evt.receiverIds()) {
            try {
                messageService.send(receiverId, evt.msgTitle(), evt.msgContent(),
                        evt.msgType(), evt.bizType(), evt.bizId());
            } catch (Exception e) {
                log.error("SLA 预警站内消息发送失败 receiver={} workOrderId={}", receiverId, evt.workOrderId(), e);
            }
            sseHub.sendToUser(receiverId, "sla:alert", sseId,
                    Map.of("workOrderId", evt.workOrderId(), "orderNo", nz(evt.orderNo()),
                            "stage", nz(evt.stage()), "overdueMinutes", evt.overdueMinutes(),
                            "title", nz(evt.msgTitle())));
        }
    }

    /** 低库存：逐个收件人发站内消息 + 全员广播 */
    private void handleStockLow(OutboxEventArrived arrived, StockLowEvent evt) {
        String sseId = String.valueOf(arrived.outboxId());
        for (Long receiverId : evt.receiverIds()) {
            try {
                messageService.send(receiverId, evt.msgTitle(), evt.msgContent(),
                        evt.msgType(), evt.bizType(), evt.bizId());
            } catch (Exception e) {
                log.error("库存预警站内消息发送失败 receiver={} partId={}", receiverId, evt.partId(), e);
            }
        }
        sseHub.sendToAll("stock:low", sseId,
                Map.of("partId", evt.partId(), "partName", nz(evt.partName()),
                        "currentStock", evt.currentStock(), "safetyStock", evt.safetyStock(),
                        "title", nz(evt.msgTitle())));
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
