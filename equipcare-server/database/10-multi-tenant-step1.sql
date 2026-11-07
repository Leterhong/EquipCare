-- =====================================================================
-- 巡维云 EquipCare · 多租户感知基础设施（第十期 · 第一步，零功能变化）
-- 文件：database/10-multi-tenant-step1.sql
-- 说明：全部业务/租户域表增加 tenant_id 列（默认 0 = 平台预置/单租户）。
--       应用侧 TenantLineInnerInterceptor 自动注入租户条件（docs/17 §2.2）。
-- 共享表（不加列，见 MybatisPlusConfig.TENANT_SHARED_TABLES）：
--       sys_permission / sys_role_permission / sys_dict / sys_dict_item / sys_job_lock
-- 幂等可重复执行；不写死库名；所有既有数据自动落在租户 0 —— 对外仍是单租户产品。
-- =====================================================================

SET NAMES utf8mb4;

-- sys_dept
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_dept ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_dept ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sys_user
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_user ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sys_role
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_role ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_role ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sys_user_role
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_user_role ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_user_role ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sys_file
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_file ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_file ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sys_message
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_message ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_message ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sys_audit_log
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_audit_log ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_audit_log ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- eq_category
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_category' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE eq_category ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_category' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE eq_category ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- eq_location
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_location' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE eq_location ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_location' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE eq_location ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- eq_equipment
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_equipment' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE eq_equipment ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_equipment' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE eq_equipment ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- eq_equipment_file
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_equipment_file' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE eq_equipment_file ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_equipment_file' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE eq_equipment_file ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- eq_health_snapshot
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_health_snapshot' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE eq_health_snapshot ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_health_snapshot' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE eq_health_snapshot ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- insp_template
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_template' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE insp_template ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_template' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_template ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- insp_template_item
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_template_item' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE insp_template_item ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_template_item' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_template_item ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- insp_plan
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_plan' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE insp_plan ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_plan' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_plan ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- insp_plan_equipment
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_plan_equipment' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE insp_plan_equipment ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_plan_equipment' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_plan_equipment ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- insp_task
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_task' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE insp_task ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_task' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_task ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- insp_task_item
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_task_item' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE insp_task_item ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_task_item' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_task_item ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- insp_anomaly
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_anomaly' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE insp_anomaly ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_anomaly' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE insp_anomaly ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- wo_work_order
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_work_order' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE wo_work_order ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_work_order' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE wo_work_order ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- wo_status_log
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_status_log' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE wo_status_log ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_status_log' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE wo_status_log ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- wo_dispatch_log
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_dispatch_log' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE wo_dispatch_log ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_dispatch_log' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE wo_dispatch_log ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- wo_repair_record
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_repair_record' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE wo_repair_record ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_repair_record' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE wo_repair_record ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- wo_verify_record
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_verify_record' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE wo_verify_record ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_verify_record' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE wo_verify_record ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- wo_evaluation
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_evaluation' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE wo_evaluation ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_evaluation' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE wo_evaluation ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sp_part_category
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_part_category' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sp_part_category ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_part_category' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sp_part_category ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sp_part
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_part' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sp_part ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_part' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sp_part ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sp_stock_txn
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_stock_txn' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sp_stock_txn ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_stock_txn' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sp_stock_txn ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sp_work_order_part
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_work_order_part' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sp_work_order_part ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_work_order_part' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sp_work_order_part ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sla_policy
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sla_policy' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sla_policy ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sla_policy' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sla_policy ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- sla_alert_record
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sla_alert_record' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sla_alert_record ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sla_alert_record' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sla_alert_record ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- event_outbox
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event_outbox' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE event_outbox ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event_outbox' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE event_outbox ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- ai_kb_document
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_kb_document' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE ai_kb_document ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''租户 id（0=平台预置/单租户模式）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_kb_document' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE ai_kb_document ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
