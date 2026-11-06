package com.equipcare.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领域事件发件箱实体（对应 event_outbox 表，DDL 见 database/08-innovation.sql）。
 *
 * <p>生命周期：业务事务内 INSERT(PENDING) → 原子认领 PROCESSING →
 * 投递成功 SUCCESS / 重试超限 DEAD。字段语义见表注释。
 */
@Data
@TableName("event_outbox")
public class OutboxMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 领域事件类全限定名（反序列化依据） */
    private String eventType;

    /** 事件负载 JSON */
    private String payloadJson;

    /** PENDING / PROCESSING / SUCCESS / DEAD */
    private String status;

    /** 已投递尝试次数 */
    private Integer attempt;

    /** 下次允许投递时间（指数退避） */
    private LocalDateTime nextAttemptAt;

    /** 认领投递的工作线程标识 */
    private String lockedBy;

    /** 认领时间（超时回收依据） */
    private LocalDateTime lockedAt;

    /** 最终投递成功时间 */
    private LocalDateTime publishedAt;

    /** 事件产生时间 */
    private LocalDateTime createdAt;
}
