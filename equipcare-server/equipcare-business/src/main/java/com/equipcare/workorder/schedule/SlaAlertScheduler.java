package com.equipcare.workorder.schedule;

import com.equipcare.workorder.service.SlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SLA 超时预警调度器。
 *
 * <p>每轮做两件事：① 重算全部活跃工单的 sla_status（NORMAL/WARN/OVERDUE）；
 * ② 对新超时的工单登记预警并通知升级角色。通知去重由 sla_alert_record
 * 的「工单 + 阶段 + 日期」唯一键保证——同一张单同一天无论扫多少轮只通知一次。
 *
 * <p>异常隔离与巡检 / 库存调度器一致：单轮失败记日志，绝不杀掉调度线程。
 */
@Component
@ConditionalOnProperty(name = "equipcare.sla.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SlaAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaAlertScheduler.class);

    private final SlaService slaService;

    public SlaAlertScheduler(SlaService slaService) {
        this.slaService = slaService;
    }

    @Scheduled(fixedDelayString = "${equipcare.sla.scheduler.interval-ms:60000}",
            initialDelayString = "${equipcare.sla.scheduler.initial-delay-ms:15000}",
            zone = "${equipcare.inspection.zone:Asia/Shanghai}")
    public void tick() {
        try {
            slaService.scan();
        } catch (Exception e) {
            log.error("SLA 预警扫描失败", e);
        }
    }
}
