package com.equipcare.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志。只允许插入，不提供更新与删除。
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long userId;

    private String username;

    private String module;

    private String action;

    private String requestMethod;

    private String requestUri;

    private String requestParam;

    private String resultStatus;

    private String errorMsg;

    private String ip;

    private String userAgent;

    private Integer costMs;

    private LocalDateTime createTime;
}
