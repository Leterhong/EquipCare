-- =====================================================================
-- 巡维云 EquipCare —— 第四阶段增量脚本（智能巡检中心）
--
-- 适用场景：已在第一/二/三阶段建过库的环境。全新部署直接执行 schema.sql
--          即可（表结构已包含本阶段字段），本脚本无需重复执行。
--
-- 内容：
--   1. 巡检模板项：新增「异常判定规则」「异常需现场照片」
--   2. 巡检任务项：新增「三项判定结果」「跳过原因」
--   3. 历史数据补齐：按项类型回填判定规则、关键项要求现场照片
--   4. 菜单与权限：巡检日历、巡检统计
--   5. 演示数据：工业水泵巡检模板（对应验收示例）+ 每周巡检计划
--
-- 幂等说明：ADD COLUMN 在 MySQL 8 无 IF NOT EXISTS 语法，这里用存储过程
--          查 information_schema 后再执行，可重复运行；其余语句均为
--          INSERT IGNORE / 带条件的 UPDATE。
--
-- 执行：脚本不写死库名，务必在命令行指定目标库（数据库名参数靠后生效）：
--   mysql -u root -p equipcare      < database/04-stage4.sql
--   mysql -u root -p equipcare_test < database/04-stage4.sql
-- 演示数据（第 4、5 节）仅在目标库存在「水泵」分类时写入，测试库会自动跳过。
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
-- 4. 演示数据：工业水泵模板（对应验收要求中的示例）+ 每周巡检计划
--    仅在目标库存在该分类时写入：测试库没有业务分类，硬插会撞外键。
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS ec4_seed_demo;

DELIMITER $$
CREATE PROCEDURE ec4_seed_demo()
BEGIN
    IF EXISTS (SELECT 1 FROM eq_category WHERE id = 4 AND deleted = 0) THEN
        -- 模板：工业水泵日常巡检，5 个检查项与验收示例一一对应
        INSERT IGNORE INTO insp_template
            (id, template_code, template_name, category_id, version, status, item_count, description, publish_time) VALUES
            (4, 'TPL-PUMP-DAILY', '工业水泵日常巡检', 4, 1, 'PUBLISHED', 5,
             '针对工业水泵的运行声音、外壳温度、管道渗漏、电机振动与电源连接进行日常点检', '2026-01-06 09:00:00');

        INSERT IGNORE INTO insp_template_item
            (id, template_id, seq_no, item_name, item_type, unit, min_value, max_value, normal_value, enum_options,
             required, check_method, standard_ref, abnormal_rule, auto_work_order, photo_required, abnormal_level) VALUES
            (25, 4, 1, '检查设备运行声音', 'ENUM',   NULL, NULL, NULL, '正常', '["正常","异响","噪声过大"]',
             1, '听诊棒或听觉判断', '设备操作规程 5.1', '出现异响或噪声明显增大即判为异常', 1, 1, 'HIGH'),
            (26, 4, 2, '检查设备外壳温度', 'NUMBER', '℃',  0,    60,   NULL, NULL,
             1, '红外测温仪测量外壳中部', 'GB/T 29529-2013', '实测值超出 0~60℃ 即判为异常，需附测温照片', 0, 1, 'MEDIUM'),
            (27, 4, 3, '检查管道是否漏水', 'BOOLEAN', NULL, NULL, NULL, '0', NULL,
             1, '目视检查法兰、焊缝与轴封', '给排水管道验收规范', '存在渗漏即判为异常，须拍照留证', 1, 1, 'HIGH'),
            (28, 4, 4, '检查电机振动情况', 'ENUM',   NULL, NULL, NULL, '正常', '["正常","轻微振动","明显振动"]',
             1, '手触或测振仪测量', 'ISO 10816-3', '明显振动即判为异常，轻微振动须记录并跟踪', 1, 1, 'HIGH'),
            (29, 4, 5, '检查电源连接情况', 'BOOLEAN', NULL, NULL, NULL, '1', NULL,
             1, '检查接线端子、接地与电缆外皮', 'GB 50169-2016', '接线松动、发热或接地不良即判为异常', 1, 1, 'CRITICAL');

        -- 选用该模板的默认模板绑定，便于扫码巡检自动带出
        UPDATE eq_category SET default_template_id = 4
         WHERE id = 4 AND (default_template_id IS NULL OR default_template_id = 0);

        -- 计划：每周一、周四 09:00，覆盖水泵类别下的全部设备
        INSERT IGNORE INTO insp_plan
            (id, plan_code, plan_name, template_id, scope_type, scope_config, executor_strategy, executor_ids,
             cycle_type, cycle_config, start_date, end_date, sla_minutes, generate_ahead_hours, status) VALUES
            (4, 'PLAN-PUMP-WEEKLY', '水泵每周巡检', 4, 'CATEGORY', '{"categoryIds":[4]}',
             'FIXED', '[]', 'WEEKLY', '{"time":"09:00","weekDays":[1,4]}', '2026-01-01', NULL, 720, 24, 'ENABLED');

        -- 设备快照：服务端生成任务前也会按类别自动展开，这里先落一份，
        -- 保证手工点击「立即生成」不必等一轮同步。
        INSERT IGNORE INTO insp_plan_equipment (plan_id, equipment_id, seq_no)
        SELECT 4, e.id, ROW_NUMBER() OVER (ORDER BY e.id)
          FROM eq_equipment e
         WHERE e.category_id = 4 AND e.deleted = 0 AND e.status <> 'SCRAPPED';
    END IF;
END$$
DELIMITER ;

CALL ec4_seed_demo();
DROP PROCEDURE IF EXISTS ec4_seed_demo;

-- 已有计划若未指定执行人，生成任务时会回退到「设备负责人 → 计划创建人」；
-- 演示环境里把第一个启用账号补进去，保证开箱即可看到自动生成效果。
UPDATE insp_plan p
  JOIN (SELECT MIN(id) AS uid FROM sys_user WHERE status = 1 AND deleted = 0) u
   SET p.executor_ids = CONCAT('[', u.uid, ']')
 WHERE p.executor_strategy = 'FIXED'
   AND (p.executor_ids IS NULL OR p.executor_ids = '' OR p.executor_ids = '[]')
   AND u.uid IS NOT NULL;

-- ---------------------------------------------------------------------
-- 5. 执行结果自检
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
SELECT '新增权限', CAST(COUNT(*) AS CHAR) FROM sys_permission WHERE perm_code IN ('inspect:calendar:view', 'inspect:stats:view')
UNION ALL
SELECT '水泵模板项', CAST(COUNT(*) AS CHAR) FROM insp_template_item WHERE template_id = 4
UNION ALL
SELECT '启用中的计划', CAST(COUNT(*) AS CHAR) FROM insp_plan WHERE status = 'ENABLED' AND deleted = 0;
