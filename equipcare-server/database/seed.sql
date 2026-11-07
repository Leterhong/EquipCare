-- =====================================================================
-- 巡维云 EquipCare · 系统基础数据（seed）
-- 文件：database/seed.sql
--
-- 重要说明：
--   1. 本文件只写入系统运转必需的基础数据：组织、角色、权限、数据字典、
--      SLA 默认政策。不含任何业务演示数据——设备/巡检/备件等业务数据
--      由实际使用产生（框架保持空库启动，见 README）；
--   2. 严禁在此写入任何账号与密码。系统不提供默认管理员，首个管理员必须通过
--      应用启动参数一次性创建（见 README 的"初始化管理员"章节）：
--          --equipcare.bootstrap.enabled=true
--          --equipcare.bootstrap.username=admin
--          --equipcare.bootstrap.password=<自行设定的强密码>
--   3. 所有语句均为幂等写法（INSERT IGNORE + 固定主键），可重复执行。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 部门
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_dept (id, parent_id, dept_name, dept_code, sort, status) VALUES
    (1, 0, '总经办',   'D001', 1, 1),
    (2, 0, '设备科',   'D002', 2, 1),
    (3, 2, '巡检一组', 'D003', 3, 1),
    (4, 2, '维修班组', 'D004', 4, 1);

-- ---------------------------------------------------------------------
-- 2. 角色（data_scope 决定数据可见范围）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_role (id, role_code, role_name, data_scope, builtin, sort, status, remark) VALUES
    (1, 'ADMIN',       '系统管理员', 'ALL',  1, 1, 1, '账号、角色、权限、字典、审计'),
    (2, 'EQUIP_ADMIN', '设备管理员', 'ALL',  0, 2, 1, '台账、模板、计划、派单、备件、SLA'),
    (3, 'INSPECTOR',   '巡检人员',   'SELF', 0, 3, 1, '执行巡检任务、上报异常'),
    (4, 'REPAIRMAN',   '维修人员',   'SELF', 0, 4, 1, '接单、维修记录、领用备件'),
    (5, 'VERIFIER',    '验收人员',   'ALL',  0, 5, 1, '维修验收、质量评分'),
    (6, 'MANAGER',     '企业负责人', 'ALL',  0, 6, 1, '全局只读、看板与报表');

-- ---------------------------------------------------------------------
-- 3. 权限（53 条，与 PRD 权限矩阵一致）
--    perm_type：MENU 页面入口 / BUTTON 操作按钮 / API 纯接口
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort, visible, status) VALUES
    (1,  0, 'dashboard:view',              '工作台',       'MENU',   '/dashboard',            'dashboard/index',        'Odometer',     1,  1, 1),
    (2,  0, 'asset:equipment:view',        '设备台账',     'MENU',   '/equipment/list',       'equipment/list',         'Box',          10, 1, 1),
    (3,  2, 'asset:equipment:create',      '设备新增',     'BUTTON', NULL, NULL, NULL, 11, 1, 1),
    (4,  2, 'asset:equipment:update',      '设备编辑',     'BUTTON', NULL, NULL, NULL, 12, 1, 1),
    (5,  2, 'asset:equipment:delete',      '设备删除',     'BUTTON', NULL, NULL, NULL, 13, 1, 1),
    (6,  2, 'asset:equipment:status',      '设备状态变更', 'BUTTON', NULL, NULL, NULL, 14, 1, 1),
    (7,  2, 'asset:equipment:qrcode',      '二维码管理',   'BUTTON', NULL, NULL, NULL, 15, 1, 1),
    (8,  2, 'asset:equipment:import',      '设备导入',     'BUTTON', NULL, NULL, NULL, 16, 1, 1),
    (9,  2, 'asset:equipment:export',      '设备导出',     'BUTTON', NULL, NULL, NULL, 17, 1, 1),
    (10, 0, 'asset:category:manage',       '设备类别',     'MENU',   '/equipment/category',   'equipment/category',     'Files',        18, 1, 1),
    (11, 0, 'asset:location:manage',       '位置管理',     'MENU',   '/equipment/location',   'equipment/location',     'Location',     19, 1, 1),
    (12, 0, 'inspect:template:view',       '巡检模板',     'MENU',   '/inspect/template',     'inspect/template',       'Document',     20, 1, 1),
    (13, 12,'inspect:template:manage',     '模板管理',     'BUTTON', NULL, NULL, NULL, 21, 1, 1),
    (14, 0, 'inspect:plan:view',           '巡检计划',     'MENU',   '/inspect/plan',         'inspect/plan',           'Calendar',     22, 1, 1),
    (15, 14,'inspect:plan:manage',         '计划管理',     'BUTTON', NULL, NULL, NULL, 23, 1, 1),
    (16, 14,'inspect:plan:generate',       '立即生成任务', 'BUTTON', NULL, NULL, NULL, 24, 1, 1),
    (17, 0, 'inspect:task:view',           '巡检任务',     'MENU',   '/inspect/task',         'inspect/task',           'List',         25, 1, 1),
    (18, 17,'inspect:task:execute',        '执行巡检',     'BUTTON', NULL, NULL, NULL, 26, 1, 1),
    (19, 17,'inspect:task:cancel',         '取消任务',     'BUTTON', NULL, NULL, NULL, 27, 1, 1),
    (20, 0, 'inspect:anomaly:view',        '巡检异常',     'MENU',   '/inspect/anomaly',      'inspect/anomaly',        'Warning',      28, 1, 1),
    (21, 20,'inspect:anomaly:report',      '异常上报',     'BUTTON', NULL, NULL, NULL, 29, 1, 1),
    (22, 20,'inspect:anomaly:convert',     '异常转工单',   'BUTTON', NULL, NULL, NULL, 30, 1, 1),
    (23, 20,'inspect:anomaly:close',       '关闭异常',     'BUTTON', NULL, NULL, NULL, 31, 1, 1),
    (24, 0, 'wo:workorder:view',           '维修工单',     'MENU',   '/workorder/list',       'workorder/list',         'Tickets',      32, 1, 1),
    (25, 24,'wo:workorder:create',         '手工报修',     'BUTTON', NULL, NULL, NULL, 33, 1, 1),
    (26, 24,'wo:workorder:dispatch',       '派单',         'BUTTON', NULL, NULL, NULL, 34, 1, 1),
    (27, 24,'wo:workorder:accept',         '接单',         'BUTTON', NULL, NULL, NULL, 35, 1, 1),
    (28, 24,'wo:workorder:reject',         '拒接',         'BUTTON', NULL, NULL, NULL, 36, 1, 1),
    (29, 24,'wo:workorder:repair',         '维修记录',     'BUTTON', NULL, NULL, NULL, 37, 1, 1),
    (30, 24,'wo:workorder:verify',         '维修验收',     'BUTTON', NULL, NULL, NULL, 38, 1, 1),
    (31, 24,'wo:workorder:cancel',         '取消工单',     'BUTTON', NULL, NULL, NULL, 39, 1, 1),
    (32, 24,'wo:workorder:force-transfer', '强制转派',     'BUTTON', NULL, NULL, NULL, 40, 1, 1),
    (33, 0, 'spare:part:view',             '备件档案',     'MENU',   '/spare/part',           'spare/part',             'Goods',        41, 1, 1),
    (34, 33,'spare:part:manage',           '备件管理',     'BUTTON', NULL, NULL, NULL, 42, 1, 1),
    (35, 33,'spare:apply',                 '备件申请',     'BUTTON', NULL, NULL, NULL, 43, 1, 1),
    (36, 33,'spare:issue',                 '备件出库',     'BUTTON', NULL, NULL, NULL, 44, 1, 1),
    (37, 33,'spare:stock:in',              '备件入库',     'BUTTON', NULL, NULL, NULL, 45, 1, 1),
    (38, 33,'spare:stock:adjust',          '盘点调整',     'BUTTON', NULL, NULL, NULL, 46, 1, 1),
    (39, 0, 'spare:txn:view',              '库存流水',     'MENU',   '/spare/txn',            'spare/txn',              'Coin',         47, 1, 1),
    (40, 0, 'sla:policy:view',             'SLA 政策',     'MENU',   '/sla/policy',           'sla/policy',             'Timer',        48, 1, 1),
    (41, 40,'sla:policy:manage',           'SLA 配置',     'BUTTON', NULL, NULL, NULL, 49, 1, 1),
    (42, 40,'sla:monitor:view',            'SLA 监控',     'MENU',   '/sla/monitor',          'sla/monitor',            'AlarmClock',   50, 1, 1),
    (43, 0, 'health:view',                 '设备健康',     'MENU',   '/health',               'health/list',            'HeartPulse',   51, 1, 1),
    (44, 0, 'analytics:view',              '数据分析',     'MENU',   '/analytics/overview',   'analytics/overview',     'TrendCharts',  52, 1, 1),
    (45, 44,'analytics:export',            '报表导出',     'BUTTON', NULL, NULL, NULL, 53, 1, 1),
    (46, 0, 'system:user:view',            '用户管理',     'MENU',   '/system/user',          'system/user',            'User',         60, 1, 1),
    (47, 46,'system:user:manage',          '用户维护',     'BUTTON', NULL, NULL, NULL, 61, 1, 1),
    (48, 0, 'system:role:manage',          '角色权限',     'MENU',   '/system/role',          'system/role',            'Key',          62, 1, 1),
    (49, 0, 'system:dept:manage',          '组织架构',     'MENU',   '/system/dept',          'system/dept',            'OfficeBuilding',63, 1, 1),
    (50, 0, 'system:dict:manage',          '数据字典',     'MENU',   '/system/dict',          'system/dict',            'Collection',   64, 1, 1),
    (51, 0, 'system:message:view',         '我的消息',     'MENU',   '/system/message',       'system/message',         'Bell',         65, 1, 1),
    (52, 0, 'system:audit:view',           '操作审计',     'MENU',   '/system/audit',         'system/audit',           'Memo',         66, 1, 1),
    (53, 0, 'system:job:view',             '任务记录',     'MENU',   '/system/job',           'system/job',             'Clock',        67, 1, 1),
    (59, 0, 'spare:stock:view',            '库存管理',     'MENU',   '/spare/stock',          'spare/stock',            'Boxes',        48, 1, 1),
    (60, 0, 'spare:alert:view',            '库存预警',     'MENU',   '/spare/alert',          'spare/alert',            'AlertTriangle',49, 1, 1);

-- ---------------------------------------------------------------------
-- 4. 角色权限分配
-- ---------------------------------------------------------------------
-- 系统管理员：全部权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code = 'ADMIN';

-- 设备管理员：除系统管理外的全部权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'EQUIP_ADMIN'
  AND p.perm_code NOT LIKE 'system:user%'
  AND p.perm_code NOT LIKE 'system:role%'
  AND p.perm_code NOT LIKE 'system:dept%'
  AND p.perm_code NOT LIKE 'system:dict%'
  AND p.perm_code NOT LIKE 'system:audit%'
  AND p.perm_code NOT LIKE 'system:job%';

-- 巡检人员
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'INSPECTOR' AND p.perm_code IN (
    'dashboard:view', 'asset:equipment:view', 'health:view',
    'inspect:template:view', 'inspect:task:view', 'inspect:task:execute',
    'inspect:anomaly:view', 'inspect:anomaly:report',
    'wo:workorder:view', 'wo:workorder:create', 'system:message:view');

-- 维修人员
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'REPAIRMAN' AND p.perm_code IN (
    'dashboard:view', 'asset:equipment:view', 'health:view',
    'wo:workorder:view', 'wo:workorder:create', 'wo:workorder:accept',
    'wo:workorder:reject', 'wo:workorder:repair',
    'spare:part:view', 'spare:apply',
    'inspect:anomaly:view', 'inspect:anomaly:report', 'system:message:view');

-- 验收人员（全量只读 + 验收）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'VERIFIER' AND p.perm_code IN (
    'dashboard:view', 'asset:equipment:view', 'asset:equipment:export', 'health:view',
    'inspect:template:view', 'inspect:plan:view', 'inspect:task:view',
    'inspect:anomaly:view', 'wo:workorder:view', 'wo:workorder:verify',
    'spare:part:view', 'spare:txn:view', 'sla:policy:view', 'sla:monitor:view',
    'analytics:view', 'system:message:view');

-- 企业负责人（全局只读）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'MANAGER' AND p.perm_code IN (
    'dashboard:view', 'asset:equipment:view', 'asset:equipment:export', 'health:view',
    'inspect:template:view', 'inspect:plan:view', 'inspect:task:view', 'inspect:anomaly:view',
    'wo:workorder:view', 'spare:part:view', 'spare:txn:view',
    'sla:policy:view', 'sla:monitor:view',
    'analytics:view', 'analytics:export', 'system:message:view');

-- ---------------------------------------------------------------------
-- 5. 数据字典
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_dict (id, dict_code, dict_name, remark, status) VALUES
    (1, 'fault_type',      '故障类型',   '工单故障分类',       1),
    (2, 'anomaly_level',   '异常等级',   '巡检异常严重程度',   1),
    (3, 'equipment_status','设备状态',   '设备台账状态',       1),
    (4, 'workorder_level', '工单等级',   'SLA 等级 P1-P4',     1);

INSERT IGNORE INTO sys_dict_item (id, dict_code, item_value, item_label, sort, status) VALUES
    (1,  'fault_type', 'ELECTRIC',    '电气故障',     1, 1),
    (2,  'fault_type', 'MECHANICAL',  '机械故障',     2, 1),
    (3,  'fault_type', 'REFRIGERATION','制冷系统故障', 3, 1),
    (4,  'fault_type', 'LEAKAGE',     '管路渗漏',     4, 1),
    (5,  'fault_type', 'CONTROL',     '控制系统故障', 5, 1),
    (6,  'fault_type', 'OTHER',       '其他',         6, 1),
    (7,  'anomaly_level', 'LOW',      '轻微',         1, 1),
    (8,  'anomaly_level', 'MEDIUM',   '一般',         2, 1),
    (9,  'anomaly_level', 'HIGH',     '严重',         3, 1),
    (10, 'anomaly_level', 'CRITICAL', '紧急',         4, 1),
    (11, 'equipment_status', 'IN_USE',   '在用',     1, 1),
    (12, 'equipment_status', 'IDLE',     '闲置',     2, 1),
    (13, 'equipment_status', 'REPAIRING','维修中',   3, 1),
    (14, 'equipment_status', 'SCRAPPED', '已报废',   4, 1),
    (15, 'workorder_level', 'P1', '紧急（30分钟内响应）', 1, 1),
    (16, 'workorder_level', 'P2', '严重（1小时内响应）',  2, 1),
    (17, 'workorder_level', 'P3', '一般（4小时内响应）',  3, 1),
    (18, 'workorder_level', 'P4', '轻微（8小时内响应）',  4, 1);

-- ---------------------------------------------------------------------
-- 6. SLA 政策（默认业务配置，可按需在页面调整）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sla_policy (id, policy_name, level, response_minutes, resolve_minutes, verify_minutes, warn_percent, escalate_role_code, enabled, remark) VALUES
    (1, '紧急工单 SLA', 'P1', 30,  240,  120, 80, 'MANAGER',     1, '设备停机、安全风险'),
    (2, '严重工单 SLA', 'P2', 60,  480,  240, 80, 'MANAGER',     1, '主要功能受损'),
    (3, '一般工单 SLA', 'P3', 240, 1440, 480, 80, 'EQUIP_ADMIN', 1, '局部功能异常'),
    (4, '轻微工单 SLA', 'P4', 480, 2880, 960, 80, 'EQUIP_ADMIN', 1, '不影响使用');
