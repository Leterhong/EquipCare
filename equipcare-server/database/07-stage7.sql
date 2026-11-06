-- =====================================================================
-- 巡维云 EquipCare —— 第七阶段增量脚本（运营驾驶舱 / 通知中心 / 导出）
--
-- 适用场景：已完成第一至第六阶段的环境。全新部署直接执行 schema.sql 即可
--          （表结构已包含本阶段全部变更），本脚本无需重复执行。
--
-- 内容：
--   1. 通知中心按时间分页索引：sys_message(receiver_id, create_time)
--   2. 驾驶舱聚合辅助索引：
--      wo_work_order(report_time)      月度趋势 / 时间范围过滤
--      insp_task(plan_date)            巡检完成率 / 巡检趋势
--      sp_stock_txn(txn_time)          维修成本 / 备件消耗趋势
--   3. 自检断言：审计表、消息已读字段、驾驶舱所需权限码必须就位
--      （本阶段不新增权限码：驾驶舱复用 dashboard:view，导出复用
--        analytics:export 与各业务域 view 权限，审计复用 system:audit:view，
--        通知中心复用 system:message:view）
--
-- 幂等说明：加索引用存储过程查 information_schema 后再执行；
--          自检断言为只读 SELECT，失败会以自定义错误中断脚本。
--
-- 执行：脚本不写死库名，务必在命令行指定目标库：
--   mysql --default-character-set=utf8mb4 -u root -p equipcare      < database/07-stage7.sql
--   mysql --default-character-set=utf8mb4 -u root -p equipcare_test < database/07-stage7.sql
-- 注意必须带 --default-character-set=utf8mb4：注释含中文，
-- Windows 控制台默认 GBK 会让存储过程参数变成乱码（ERROR 1366）。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 可重复执行的加索引过程
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS ec7_add_index;

DELIMITER $$
CREATE PROCEDURE ec7_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(200))
BEGIN
    IF NOT EXISTS (SELECT 1
                     FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL ec7_add_index('sys_message',   'idx_sys_message_receiver_time', '`receiver_id`, `create_time` DESC');
CALL ec7_add_index('wo_work_order', 'idx_wo_work_order_report_time', '`report_time`');
CALL ec7_add_index('insp_task',     'idx_insp_task_plan_date',       '`plan_date`');
CALL ec7_add_index('sp_stock_txn',  'idx_sp_stock_txn_txn_time',     '`txn_time`');

DROP PROCEDURE IF EXISTS ec7_add_index;

-- ---------------------------------------------------------------------
-- 2. 断言过程：失败以 SIGNAL 真中断（普通 SELECT 1/0 在非严格模式不报错）
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS ec7_assert;

DELIMITER $$
CREATE PROCEDURE ec7_assert(IN p_ok TINYINT, IN p_message VARCHAR(200))
BEGIN
    IF p_ok IS NULL OR p_ok = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = p_message;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 3. 权限码补种（幂等，按 perm_code 判断、不指定 id——测试库的权限表
--    可能是另一套 id 体系，固定 id 会被主键冲突静默 IGNORE 掉）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort, visible, status)
SELECT 0, 'dashboard:view', '工作台', 'MENU', '/dashboard', 'dashboard/index', 'Odometer', 1, 1, 1
 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'dashboard:view');

INSERT INTO sys_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort, visible, status)
SELECT 0, 'analytics:view', '数据分析', 'MENU', '/analytics/overview', 'analytics/overview', 'TrendCharts', 52, 1, 1
 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'analytics:view');

INSERT INTO sys_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort, visible, status)
SELECT (SELECT id FROM sys_permission WHERE perm_code = 'analytics:view'),
       'analytics:export', '报表导出', 'BUTTON', NULL, NULL, NULL, 53, 1, 1
 WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'analytics:export');

-- 管理员角色补授权（幂等）：ADMIN 全量、EQUIP_ADMIN 补驾驶舱与导出
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'ADMIN' AND p.perm_code IN
       ('dashboard:view', 'analytics:view', 'analytics:export');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'EQUIP_ADMIN' AND p.perm_code IN
       ('dashboard:view', 'analytics:view', 'analytics:export');

-- 巡检员 / 维修工：驾驶舱只读（沿用阶段一「工作台人人可见」的设计意图）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code IN ('INSPECTOR', 'REPAIRMAN', 'VERIFIER')
   AND p.perm_code = 'dashboard:view';

-- ---------------------------------------------------------------------
-- 4. 自检断言（任一失败 SIGNAL 中断）
-- ---------------------------------------------------------------------

-- 4.1 审计表
SELECT IF(COUNT(*) = 1, 1, 0) INTO @ok FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log';
CALL ec7_assert(@ok, '缺失 sys_audit_log 表：请先执行 schema.sql');

-- 4.2 消息已读字段
SELECT IF(COUNT(*) = 1, 1, 0) INTO @ok FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_message' AND COLUMN_NAME = 'read_flag';
CALL ec7_assert(@ok, '缺失 sys_message.read_flag 列');

-- 4.3 驾驶舱 / 导出 / 审计 / 通知权限码
SELECT IF(COUNT(*) = 5, 1, 0) INTO @ok FROM sys_permission
 WHERE perm_code IN ('dashboard:view', 'analytics:view', 'analytics:export',
                     'system:audit:view', 'system:message:view') AND deleted = 0;
CALL ec7_assert(@ok, '权限码缺失：dashboard:view / analytics:view / analytics:export / system:audit:view / system:message:view');

-- 4.4 驾驶舱菜单指向
SELECT IF(COUNT(*) = 1 AND MAX(path) = '/dashboard', 1, 0) INTO @ok
  FROM sys_permission WHERE perm_code = 'dashboard:view' AND deleted = 0;
CALL ec7_assert(@ok, 'dashboard:view 菜单缺失或路径不为 /dashboard');

-- 4.5 ADMIN 至少能拿到驾驶舱与导出权限
SELECT IF(COUNT(*) >= 3, 1, 0) INTO @ok
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id AND r.role_code = 'ADMIN'
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.perm_code IN ('dashboard:view', 'analytics:view', 'analytics:export');
CALL ec7_assert(@ok, 'ADMIN 角色未获得驾驶舱/导出权限');

DROP PROCEDURE IF EXISTS ec7_assert;

SELECT '第七阶段迁移自检全部通过' AS result;
