package com.equipcare.common.tenant;

/**
 * 租户上下文（ThreadLocal，参照 SecurityUtils 模式）。
 *
 * <p>当前值语义（第十期第一步，零功能变化）：
 * <ul>
 *   <li>登录请求：由后续版本的认证过滤器写入登录人所属租户；</li>
 *   <li>调度器/Outbox 投递等无登录上下文线程：{@code get()} 返回 empty，
 *       TenantLineHandler 兜底为 0（平台预置租户）——与全部既有数据一致，
 *       单租户模式下行为与改造前完全相同；</li>
 *   <li>跨租户场景（运营报表、迁移工具）用 {@link #runAs} 显式切换。</li>
 * </ul>
 */
public final class TenantContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 当前线程租户 id；无上下文返回 null（由 TenantLineHandler 兜底为 0） */
    public static Long get() {
        return HOLDER.get();
    }

    public static void set(Long tenantId) {
        HOLDER.set(tenantId);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 以指定租户身份执行一段逻辑（运营工具/跨租户统计），
     * 结束自动恢复原上下文，异常同样恢复。
     */
    public static void runAs(Long tenantId, Runnable action) {
        Long previous = HOLDER.get();
        HOLDER.set(tenantId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
