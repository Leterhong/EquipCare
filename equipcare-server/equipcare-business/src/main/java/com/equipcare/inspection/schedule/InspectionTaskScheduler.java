package com.equipcare.inspection.schedule;

import com.equipcare.inspection.service.InspectionGenerationService;
import com.equipcare.inspection.service.InspectionTaskService;
import com.equipcare.inspection.support.InspectionClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 巡检调度器。
 *
 * <p>两件事，互相隔离：
 * <ol>
 *   <li>按计划生成任务；</li>
 *   <li>把超时未执行的任务标记为逾期。</li>
 * </ol>
 *
 * <p>用 fixedDelay 而不是 fixedRate：单线程调度下前一轮结束才开始计时，
 * 避免上一轮还没跑完就被下一轮重入。任务生成本身也是幂等的，
 * 即便多实例同时触发，唯一索引也能兜住。
 *
 * <p>时区通过 {@code zone} 显式绑定到配置项，不依赖 JVM 默认时区，
 * 否则容器默认 UTC 时「每天早上 8 点巡检」会在北京时间 16 点生成。
 */
@Component
@ConditionalOnProperty(name = "equipcare.inspection.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class InspectionTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(InspectionTaskScheduler.class);

    private final InspectionGenerationService generationService;
    private final InspectionTaskService taskService;
    private final InspectionClock clock;

    public InspectionTaskScheduler(InspectionGenerationService generationService,
                                   InspectionTaskService taskService,
                                   InspectionClock clock) {
        this.generationService = generationService;
        this.taskService = taskService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${equipcare.inspection.scheduler.interval-ms:60000}",
            initialDelayString = "${equipcare.inspection.scheduler.initial-delay-ms:10000}",
            zone = "${equipcare.inspection.zone:Asia/Shanghai}")
    public void tick() {
        long begin = System.currentTimeMillis();
        try {
            InspectionGenerationService.Summary summary = generationService.generateDue(clock.now());
            if (summary.created() > 0) {
                log.info("定时生成巡检任务：计划 {} 个，新建 {} 条，重复跳过 {} 条，无执行人 {} 条，失败 {} 个",
                        summary.plans(), summary.created(), summary.duplicate(),
                        summary.noExecutor(), summary.errors());
            }
        } catch (Exception e) {
            // 生成链路整体异常（如数据库瞬时不可用）：记录后等待下一轮，不让调度线程死掉
            log.error("巡检任务生成轮次异常，将在下一轮重试", e);
        }
        try {
            taskService.expireOverdue();
        } catch (Exception e) {
            log.error("巡检任务过期处理异常", e);
        }
        long cost = System.currentTimeMillis() - begin;
        if (cost > 5000) {
            log.warn("巡检调度单轮耗时 {} ms，请关注计划数量与数据库负载", cost);
        }
    }
}
