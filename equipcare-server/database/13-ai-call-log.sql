-- =====================================================================
-- 巡维云 EquipCare · AI 调用审计（第十期整改）
-- 文件：database/13-ai-call-log.sql
-- 说明：AI 诊断/报告/派单推荐/缺陷识别四类调用的审计落库（谁在何时对哪张
--       工单用了什么模型、结果与耗时），支撑事后追溯与配额统计。幂等可重复执行。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_call_log (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户 id（行级隔离）',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '发起用户',
    username     VARCHAR(64)     NOT NULL COMMENT '发起用户名（冗余，便于审计检索）',
    call_type    VARCHAR(20)     NOT NULL COMMENT 'DIAGNOSIS/REPORT/DISPATCH/DEFECT',
    biz_id       BIGINT UNSIGNED NULL COMMENT '关联业务 id（工单 id）',
    model        VARCHAR(128)    NULL COMMENT '实际使用的模型 id',
    duration_ms  INT             NULL COMMENT '调用耗时（毫秒，流式按整段计）',
    status       VARCHAR(16)     NOT NULL COMMENT 'SUCCESS/ERROR',
    error_msg    VARCHAR(500)    NULL COMMENT '失败原因（截断 500 字）',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_call_user (user_id, created_at),
    KEY idx_call_type (call_type, created_at),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'AI 调用审计（只插入）';
