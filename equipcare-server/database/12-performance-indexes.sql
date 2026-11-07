-- =====================================================================
-- 巡维云 EquipCare · 性能索引补充（第十期慢查询扫描）
-- 文件：database/12-performance-indexes.sql
-- 说明：EXPLAIN 实测巡检指标统计（insp_anomaly 按 create_time 范围计数）
--       走全表扫描，补时间索引。幂等可重复执行。
-- 其余热点列（wo_work_order.report_time/status、insp_task.plan_date、
-- sys_audit_log.create_time、eq_health_snapshot.stat_date）经 EXPLAIN 验证已有索引覆盖。
-- =====================================================================

SET NAMES utf8mb4;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_anomaly' AND INDEX_NAME = 'idx_insp_anomaly_create_time');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_anomaly ADD INDEX idx_insp_anomaly_create_time (create_time)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
