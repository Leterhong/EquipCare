package com.equipcare.outbox;

/**
 * Outbox 重试退避策略（纯函数，便于单测）。
 *
 * <p>指数退避 + 上限封顶：base=5s，倍率 2，即 5s/10s/20s/40s/…/上限 10 分钟。
 */
public final class OutboxBackoffPolicy {

    /** 最大尝试次数（含首次），超过即判 DEAD，等待人工处理 */
    public static final int MAX_ATTEMPTS = 8;

    private static final long BASE_DELAY_MS = 5_000L;
    private static final long MAX_DELAY_MS = 10 * 60 * 1000L;

    private OutboxBackoffPolicy() {
    }

    /**
     * 第 attempt 次尝试失败后，下次重试应等待的毫秒数。
     *
     * @param attempt 失败时的尝试次数（从 1 开始）
     */
    public static long nextDelayMs(int attempt) {
        if (attempt < 1) {
            attempt = 1;
        }
        long delay = BASE_DELAY_MS;
        for (int i = 1; i < attempt; i++) {
            delay = Math.min(MAX_DELAY_MS, delay * 2);
        }
        return Math.min(MAX_DELAY_MS, delay);
    }

    /** 是否已超出最大尝试次数，应判 DEAD */
    public static boolean isDead(int attempt) {
        return attempt >= MAX_ATTEMPTS;
    }
}
