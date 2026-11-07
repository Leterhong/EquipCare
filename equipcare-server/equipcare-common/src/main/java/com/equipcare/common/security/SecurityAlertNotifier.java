package com.equipcare.common.security;

/**
 * 安全告警通知接口（横切设施，可选组件）。
 *
 * <p>框架层（登录流程）在检测到账号锁定等安全事件时调用；实现位于
 * notification 模块（外发渠道：企业微信 / 邮件），经 ObjectProvider 注入——
 * 实现缺席时调用方必须静默跳过（安全告警是尽力而为，不得影响登录主流程）。
 *
 * <p>放在 common 的原因与 DomainEventPublisher 相同：framework 与 business
 * 都只面向 common 接口，依赖方向不受影响。
 */
public interface SecurityAlertNotifier {

    /**
     * 账号连续登录失败达到锁定阈值。
     *
     * @param username 触发锁定的账号
     * @param clientIp 来源 IP（可空）
     * @param maxFail  锁定阈值
     */
    void onAccountLocked(String username, String clientIp, int maxFail);
}
