package com.equipcare.ai.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 调用审计实体（只插入表，见 database/13-ai-call-log.sql）。 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 id（行级隔离，拦截器自动注入） */
    private Long tenantId;

    /** 发起用户 id */
    private Long userId;

    /** 发起用户名（冗余，便于审计检索） */
    private String username;

    /** DIAGNOSIS/REPORT/DISPATCH/DEFECT */
    private String callType;

    /** 关联业务 id（工单 id） */
    private Long bizId;

    /** 实际使用的模型 id */
    private String model;

    /** 调用耗时（毫秒） */
    private Integer durationMs;

    /** SUCCESS/ERROR */
    private String status;

    /** 失败原因（截断 500 字） */
    private String errorMsg;

    private LocalDateTime createdAt;
}
