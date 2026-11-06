package com.equipcare.notification.outbound;

import com.equipcare.common.event.OutboxEventArrived;
import com.equipcare.inspection.event.InspectionAnomalyFoundEvent;
import com.equipcare.inventory.event.StockLowEvent;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysUserMapper;
import com.equipcare.workorder.event.SlaAlertTriggeredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 领域事件 → 外发通知（企业微信群机器人 / 邮件）。
 *
 * <p>与站内消息桥接监听器并行的第二消费者：SLA 预警、库存不足、巡检异常
 * 三类「需要人立即知道」的事件会同时外发。工单状态流转属于常规业务节奏，
 * 不外发（避免刷屏），站内 + SSE 已足够。
 *
 * <p>通道语义：
 * <ul>
 *   <li>企微 webhook = 群播（发到运维群，无个人收件人概念）；</li>
 *   <li>邮件 = 按事件 receiverIds 逐个发给对应用户的 email（用户未填邮箱则跳过）。</li>
 * </ul>
 * 全部吞异常：外发失败不影响 outbox 状态机与站内消息（至少一次语义下
 * 抛错会导致站内消息重复投递，得不偿失）。
 */
@Component
public class OutboundNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(OutboundNotifyListener.class);

    private final WeComWebhookNotifier wecom;
    private final EmailNotifier email;
    private final SysUserMapper userMapper;

    public OutboundNotifyListener(WeComWebhookNotifier wecom, EmailNotifier email, SysUserMapper userMapper) {
        this.wecom = wecom;
        this.email = email;
        this.userMapper = userMapper;
    }

    @EventListener
    public void on(OutboxEventArrived arrived) {
        Object payload = arrived.payload();
        try {
            if (payload instanceof SlaAlertTriggeredEvent evt) {
                String title = "SLA 超时预警 · " + evt.orderNo();
                String md = "**" + title + "**\n" + evt.msgContent();
                wecom.sendMarkdown(md);
                emailToReceivers(evt.receiverIds(), title, evt.msgContent());
            } else if (payload instanceof StockLowEvent evt) {
                String title = "库存不足 · " + evt.partName();
                String md = "**" + title + "**\n" + evt.msgContent();
                wecom.sendMarkdown(md);
                emailToReceivers(evt.receiverIds(), title, evt.msgContent());
            } else if (payload instanceof InspectionAnomalyFoundEvent evt) {
                String title = "巡检发现异常 · " + evt.itemName();
                String content = "异常单 " + evt.anomalyNo() + "：巡检项【" + evt.itemName()
                        + "】判定异常（等级 " + evt.level() + "）。\n" + nz(evt.description());
                wecom.sendMarkdown("**" + title + "**\n" + content);
            }
        } catch (Exception e) {
            log.error("外发通知处理失败 outboxId={} type={}", arrived.outboxId(), arrived.eventType(), e);
        }
    }

    /** 按事件携带的收件人发邮件（用户未填 email 则跳过该人） */
    private void emailToReceivers(List<Long> receiverIds, String subject, String content) {
        if (!email.enabled() || receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>(receiverIds);
        for (Long userId : ids) {
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            email.send(user.getEmail(), subject, content);
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
