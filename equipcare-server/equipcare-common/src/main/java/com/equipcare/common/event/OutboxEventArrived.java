package com.equipcare.common.event;

/**
 * Outbox 投递信封：调度器把 event_outbox 里的一行事件反序列化后，
 * 以此对象发布到 Spring 事件总线。业务监听方（notification 桥接器）
 * 统一监听本信封，按 {@code payload instanceof 具体事件} 分发。
 *
 * @param outboxId 事件主键（同时用作 SSE 的 id 字段，客户端据此去重）
 * @param eventType 事件类全限定名
 * @param payload 反序列化后的领域事件 record
 */
public record OutboxEventArrived(long outboxId, String eventType, Object payload) {
}
