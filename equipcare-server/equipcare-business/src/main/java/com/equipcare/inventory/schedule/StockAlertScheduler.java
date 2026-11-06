package com.equipcare.inventory.schedule;

import com.equipcare.inventory.entity.SpPart;
import com.equipcare.inventory.mapper.SpPartMapper;
import com.equipcare.inventory.service.InventoryService;
import com.equipcare.common.event.DomainEventPublisher;
import com.equipcare.inventory.event.StockLowEvent;
import com.equipcare.inventory.vo.PartVO;
import com.equipcare.notification.mapper.SysMessageMapper;
import com.equipcare.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 库存不足提醒。
 *
 * <p>周期扫描低于安全库存的备件，给管理员 / 设备管理员发站内消息。
 * 去重规则：<b>同一备件同一天只提醒一次</b>——用 sys_message 的
 * biz_type=STOCK_ALERT + biz_id=备件ID + 当天 0 点以后是否已有消息判断，
 * 定时任务再密也不会刷屏；跨天会重新提醒（缺料状态还在，不提醒才是事故）。
 *
 * <p>异常隔离与巡检调度器一致：单轮失败记日志，绝不杀掉调度线程。
 */
@Component
@ConditionalOnProperty(name = "equipcare.stock.alert.enabled", havingValue = "true", matchIfMissing = true)
public class StockAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockAlertScheduler.class);

    /** 与 sla 域共用的时区配置：同一台机器上所有定时任务按同一本地时间行事 */
    private static final String ZONE = "${equipcare.inspection.zone:Asia/Shanghai}";

    private static final String BIZ_TYPE_STOCK_ALERT = "STOCK_ALERT";
    private static final String MSG_TYPE_SYSTEM = "SYSTEM";

    private final InventoryService inventoryService;
    private final DomainEventPublisher domainEventPublisher;
    private final SysUserMapper userMapper;
    private final SysMessageMapper messageMapper;
    /** 与 @Scheduled zone 共用同一配置：调度时刻与「当天」判定不错位（阶段十二整改 Q-06） */
    private final ZoneId zone;

    public StockAlertScheduler(InventoryService inventoryService, DomainEventPublisher domainEventPublisher,
                               SysUserMapper userMapper, SysMessageMapper messageMapper,
                               @org.springframework.beans.factory.annotation.Value("${equipcare.inspection.zone:Asia/Shanghai}") String zone) {
        this.inventoryService = inventoryService;
        this.domainEventPublisher = domainEventPublisher;
        this.userMapper = userMapper;
        this.messageMapper = messageMapper;
        this.zone = ZoneId.of(zone);
    }

    @Scheduled(fixedDelayString = "${equipcare.stock.alert.interval-ms:3600000}",
            initialDelayString = "${equipcare.stock.alert.initial-delay-ms:20000}",
            zone = ZONE)
    public void scan() {
        try {
            doScan();
        } catch (Exception e) {
            log.error("库存预警扫描失败", e);
        }
    }

    void doScan() {
        List<PartVO> below = inventoryService.belowSafetyStock();
        if (below.isEmpty()) {
            return;
        }
        Set<Long> receivers = resolveReceivers();
        if (receivers.isEmpty()) {
            log.warn("库存预警：未找到 ADMIN / EQUIP_ADMIN 收件人，本轮跳过（{} 个备件低于安全库存）",
                    below.size());
            return;
        }
        LocalDateTime todayStart = LocalDate.now(zone).atStartOfDay();
        int sent = 0;
        for (PartVO part : below) {
            // 同一备件当天只提醒一次；判断依据落在消息表本身，重启 / 多实例也不会漏判
            if (messageMapper.countSince(BIZ_TYPE_STOCK_ALERT, part.getId(), todayStart) > 0) {
                continue;
            }
            String title = "库存不足：" + part.getPartName();
            String content = "备件「" + part.getPartName() + "」（" + part.getPartNo() + "）当前库存 "
                    + part.getCurrentStock() + " " + part.getUnit() + "，低于安全库存 "
                    + part.getSafetyStock() + "，请及时补货。";
            // 领域事件：站内消息投递与 SSE 推送由 notification 监听器异步完成
            domainEventPublisher.publish(new StockLowEvent(part.getId(), part.getPartName(),
                    part.getPartNo(), part.getCurrentStock(), part.getUnit(), part.getSafetyStock(),
                    List.copyOf(receivers), title, content, MSG_TYPE_SYSTEM,
                    BIZ_TYPE_STOCK_ALERT, part.getId()));
            sent++;
        }
        if (sent > 0) {
            log.info("库存预警：本轮提醒 {} 个备件，收件人 {} 人", sent, receivers.size());
        }
    }

    private Set<Long> resolveReceivers() {
        Set<Long> receivers = new LinkedHashSet<>();
        for (String roleCode : List.of("ADMIN", "EQUIP_ADMIN")) {
            List<Long> ids = userMapper.selectUserIdsByRoleCode(roleCode);
            if (ids != null) {
                receivers.addAll(ids);
            }
        }
        return receivers;
    }
}
