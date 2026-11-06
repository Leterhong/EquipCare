package com.equipcare.outbox;

import com.equipcare.common.event.DomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 领域事件发布器 · Outbox 实现。
 *
 * <p>发布路径：业务事务内把事件写入 event_outbox（与业务变更同事务），
 * 事务提交后立即触发一次快速投递；投递交给 {@link OutboxDispatcher}，
 * 失败由其定时补投。写入与业务变更原子，是整个模式的正确性根基。
 */
@Service
@ConditionalOnProperty(name = "equipcare.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDomainEventPublisher.class);

    private final TransactionTemplate transactionTemplate;
    private final OutboxMessageMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final OutboxDispatcher dispatcher;

    public OutboxDomainEventPublisher(TransactionTemplate transactionTemplate,
                                      OutboxMessageMapper outboxMapper,
                                      ObjectMapper objectMapper,
                                      OutboxDispatcher dispatcher) {
        this.transactionTemplate = transactionTemplate;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("领域事件不可为 null");
        }
        String type = event.getClass().getName();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            // 序列化失败属于编程错误（事件必须是可 JSON 化的 record），直接抛出让开发者当场发现
            throw new IllegalArgumentException("领域事件序列化失败: " + type, e);
        }
        transactionTemplate.executeWithoutResult(status -> {
            // 实体插入：status/attempt/next_attempt_at/created_at 留空走数据库默认值，
            // MP 字段策略 NOT_NULL 自动省略空字段；自增主键回填到实体
            OutboxMessage row = new OutboxMessage();
            row.setEventType(type);
            row.setPayloadJson(payload);
            row.setStatus("PENDING");
            row.setAttempt(0);
            outboxMapper.insert(row);
            Long outboxId = row.getId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            dispatcher.dispatchById(outboxId);
                        } catch (Throwable t) {
                            // 快速投递失败不影响业务返回，调度器会补投
                            log.warn("事件快速投递失败（将定时补投）outboxId={} type={}", outboxId, type, t);
                        }
                    }
                });
            } else {
                // 无事务场景（罕见）：行已随模板事务提交，直接快速投递
                dispatcher.dispatchById(outboxId);
            }
        });
    }
}
