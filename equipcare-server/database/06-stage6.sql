-- =====================================================================
-- 巡维云 EquipCare —— 第六阶段增量脚本（备件闭环 / SLA 预警 / 健康分 v2）
--
-- 适用场景：已完成第一至第五阶段的环境。全新部署直接执行 schema.sql 即可
--          （表结构已包含本阶段全部变更），本脚本无需重复执行。
--
-- 内容：
--   1. 备件分类：新建 sp_part_category 表；sp_part 挂 category_id；
--      存量分类字符串自动归位到分类表
--   2. SLA 配置补全：sla_policy 增加「验收时限」；wo_work_order 增加
--      verify_due_time（提交验收时按政策计算）
--   3. SLA 预警记录表 sla_alert_record：同单同阶段每天至多一条（唯一键防刷屏）
--   4. 健康快照扩展：未完成工单数、久未维修天数、逐项扣分明细 JSON
--   5. 权限：库存管理、库存预警两个菜单；health:view 从 API 型升级为菜单型
--
-- 幂等说明：ADD COLUMN 用存储过程查 information_schema 后再执行；
--          其余为 INSERT IGNORE / 带条件的 UPDATE。
--
-- 执行：脚本不写死库名，务必在命令行指定目标库：
--   mysql --default-character-set=utf8mb4 -u root -p equipcare      < database/06-stage6.sql
--   mysql --default-character-set=utf8mb4 -u root -p equipcare_test < database/06-stage6.sql
-- 注意必须带 --default-character-set=utf8mb4：加列定义里含中文注释，
-- Windows 控制台默认 GBK 会让存储过程参数变成乱码（ERROR 1366）。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 可重复执行的加列 / 加索引过程
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS ec6_add_column;

DELIMITER $$
CREATE PROCEDURE ec6_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition VARCHAR(500))
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

DROP PROCEDURE IF EXISTS ec6_add_index;

DELIMITER $$
CREATE PROCEDURE ec6_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(200))
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

-- ---------------------------------------------------------------------
-- 2. 备件分类
--    分类从「备件档案上的一个字符串」升级为可管理的实体：
--    分类重命名时不必逐条改备件，统计也能按分类聚合。
--    原 category 字符串列保留（快照语义，历史数据不丢）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sp_part_category (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_name VARCHAR(50)     NOT NULL COMMENT '分类名称',
    remark        VARCHAR(200)    NULL COMMENT '说明',
    sort          INT             NOT NULL DEFAULT 0 COMMENT '排序（小在前）',
    create_by     BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sp_cat_name (category_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '备件分类';

CALL ec6_add_column('sp_part', 'category_id',
    "BIGINT UNSIGNED NULL COMMENT '分类ID（分类实体），category 字符串列保留作历史快照'");

-- 存量备件的分类字符串自动归位：同名分类只建一条，再把备件挂上去
INSERT IGNORE INTO sp_part_category (category_name)
SELECT DISTINCT category FROM sp_part
 WHERE category IS NOT NULL AND category <> '' AND deleted = 0;

UPDATE sp_part p
  JOIN sp_part_category c ON c.category_name = p.category
   SET p.category_id = c.id
 WHERE p.category_id IS NULL AND p.deleted = 0;

CALL ec6_add_index('sp_part', 'idx_sp_part_category', '`category_id`');

-- ---------------------------------------------------------------------
-- 3. SLA 配置补全
--    需求要求支持「按故障等级设置处理时限」。sla_policy 表在阶段一已建，
--    一直未接入代码（时限硬编码在 WorkOrderLevel 枚举里）；本阶段正式接管：
--    建单 / 派单 / 提交验收的时限一律读表，表缺行或停用时才兜底枚举值。
--    本阶段新增第四个时限：验收时限（提交验收 → 验收完成的窗口）。
-- ---------------------------------------------------------------------
CALL ec6_add_column('sla_policy', 'verify_minutes',
    "INT NOT NULL DEFAULT 240 COMMENT '验收时限（分钟）：提交验收后必须在此窗口内完成验收' AFTER `resolve_minutes`");

-- 工单落一个验收截止时间，SLA 扫描直接比较时间列，不必每次联查政策表
CALL ec6_add_column('wo_work_order', 'verify_due_time',
    "DATETIME NULL COMMENT '验收时限（提交验收时按政策 verify_minutes 计算）' AFTER `resolve_due_time`");

CALL ec6_add_index('wo_work_order', 'idx_wo_verify_due', '`verify_due_time`');
DROP PROCEDURE IF EXISTS ec6_add_index;

-- ---------------------------------------------------------------------
-- 4. SLA 预警记录
--    定时任务每轮都会扫到同一张超时工单，「避免反复生成大量相同通知」
--    不能只靠应用层判断（并发下会漏），落一条唯一键最可靠：
--    同一工单 + 同一阶段 + 同一天 只允许一条记录，alert_count 累加。
--    跨天仍会重新提醒——工单还在超时，不提醒才是事故。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sla_alert_record (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id   BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    stage           VARCHAR(20)     NOT NULL COMMENT '超时阶段：PENDING_ASSIGN/PENDING_ACCEPT/IN_PROGRESS/PENDING_REVIEW',
    alert_date      DATE            NOT NULL COMMENT '预警日期（去重维度之一）',
    order_level     VARCHAR(10)     NOT NULL COMMENT '工单等级快照：P1/P2/P3/P4',
    overdue_minutes INT             NOT NULL DEFAULT 0 COMMENT '超时分钟数',
    alert_count     INT             NOT NULL DEFAULT 1 COMMENT '当日累计提醒次数（通知不重复发，只累加计数）',
    last_alert_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次提醒时间',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次预警时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sla_alert (work_order_id, stage, alert_date),
    KEY idx_sla_alert_date (alert_date),
    CONSTRAINT fk_sla_alert_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SLA 超时预警记录';

-- ---------------------------------------------------------------------
-- 5. 健康快照扩展（健康分 v2）
--    v2 在原五项分项（故障/超时/异常/逾期/巡检加分）之外补三个因素：
--    未完成工单数、久未维修天数；逐项扣分原因以 JSON 明细落库，
--    详情页据此解释「为什么扣分、建议做什么」——分数必须可解释。
-- ---------------------------------------------------------------------
CALL ec6_add_column('eq_health_snapshot', 'open_wo_count',
    "INT NOT NULL DEFAULT 0 COMMENT '未完成维修工单数（待派单/待接单/维修中/待验收）' AFTER `anomaly_count`");
CALL ec6_add_column('eq_health_snapshot', 'idle_days',
    "INT NULL COMMENT '距最近一次完成维修的天数（从未维修为 NULL）' AFTER `open_wo_count`");
CALL ec6_add_column('eq_health_snapshot', 'risk_detail',
    "MEDIUMTEXT NULL COMMENT '逐项扣分明细 JSON：factor/deduction/detail/suggestion' AFTER `idle_days`");

DROP PROCEDURE IF EXISTS ec6_add_column;

-- ---------------------------------------------------------------------
-- 6. 权限
--    43 health:view 原为 API 型（无页面），本阶段健康列表/详情上线，
--    升级为菜单型；库存管理 / 库存预警补菜单权限（59/60 接续第五阶段）。
-- ---------------------------------------------------------------------
UPDATE sys_permission
   SET perm_type = 'MENU', path = '/health', component = 'health/list', icon = 'HeartPulse'
 WHERE id = 43 AND perm_code = 'health:view';

INSERT IGNORE INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort, visible, status) VALUES
    (59, 0, 'spare:stock:view', '库存管理', 'MENU', '/spare/stock', 'spare/stock', 'Boxes',       40, 1, 1),
    (60, 0, 'spare:alert:view', '库存预警', 'MENU', '/spare/alert', 'spare/alert', 'AlertTriangle', 41, 1, 1);

-- 库存流水（39）原本误挂在 /spare/stock 路径上，与「库存管理」冲突，归位为独立流水页
UPDATE sys_permission
   SET path = '/spare/txn', component = 'spare/txn'
 WHERE id = 39 AND perm_code = 'spare:txn:view' AND path = '/spare/stock';

-- 库存管理 / 预警：管理员与设备管理员（与既有 spare:* 的授权范围一致）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code IN ('ADMIN', 'EQUIP_ADMIN')
   AND p.perm_code IN ('spare:stock:view', 'spare:alert:view', 'health:view', 'sla:policy:view', 'sla:policy:manage', 'sla:monitor:view');

-- 企业负责人：可看 SLA 监控与健康看板（只读）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'MANAGER'
   AND p.perm_code IN ('health:view', 'sla:monitor:view');

-- ---------------------------------------------------------------------
-- 7. 自检
-- ---------------------------------------------------------------------
SELECT '当前库' AS item, DATABASE() AS value
UNION ALL
SELECT '备件分类表', CAST(COUNT(*) AS CHAR) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_part_category'
UNION ALL
SELECT '备件分类列', CAST(COUNT(*) AS CHAR) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sp_part' AND COLUMN_NAME = 'category_id'
UNION ALL
SELECT '已归位分类备件', CAST(COUNT(*) AS CHAR) FROM sp_part WHERE category_id IS NOT NULL
UNION ALL
SELECT 'SLA验收时限列', CAST(COUNT(*) AS CHAR) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sla_policy' AND COLUMN_NAME = 'verify_minutes'
UNION ALL
SELECT '工单验收时限列', CAST(COUNT(*) AS CHAR) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wo_work_order' AND COLUMN_NAME = 'verify_due_time'
UNION ALL
SELECT 'SLA预警表', CAST(COUNT(*) AS CHAR) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sla_alert_record'
UNION ALL
SELECT '健康快照扩展列', CAST(COUNT(*) AS CHAR) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_health_snapshot'
   AND COLUMN_NAME IN ('open_wo_count', 'idle_days', 'risk_detail')
UNION ALL
SELECT '新增权限', CAST(COUNT(*) AS CHAR) FROM sys_permission
 WHERE perm_code IN ('spare:stock:view', 'spare:alert:view')
UNION ALL
SELECT '健康菜单型权限', CAST(COUNT(*) AS CHAR) FROM sys_permission
 WHERE perm_code = 'health:view' AND perm_type = 'MENU';
