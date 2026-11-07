-- =====================================================================
-- 巡维云 EquipCare —— 第四阶段增量脚本（智能巡检中心）

SET NAMES utf8mb4;
--
-- 适用场景：已在第一/二/三阶段建过库的环境。全新部署直接执行 schema.sql
--          即可（表结构已包含本阶段字段），本脚本无需重复执行。
--
-- 内容：
--   1. 巡检模板项：新增「异常判定规则」「异常需现场照片」
--   2. 巡检任务项：新增「三项判定结果」「跳过原因」
--   3. 历史数据补齐：按项类型回填判定规则、关键项要求现场照片
--   4. 菜单与权限：巡检日历、巡检统计
--
-- 幂等说明：ADD COLUMN 在 MySQL 8 无 IF NOT EXISTS 语法，这里用存储过程
--          查 information_schema 后再执行，可重复运行；其余语句均为
--          INSERT IGNORE / 带条件的 UPDATE。
--
-- 执行：脚本不写死库名，务必在命令行指定目标库（数据库名参数靠后生效）：
--   mysql -u root -p equipcare      < database/04-stage4.sql
--   mysql -u root -p equipcare_test < database/04-stage4.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 可重复执行的加列过程
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS ec4_add_column;

DELIMITER $$
CREATE PROCEDURE ec4_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1
                     FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 1.1 巡检模板项：判定规则与现场证据
CALL ec4_add_column('insp_template_item', 'abnormal_rule',
    '`abnormal_rule` VARCHAR(200) NULL COMMENT ''异常判定规则说明（数值区间之外的人工判定口径）'' AFTER `standard_ref`');
CALL ec4_add_column('insp_template_item', 'photo_required',
    '`photo_required` TINYINT NOT NULL DEFAULT 0 COMMENT ''判为异常时是否必须上传现场照片'' AFTER `auto_work_order`');

-- 1.2 巡检任务项：三项判定结果（正常/异常/跳过）与跳过原因
CALL ec4_add_column('insp_task_item', 'result',
    '`result` VARCHAR(20) NOT NULL DEFAULT ''NORMAL'' COMMENT ''单项结果：NORMAL/ABNORMAL/SKIPPED'' AFTER `item_value`');
CALL ec4_add_column('insp_task_item', 'skip_reason',
    '`skip_reason` VARCHAR(200) NULL COMMENT ''跳过原因（仅 SKIPPED 填写）'' AFTER `remark`');

DROP PROCEDURE IF EXISTS ec4_add_column;

-- ---------------------------------------------------------------------
-- 2. 历史数据补齐（只改仍为空的行，可重复执行）
-- ---------------------------------------------------------------------
-- 数值项：按模板区间判定
UPDATE insp_template_item
   SET abnormal_rule = '实测值超出标准区间即判为异常，需填写原因'
 WHERE item_type = 'NUMBER' AND abnormal_rule IS NULL;

-- 枚举/布尔项：与正常值不一致即异常
UPDATE insp_template_item
   SET abnormal_rule = '与正常值不一致即判为异常，需选择异常并说明'
 WHERE item_type IN ('ENUM', 'BOOL', 'BOOLEAN') AND abnormal_rule IS NULL;

-- 文本项：由巡检人人工判定
UPDATE insp_template_item
   SET abnormal_rule = '由巡检人结合现场情况人工判定'
 WHERE item_type = 'TEXT' AND abnormal_rule IS NULL;

-- 严重等级的异常必须留证，便于后续追责与工单核对
UPDATE insp_template_item
   SET photo_required = 1
 WHERE abnormal_level = 'CRITICAL' AND photo_required = 0;

-- 历史数据统一：第一版字典用 BOOL，检查项类型统一为 BOOLEAN（代码里仍兼容 BOOL 读取）
UPDATE insp_template_item SET item_type = 'BOOLEAN' WHERE item_type = 'BOOL';

-- ---------------------------------------------------------------------
-- 3. 菜单与权限
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort, visible, status) VALUES
    (54, 0, 'inspect:calendar:view', '巡检日历', 'MENU', '/inspect/calendar', 'inspect/calendar', 'Calendar', 25, 1, 1),
    (55, 0, 'inspect:stats:view',    '巡检统计', 'MENU', '/inspect/stats',    'inspect/stats',    'TrendCharts', 32, 1, 1);

-- 管理员与设备管理员：全部新权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code IN ('ADMIN', 'EQUIP_ADMIN')
   AND p.perm_code IN ('inspect:calendar:view', 'inspect:stats:view');

-- 巡检人员：可看日历（自己的任务分布）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'INSPECTOR'
   AND p.perm_code = 'inspect:calendar:view';

-- 验收人员 / 企业负责人：日历 + 统计（只读）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code IN ('VERIFIER', 'MANAGER')
   AND p.perm_code IN ('inspect:calendar:view', 'inspect:stats:view');

-- ---------------------------------------------------------------------
-- 4. 执行结果自检
-- ---------------------------------------------------------------------
SELECT '当前库' AS item, DATABASE() AS detail
UNION ALL
SELECT 'insp_template_item 列', CAST(COUNT(*) AS CHAR)
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_template_item'
   AND COLUMN_NAME IN ('abnormal_rule', 'photo_required')
UNION ALL
SELECT 'insp_task_item 列', CAST(COUNT(*) AS CHAR)
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'insp_task_item'
   AND COLUMN_NAME IN ('result', 'skip_reason')
UNION ALL
SELECT '新增权限', CAST(COUNT(*) AS CHAR) FROM sys_permission WHERE perm_code IN ('inspect:calendar:view', 'inspect:stats:view');
