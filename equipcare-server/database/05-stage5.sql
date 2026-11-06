-- =====================================================================
-- 巡维云 EquipCare —— 第五阶段增量脚本（维修工单中心）
--
-- 适用场景：已完成第一至第四阶段的环境。全新部署直接执行 schema.sql 即可
--          （表结构已包含本阶段字段与 wo_evaluation 表），本脚本无需重复执行。
--
-- 内容：
--   1. 工单表：新增「维修优先级」「故障现场照片」
--   2. 维修记录：新增「维修前照片」「维修后照片」
--   3. 新建工单评价表 wo_evaluation（完成后评价：服务态度 / 维修质量 / 响应速度 / 文字）
--   4. 状态码收敛：PENDING_DISPATCH→PENDING_ASSIGN、PENDING_VERIFY→PENDING_REVIEW、
--      SUSPENDED→IN_PROGRESS（本阶段状态机定义 7 态，不再保留挂起态）
--   5. 菜单与权限：派单工作台、维修工作台、工单评价
--
-- 幂等说明：ADD COLUMN 在 MySQL 8 无 IF NOT EXISTS 语法，用存储过程查
--          information_schema 后再执行；其余为 INSERT IGNORE / 带条件的 UPDATE。
--
-- 执行：脚本不写死库名，务必在命令行指定目标库（数据库名参数靠后生效）：
--   mysql --default-character-set=utf8mb4 -u root -p equipcare      < database/05-stage5.sql
--   mysql --default-character-set=utf8mb4 -u root -p equipcare_test < database/05-stage5.sql
-- 注意必须带 --default-character-set=utf8mb4：本脚本的加列定义里含中文注释，
-- Windows 控制台默认 GBK 会让存储过程参数变成乱码（ERROR 1366）。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 可重复执行的加列过程
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS ec5_add_column;

DELIMITER $$
CREATE PROCEDURE ec5_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1
                     FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL ec5_add_column('wo_work_order', 'priority',
    "VARCHAR(10) NOT NULL DEFAULT 'NORMAL' COMMENT '维修优先级：HIGH/NORMAL/LOW' AFTER `level`");
CALL ec5_add_column('wo_work_order', 'fault_image_ids',
    "VARCHAR(500) NULL COMMENT '故障现场照片文件ID，逗号分隔（不复制文件本身）' AFTER `fault_desc`");
CALL ec5_add_column('wo_repair_record', 'before_image_ids',
    "VARCHAR(500) NULL COMMENT '维修前照片文件ID，逗号分隔' AFTER `content`");
CALL ec5_add_column('wo_repair_record', 'after_image_ids',
    "VARCHAR(500) NULL COMMENT '维修后照片文件ID，逗号分隔' AFTER `before_image_ids`");

DROP PROCEDURE IF EXISTS ec5_add_column;

-- 优先级参与「按紧急程度派单」排序，补索引
DROP PROCEDURE IF EXISTS ec5_add_index;

DELIMITER $$
CREATE PROCEDURE ec5_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(200))
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

CALL ec5_add_index('wo_work_order', 'idx_wo_priority_status', '`priority`, `status`');

DROP PROCEDURE IF EXISTS ec5_add_index;

-- ---------------------------------------------------------------------
-- 2. 工单评价表
--    与 wo_verify_record 分开：验收是内部质量把关，评价是报修人的服务感知，
--    两者的评分主体、时机、可见范围都不同，合表会让「谁打的分」说不清。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wo_evaluation (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id  BIGINT UNSIGNED NOT NULL COMMENT '工单ID，一张工单只允许一条评价',
    evaluator_id   BIGINT UNSIGNED NOT NULL COMMENT '评价人（报修人或管理员）',
    attitude_score TINYINT         NOT NULL COMMENT '服务态度 1-5',
    quality_score  TINYINT         NOT NULL COMMENT '维修质量 1-5',
    speed_score    TINYINT         NOT NULL COMMENT '响应速度 1-5',
    content        VARCHAR(500)    NULL COMMENT '文字评价',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wo_eval_order (work_order_id),
    KEY idx_wo_eval_score (quality_score),
    CONSTRAINT fk_woeval_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单服务评价';

-- ---------------------------------------------------------------------
-- 3. 状态码收敛（第五阶段状态机为 7 态）
--    只改工单主表；wo_status_log 有 append-only 触发器，历史轨迹原样保留，
--    旧码由应用层做展示兜底 —— 轨迹不能被「升级脚本」改写。
-- ---------------------------------------------------------------------
UPDATE wo_work_order SET status = 'PENDING_ASSIGN' WHERE status = 'PENDING_DISPATCH';
UPDATE wo_work_order SET status = 'PENDING_REVIEW' WHERE status = 'PENDING_VERIFY';
UPDATE wo_work_order
   SET start_repair_time = COALESCE(start_repair_time, accept_time, update_time)
 WHERE status = 'SUSPENDED';
UPDATE wo_work_order SET status = 'IN_PROGRESS' WHERE status = 'SUSPENDED';
-- 挂起时长不再参与 SLA，统一清零，避免详情页出现无来源的暂停时长
UPDATE wo_work_order SET suspend_start_time = NULL, suspend_total_minutes = 0
 WHERE suspend_start_time IS NOT NULL OR suspend_total_minutes <> 0;
UPDATE wo_work_order SET sla_status = 'NORMAL' WHERE sla_status = 'PAUSED';

-- ---------------------------------------------------------------------
-- 4. 时间线时间戳精度：秒 → 毫秒
--    工单时间线把六类记录按时间归并，现场一次维修里「接单 / 开始维修 / 提交验收」
--    可能发生在同一秒内，秒级时间戳无法排序，会让历史记录顺序失真。
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS ec5_upgrade_time_precision;

DELIMITER $$
CREATE PROCEDURE ec5_upgrade_time_precision(IN p_table VARCHAR(64), IN p_column VARCHAR(64),
                                            IN p_nullable TINYINT, IN p_comment VARCHAR(100))
BEGIN
    DECLARE v_precision INT DEFAULT NULL;
    SELECT DATETIME_PRECISION INTO v_precision
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
     LIMIT 1;
    IF v_precision IS NOT NULL AND v_precision < 3 THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` MODIFY COLUMN `', p_column, '` DATETIME(3) ',
                          IF(p_nullable = 1, 'NULL', 'NOT NULL DEFAULT CURRENT_TIMESTAMP(3)'),
                          ' COMMENT ''', p_comment, '''');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL ec5_upgrade_time_precision('wo_status_log', 'create_time', 0, '发生时间');
CALL ec5_upgrade_time_precision('wo_repair_record', 'create_time', 0, '记录时间');
CALL ec5_upgrade_time_precision('wo_dispatch_log', 'dispatch_time', 0, '派单时间');
CALL ec5_upgrade_time_precision('wo_verify_record', 'verify_time', 0, '验收时间');
CALL ec5_upgrade_time_precision('wo_evaluation', 'create_time', 0, '评价时间');
CALL ec5_upgrade_time_precision('sp_work_order_part', 'apply_time', 0, '申请时间');
CALL ec5_upgrade_time_precision('sp_work_order_part', 'issue_time', 1, '出库时间');

DROP PROCEDURE IF EXISTS ec5_upgrade_time_precision;

-- ---------------------------------------------------------------------
-- 5. 菜单与权限
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort, visible, status) VALUES
    (56, 0,  'wo:workorder:workbench', '维修工作台', 'MENU',   '/workorder/workbench', 'workorder/workbench', 'Tools',      33, 1, 1),
    (57, 0,  'wo:workorder:board',     '派单工作台', 'MENU',   '/workorder/dispatch',  'workorder/dispatch',  'Promotion',  34, 1, 1),
    (58, 24, 'wo:workorder:evaluate',  '服务评价',   'BUTTON', NULL, NULL, NULL, 41, 1, 1);

-- 管理员 / 设备管理员：派单、工作台、评价全给
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code IN ('ADMIN', 'EQUIP_ADMIN')
   AND p.perm_code IN ('wo:workorder:workbench', 'wo:workorder:board', 'wo:workorder:evaluate');

-- 维修人员：维修工作台 + 评价（自己报修的工单也要能评）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'REPAIRMAN'
   AND p.perm_code IN ('wo:workorder:workbench', 'wo:workorder:evaluate');

-- 验收人员 / 企业负责人：可看派单工作台，可评价
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code IN ('VERIFIER', 'MANAGER')
   AND p.perm_code IN ('wo:workorder:board', 'wo:workorder:evaluate');

-- 巡检人员：报修后可评价
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'INSPECTOR'
   AND p.perm_code = 'wo:workorder:evaluate';

-- ---------------------------------------------------------------------
-- 5. 自检
-- ---------------------------------------------------------------------
SELECT '当前库' AS item, DATABASE() AS value
UNION ALL
SELECT '工单优先级列', CAST(COUNT(*) AS CHAR) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_work_order' AND COLUMN_NAME IN ('priority', 'fault_image_ids')
UNION ALL
SELECT '维修记录照片列', CAST(COUNT(*) AS CHAR) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_repair_record' AND COLUMN_NAME IN ('before_image_ids', 'after_image_ids')
UNION ALL
SELECT '评价表', CAST(COUNT(*) AS CHAR) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_evaluation'
UNION ALL
SELECT '新增权限', CAST(COUNT(*) AS CHAR) FROM sys_permission
 WHERE perm_code IN ('wo:workorder:workbench', 'wo:workorder:board', 'wo:workorder:evaluate')
UNION ALL
SELECT '遗留旧状态工单', CAST(COUNT(*) AS CHAR) FROM wo_work_order
 WHERE status IN ('PENDING_DISPATCH', 'PENDING_VERIFY', 'SUSPENDED');
