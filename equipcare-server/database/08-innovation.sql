-- =====================================================================
-- 巡维云 EquipCare · 创新升级增量脚本（第八期）
-- 文件：database/08-innovation.sql
-- 说明：事件驱动架构 —— 领域事件事务性发件箱（Transactional Outbox）表。
--       幂等可重复执行；不写死库名；不影响既有数据。
-- 背景：业务事务内同步写入事件（与状态变更同事务提交），
--       OutboxDispatcher 异步投递给事件监听器（站内消息 / SSE 实时推送），
--       崩溃或重启后未投递事件自动补投 —— 保证"至少一次"投递语义。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 领域事件发件箱
--   status 状态机：PENDING → PROCESSING → SUCCESS
--                  ↘ (attempt 超限) DEAD
--   PROCESSING 超过 120s 未完成视为 Worker 崩溃，由调度器回收为 PENDING 重投
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event_outbox (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '事件主键（同时作为 SSE lastEventId）',
    event_type     VARCHAR(191)    NOT NULL COMMENT '领域事件类全限定名（反序列化依据）',
    payload_json   JSON            NOT NULL COMMENT '事件负载（Jackson 序列化的 record）',
    status         VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/DEAD',
    attempt        INT             NOT NULL DEFAULT 0 COMMENT '已投递尝试次数',
    next_attempt_at DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次允许投递时间（指数退避）',
    locked_by      VARCHAR(64)     NULL COMMENT '认领投递的工作线程标识',
    locked_at      DATETIME(3)     NULL COMMENT '认领时间（超时回收依据）',
    published_at   DATETIME(3)     NULL COMMENT '最终投递成功时间',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件产生时间',
    PRIMARY KEY (id),
    KEY idx_outbox_dispatch (status, next_attempt_at, id),
    KEY idx_outbox_stale (status, locked_at),
    KEY idx_outbox_type (event_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '领域事件发件箱（事务性 Outbox，只允许系统调度器更新状态）';
