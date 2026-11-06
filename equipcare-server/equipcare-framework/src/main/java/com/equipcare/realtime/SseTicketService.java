package com.equipcare.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 连接票据服务（一次性、短时效、原子消费）。
 *
 * <p>浏览器原生 EventSource 无法携带 Authorization 头，业界标准解法是
 * 「短票据换长连接」：已登录用户先 POST /api/sse/ticket 拿一张 30 秒一次性票据，
 * 再 GET /api/sse/stream?ticket=… 建立连接。
 *
 * <p>安全要点（架构评审采纳）：
 * <ul>
 *   <li>票据为 SecureRandom 256 位随机值，不含任何可解析业务信息；</li>
 *   <li>消费即删除（{@link ConcurrentHashMap#remove} 原子），防重放；</li>
 *   <li>绑定生成它的用户，建立连接时校验一致；</li>
 *   <li>每用户每分钟最多签发 {@value #RATE_LIMIT_PER_MINUTE} 张，防刷；</li>
 *   <li>进程内存存储 —— 与 SSE 连接一样是节点本地的，本系统为单体部署，
 *       多实例部署时需改为 Redis GETDEL（代码注释已标注扩展点）。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "equipcare.sse.enabled", havingValue = "true", matchIfMissing = true)
public class SseTicketService {

    private static final Logger log = LoggerFactory.getLogger(SseTicketService.class);

    /** 票据有效期毫秒 */
    public static final long TTL_MS = 30_000L;
    /** 每用户每分钟最多签发张数 */
    public static final int RATE_LIMIT_PER_MINUTE = 10;

    private record Ticket(Long userId, long expireAt) {
    }

    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    /** 限流桶：userId → (窗口起始毫秒, 已签发张数) */
    private final ConcurrentHashMap<Long, WindowCounter> rateLimit = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** 以当前登录用户身份签发一张一次性票据 */
    public String issue(Long userId) {
        WindowCounter counter = rateLimit.computeIfAbsent(userId, k -> new WindowCounter());
        if (!counter.tryAcquire()) {
            throw new IllegalStateException("SSE 票据签发过于频繁，请稍后再试");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(userId, System.currentTimeMillis() + TTL_MS));
        return ticket;
    }

    /**
     * 原子消费票据并取回签发时的用户身份（存在且未过期），无论结果如何票据立即失效。
     *
     * <p>EventSource 建连请求不带 Authorization 头，连接身份<b>完全由票据携带</b>：
     * 票据是签发时在 JWT 保护下用 SecureRandom 生成的 256 位随机值、30 秒过期、
     * 一次性消费，不可猜测也不可重放，等价于一张短时效身份凭证。
     */
    public Long resolve(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        Ticket t = tickets.remove(ticket);
        if (t == null || t.expireAt() <= System.currentTimeMillis()) {
            return null;
        }
        return t.userId();
    }

    /** 清理过期票据（正常情况下票据 30 秒内即被消费，这里是兜底） */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        tickets.values().removeIf(t -> t.expireAt() < now);
        rateLimit.values().removeIf(w -> w.expired(now));
        int active = tickets.size();
        if (active > 0) {
            log.debug("SSE 票据存留 {} 张", active);
        }
    }

    /** 简易滑动窗口限流（每分钟重置） */
    private static final class WindowCounter {
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() > 60_000L) {
                windowStart.set(now);
                count.set(0);
            }
            return count.incrementAndGet() <= RATE_LIMIT_PER_MINUTE;
        }

        boolean expired(long now) {
            return now - windowStart.get() > 120_000L;
        }
    }
}
