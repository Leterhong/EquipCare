package com.equipcare.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox 退避策略单元测试：5s 起步、指数翻倍、10 分钟封顶、8 次判死。
 */
class OutboxBackoffPolicyTest {

    @Test
    void 首次失败后等待5秒() {
        assertEquals(5_000L, OutboxBackoffPolicy.nextDelayMs(1));
    }

    @Test
    void 指数翻倍() {
        assertEquals(10_000L, OutboxBackoffPolicy.nextDelayMs(2));
        assertEquals(20_000L, OutboxBackoffPolicy.nextDelayMs(3));
        assertEquals(40_000L, OutboxBackoffPolicy.nextDelayMs(4));
    }

    @Test
    void 封顶10分钟() {
        assertEquals(10 * 60 * 1000L, OutboxBackoffPolicy.nextDelayMs(9));
        assertEquals(10 * 60 * 1000L, OutboxBackoffPolicy.nextDelayMs(100));
    }

    @Test
    void 非法入参按首次处理() {
        assertEquals(5_000L, OutboxBackoffPolicy.nextDelayMs(0));
        assertEquals(5_000L, OutboxBackoffPolicy.nextDelayMs(-3));
    }

    @Test
    void 第8次尝试判死() {
        assertTrue(OutboxBackoffPolicy.isDead(8));
        assertTrue(OutboxBackoffPolicy.isDead(9));
        assertFalse(OutboxBackoffPolicy.isDead(7));
    }
}
