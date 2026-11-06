package com.equipcare.common.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 实时推送中心（进程内注册表）。
 *
 * <p>放在 common 层的原因：业务模块的事件桥接监听器需要直接调用它推送，
 * 而 business 不允许依赖 framework（架构守护测试强制），故下沉到与
 * TokenStore/DomainEventPublisher 同级的「登录上下文与横切设施」包。
 *
 * <p>设计要点（经架构评审修正）：
 * <ul>
 *   <li>按用户维度维护连接集合：{@code ConcurrentHashMap<Long, Set<EmitterBox>>}，
 *       单用户最多 {@value #MAX_CONNECTIONS_PER_USER} 条，超出关闭最旧一条；</li>
 *   <li>每条连接统一挂 onCompletion / onTimeout / onError 三钩子做清理，
 *       推送失败立即摘除，绝不留下僵尸连接；</li>
 *   <li>心跳使用 SSE 注释帧 {@code : ping}（15~25s 一次），浏览器不会当作消息；</li>
 *   <li>每条连接有最大生命周期（默认 30 分钟），到期由服务端主动 complete，
 *       客户端按退避策略重连并全量拉一次快照校正状态；</li>
 *   <li>发送失败的 emitter 立即移除；整个 Hub 永不抛出异常到调用方。</li>
 * </ul>
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    /** 单用户最大并发连接数（超出淘汰最旧） */
    public static final int MAX_CONNECTIONS_PER_USER = 3;
    /** 单条连接最大生命周期毫秒数（到期服务端主动断开，客户端重连） */
    public static final long MAX_CONNECTION_LIFETIME_MS = 30 * 60 * 1000L;

    /** 单条 SSE 连接包装：注册时间用于生命周期清扫 */
    record EmitterBox(Long userId, SseEmitter emitter, long registeredAt) {
    }

    private final Map<Long, Set<EmitterBox>> userEmitters = new ConcurrentHashMap<>();

    /**
     * 为指定用户注册一条 SSE 连接（由 SseController 在票据校验通过后调用）。
     * 超时参数传 0L（容器不超时），生命周期由本 Hub 主动清扫。
     */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        EmitterBox box = new EmitterBox(userId, emitter, System.currentTimeMillis());
        Set<EmitterBox> set = userEmitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        set.add(box);
        // 清理钩子：无论哪条路径结束连接，都保证从注册表摘除
        emitter.onCompletion(() -> remove(box));
        emitter.onTimeout(() -> remove(box));
        emitter.onError(t -> remove(box));
        evictOldestIfNeeded(userId, box);
        log.debug("SSE 连接注册 userId={} 当前连接数={}", userId, set.size());
        return emitter;
    }

    /** 当前在线连接数（监控用） */
    public int onlineCount() {
        return userEmitters.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * 向单个用户推送一条事件。事件名 / id / 数据均为 SSE 原生字段；
     * id 由调用方传 outbox 事件主键，客户端按其做 lastEventId 去重。
     */
    public void sendToUser(Long userId, String event, String id, Object data) {
        Set<EmitterBox> set = userEmitters.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (EmitterBox box : set.toArray(new EmitterBox[0])) {
            safeSend(box, event, id, data);
        }
    }

    /** 向全体在线用户广播（工单状态变更 / 低库存 / 巡检异常等公共事件） */
    public void sendToAll(String event, String id, Object data) {
        for (Set<EmitterBox> set : userEmitters.values()) {
            for (EmitterBox box : set.toArray(new EmitterBox[0])) {
                safeSend(box, event, id, data);
            }
        }
    }

    /** 心跳：注释帧不进入浏览器消息回调，仅保活穿透代理 */
    @Scheduled(fixedDelay = 20_000, initialDelay = 20_000)
    public void heartbeat() {
        for (Set<EmitterBox> set : userEmitters.values()) {
            for (EmitterBox box : set.toArray(new EmitterBox[0])) {
                try {
                    box.emitter().send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(box);
                    quietComplete(box);
                }
            }
        }
    }

    /** 生命周期清扫：超过最大时长的连接服务端主动断开，触发客户端重连 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sweepExpired() {
        long now = System.currentTimeMillis();
        for (Set<EmitterBox> set : userEmitters.values()) {
            for (EmitterBox box : set.toArray(new EmitterBox[0])) {
                if (now - box.registeredAt() > MAX_CONNECTION_LIFETIME_MS) {
                    remove(box);
                    quietComplete(box);
                }
            }
        }
    }

    private void safeSend(EmitterBox box, String event, String id, Object data) {
        try {
            box.emitter().send(SseEmitter.event()
                    .id(id == null ? null : id)
                    .name(event)
                    .data(data));
        } catch (Exception e) {
            // 推送失败立即摘除并结束连接，客户端会重连
            remove(box);
            quietComplete(box);
        }
    }

    private void evictOldestIfNeeded(Long userId, EmitterBox fresh) {
        Set<EmitterBox> set = userEmitters.get(userId);
        if (set == null || set.size() <= MAX_CONNECTIONS_PER_USER) {
            return;
        }
        set.stream()
                .filter(b -> b != fresh)
                .min((a, b) -> Long.compare(a.registeredAt(), b.registeredAt()))
                .ifPresent(oldest -> {
                    remove(oldest);
                    quietComplete(oldest);
                });
    }

    private void remove(EmitterBox box) {
        Set<EmitterBox> set = userEmitters.get(box.userId());
        if (set != null) {
            set.remove(box);
            if (set.isEmpty()) {
                userEmitters.remove(box.userId(), set);
            }
        }
    }

    private void quietComplete(EmitterBox box) {
        try {
            box.emitter().complete();
        } catch (Exception ignored) {
            // 连接可能已关闭，忽略
        }
    }
}
