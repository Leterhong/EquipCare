package com.equipcare.common.event;

/**
 * 领域事件发布器（事务性 Outbox 模式的入口）。
 *
 * <p>业务代码在业务事务内调用 {@link #publish(Object)}：
 * <ul>
 *   <li>事件先以 PENDING 状态写入 event_outbox 表 —— 与业务变更<b>同事务提交</b>，
 *       业务回滚则事件一起消失，杜绝「状态改了、下游没感知」；</li>
 *   <li>事务提交后立即快速投递一次；投递失败或进程崩溃由
 *       OutboxDispatcher 定时扫描补投（指数退避），整体语义为<b>至少一次</b>；</li>
 *   <li>消费方必须容忍重复事件（现网各监听均满足：SLA 去重落在
 *       sla_alert_record 唯一键、库存去重落在消息表当天计数）。</li>
 * </ul>
 *
 * <p>接口位于 common（业务模块可见），Outbox 实现位于 framework 模块 ——
 * 保持「业务 → common」单向依赖，业务侧只面向接口编程。
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件。事件对象为业务模块 event 包下的 record，
     * 序列化为 JSON 落库，event_type 记录类全限定名。
     *
     * <p>推荐在业务事务内调用；无事务时自动以独立事务落库并立即投递。
     *
     * @param event 领域事件（record 实例，不可为 null）
     */
    void publish(Object event);
}
