package com.equipcare.outbox;

import com.equipcare.common.event.OutboxEventArrived;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox 调度器：把 event_outbox 中的待投递事件投递到 Spring 事件总线。
 *
 * <p>两条投递路径共用同一套<b>原子认领</b>语句（跨模型评审修正点）：
 * <ul>
 *   <li>快速路径：业务事务 afterCommit 后由发布器调 {@link #dispatchById(long)}；</li>
 *   <li>补偿路径：本类每 10 秒扫描 PENDING 事件（覆盖快速路径失败 / 进程崩溃）。</li>
 * </ul>
 * 认领即置 PROCESSING 并记录 locked_by/locked_at，PROCESSING 超过 120 秒视为
 * Worker 崩溃回收重投。单次投递失败按指数退避重试，超过
 * {@link OutboxBackoffPolicy#MAX_ATTEMPTS} 判 DEAD 并告警日志。
 *
 * <p>投递 = 反序列化 → 发布 {@link OutboxEventArrived} 信封（同步监听在
 * 本线程执行，异常被捕获记为失败）。整体语义「至少一次」，监听方需幂等。
 */
@Component
@ConditionalOnProperty(name = "equipcare.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    /** 每轮最多认领条数 */
    private static final int BATCH_SIZE = 50;
    /** PROCESSING 认领超时（秒），超过视为 Worker 崩溃回收 */
    private static final int STALE_SECONDS = 120;

    private final OutboxMessageMapper outboxMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String workerId;

    public OutboxDispatcher(OutboxMessageMapper outboxMapper,
                            ApplicationEventPublisher eventPublisher,
                            ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.workerId = resolveWorkerId();
    }

    /** 补偿扫描：回收僵死认领 → 批量认领 → 逐条投递 */
    @Scheduled(fixedDelayString = "${equipcare.outbox.dispatch-interval-ms:10000}",
            initialDelayString = "${equipcare.outbox.initial-delay-ms:20000}")
    public void dispatchPending() {
        try {
            outboxMapper.reclaimStale(STALE_SECONDS);
            LocalDateTime tickStart = LocalDateTime.now().minusSeconds(1);
            int claimed = outboxMapper.claimBatch(workerId, BATCH_SIZE);
            if (claimed == 0) {
                return;
            }
            for (OutboxMessage row : outboxMapper.selectClaimedBy(workerId, tickStart)) {
                dispatch(row);
            }
        } catch (Exception e) {
            log.error("Outbox 补偿扫描失败", e);
        }
    }

    /** 快速路径：按 id 原子认领并立即投递（失败静默，交由补偿路径） */
    public void dispatchById(long id) {
        int claimed = outboxMapper.claimById(workerId, id);
        if (claimed == 0) {
            // 已被补偿路径认领或已投递，无需处理
            return;
        }
        OutboxMessage row = outboxMapper.selectById(id);
        if (row != null) {
            dispatch(row);
        }
    }

    /** 投递单行：反序列化 → 发布信封 → 条件更新为 SUCCESS；失败走退避/判死 */
    void dispatch(OutboxMessage row) {
        try {
            Class<?> eventType = Class.forName(row.getEventType());
            Object payload = objectMapper.readValue(row.getPayloadJson(), eventType);
            eventPublisher.publishEvent(new OutboxEventArrived(row.getId(), row.getEventType(), payload));
            outboxMapper.markSuccess(row.getId());
            log.debug("领域事件投递成功 outboxId={} type={}", row.getId(), row.getEventType());
        } catch (Exception e) {
            int attempt = row.getAttempt() == null ? 1 : row.getAttempt();
            if (OutboxBackoffPolicy.isDead(attempt)) {
                outboxMapper.markRetryOrFail(row.getId(), "DEAD", LocalDateTime.now());
                log.error("领域事件投递判 DEAD（需人工介入）outboxId={} type={} attempt={}",
                        row.getId(), row.getEventType(), attempt, e);
            } else {
                long delay = OutboxBackoffPolicy.nextDelayMs(attempt);
                outboxMapper.markRetryOrFail(row.getId(), "PENDING",
                        LocalDateTime.now().plusNanos(delay * 1_000_000));
                log.warn("领域事件投递失败，{}ms 后重试 outboxId={} type={} attempt={}",
                        delay, row.getId(), row.getEventType(), attempt, e);
            }
        }
    }

    private static String resolveWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
