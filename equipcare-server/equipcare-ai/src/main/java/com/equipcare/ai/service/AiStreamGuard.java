package com.equipcare.ai.service;

import com.equipcare.ai.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 流式调用并发闸门：全局上限（保护上游配额）+ 单用户互斥（防连点）。
 * 全部为进程内轻量实现，掉线由 finally 释放兜底。
 */
@Component
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiStreamGuard {

    private final Semaphore global;
    private final Map<Long, AtomicInteger> perUser = new ConcurrentHashMap<>();

    public AiStreamGuard(AiProperties props) {
        this.global = new Semaphore(Math.max(1, props.getMaxConcurrentStreams()));
    }

    /** 尝试占用一个流式名额；false 表示全局繁忙或该用户已有流在进行 */
    public boolean tryAcquire(long userId) {
        AtomicInteger count = perUser.computeIfAbsent(userId, k -> new AtomicInteger());
        if (!count.compareAndSet(0, 1)) {
            return false;
        }
        if (global.tryAcquire()) {
            return true;
        }
        count.set(0);
        return false;
    }

    /** 释放名额（必须放 finally） */
    public void release(long userId) {
        global.release();
        AtomicInteger count = perUser.get(userId);
        if (count != null) {
            count.set(0);
        }
    }
}
