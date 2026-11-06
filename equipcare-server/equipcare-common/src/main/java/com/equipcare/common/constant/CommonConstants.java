package com.equipcare.common.constant;

/**
 * 全局常量。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 链路追踪ID响应头 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** MDC 中的 traceId 键 */
    public static final String MDC_TRACE_ID = "traceId";

    /** 认证请求头 */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** 逻辑删除：未删除 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除：已删除 */
    public static final int DELETED = 1;

    /** 系统内置角色编码 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 超级管理员用户名占位（仅用于保护，不预置账号） */
    public static final String BUILTIN_ADMIN_ROLE = "ADMIN";
}
