-- =====================================================================
-- 巡维云 EquipCare · 演示基础数据（seed）
-- 文件：database/seed.sql
--
-- 重要说明：
--   1. 本文件只写入"组织、角色、权限、字典、设备类别、位置、设备、巡检模板、
--      巡检计划、备件、SLA 政策"等基础数据；
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
-- 6. 设备类别
-- ---------------------------------------------------------------------
INSERT IGNORE INTO eq_category (id, parent_id, category_code, category_name, default_template_id, maintain_cycle_days, sort, status) VALUES
    (1, 0, 'KT',  '空调机组', 1,    30,  1, 1),
    (2, 0, 'KYJ', '空压机',   NULL, 30,  2, 1),
    (3, 0, 'PDG', '配电柜',   2,    90,  3, 1),
    (4, 0, 'SB',  '水泵',     NULL, 30,  4, 1),
    (5, 0, 'DT',  '电梯',     NULL, 15,  5, 1),
    (6, 0, 'XFS', '消防设施', 3,    30,  6, 1);

-- 模板晚于类别插入，这里补一次默认模板绑定（可重复执行，兼容已存在的库）
UPDATE eq_category SET default_template_id = 1 WHERE id = 1;
UPDATE eq_category SET default_template_id = 2 WHERE id = 3;
UPDATE eq_category SET default_template_id = 3 WHERE id = 6;

-- ---------------------------------------------------------------------
-- 7. 位置（全路径冗余，避免递归查询）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO eq_location (id, parent_id, location_code, location_name, location_type, full_path, sort, status) VALUES
    (1, 0, 'A-PARK',    'A 园区',       'PARK',     'A 园区',                    1, 1),
    (2, 1, 'A-B1',      'B 栋',         'BUILDING', 'A 园区/B 栋',               1, 1),
    (3, 2, 'A-B1-1F',   '1 层',         'FLOOR',    'A 园区/B 栋/1 层',          1, 1),
    (4, 2, 'A-B1-2F',   '2 层',         'FLOOR',    'A 园区/B 栋/2 层',          2, 1),
    (5, 3, 'A-B1-101',  '101 配电间',   'ROOM',     'A 园区/B 栋/1 层/101 配电间', 1, 1),
    (6, 4, 'A-B1-201',  '201 机房',     'ROOM',     'A 园区/B 栋/2 层/201 机房', 1, 1),
    (7, 2, 'A-B1-ROOF', '屋面设备区',   'ROOM',     'A 园区/B 栋/屋面设备区',    3, 1);

-- ---------------------------------------------------------------------
-- 8. 设备台账（12 台，覆盖 4 种状态）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO eq_equipment
    (id, asset_no, equipment_name, category_id, location_id, dept_id, brand, model, spec, serial_no,
     manufacturer, purchase_date, install_date, warranty_end_date, original_value, status,
     health_score, qr_token, remark) VALUES
    (1,  'EQ-KT-000001',  '1 号中央空调机组', 1, 7, 2, '格力',   'GMV-800W',  '制冷量 80kW',  'KT2023001', '珠海格力', '2023-03-15', '2023-05-10', '2028-05-09', 268000.00, 'IN_USE',   92.50, 'q7c3f1a2e8b94d05', '屋面主设备'),
    (2,  'EQ-KT-000002',  '2 号中央空调机组', 1, 7, 2, '格力',   'GMV-800W',  '制冷量 80kW',  'KT2023002', '珠海格力', '2023-03-15', '2023-05-10', '2028-05-09', 268000.00, 'IN_USE',   88.00, 'q1a2b3c4d5e6f708', NULL),
    (3,  'EQ-KYJ-000001', '1 号螺杆空压机',   2, 7, 2, '阿特拉斯','GA55',     '排气量 9.6m³/min','KYJ2022041','阿特拉斯', '2022-08-01', '2022-09-12', '2027-09-11', 185000.00, 'REPAIRING',72.00, 'q9f8e7d6c5b4a302', '近期异响待修'),
    (4,  'EQ-KYJ-000002', '2 号螺杆空压机',   2, 7, 2, '阿特拉斯','GA55',     '排气量 9.6m³/min','KYJ2022042','阿特拉斯', '2022-08-01', '2022-09-12', '2027-09-11', 185000.00, 'IN_USE',   90.00, 'q3d4e5f60718293a', NULL),
    (5,  'EQ-PDG-000001', '101 配电间低压柜', 3, 5, 2, '施耐德', 'MNS',       '400V/630A',    'PDG2021110','施耐德电气','2021-11-20','2021-12-05','2026-12-04', 96000.00,  'IN_USE',   95.00, 'q5b6c7d8e9f01a2b', NULL),
    (6,  'EQ-PDG-000002', '201 机房配电柜',   3, 6, 2, '施耐德', 'MNS',       '400V/400A',    'PDG2021111','施耐德电气','2021-11-20','2021-12-05','2026-12-04', 78000.00,  'IN_USE',   93.00, 'q7e8f9a0b1c2d3e4', NULL),
    (7,  'EQ-SB-000001',  '1 号冷冻水泵',     4, 6, 2, '格兰富', 'CR20-5',    '流量 20m³/h',  'SB2022067', '格兰富',   '2022-06-10', '2022-07-01', '2027-06-30', 32000.00,  'IN_USE',   86.00, 'q1c2d3e4f5a6b708', NULL),
    (8,  'EQ-SB-000002',  '2 号冷冻水泵',     4, 6, 2, '格兰富', 'CR20-5',    '流量 20m³/h',  'SB2022068', '格兰富',   '2022-06-10', '2022-07-01', '2027-06-30', 32000.00,  'IDLE',     78.00, 'q3f4a5b6c7d8e901', '备用水泵'),
    (9,  'EQ-DT-000001',  'B 栋 1 号客梯',    5, 2, 2, '三菱',   'LEHY-III',  '载重 1000kg',  'DT2020088', '上海三菱', '2020-09-01', '2020-11-15', '2025-11-14', 420000.00, 'IN_USE',   82.00, 'q5a6b7c8d9e0f102', '保修已到期'),
    (10, 'EQ-DT-000002',  'B 栋 2 号客梯',    5, 2, 2, '三菱',   'LEHY-III',  '载重 1000kg',  'DT2020089', '上海三菱', '2020-09-01', '2020-11-15', '2025-11-14', 420000.00, 'IN_USE',   91.00, 'q7b8c9d0e1f2a304', NULL),
    (11, 'EQ-XFS-000001', '消防水泵房主泵',   6, 3, 2, '凯泉',   'XBD6.0/20', '扬程 60m',     'XFS2021055', '上海凯泉', '2021-05-06', '2021-06-01', '2026-05-31', 56000.00,  'IN_USE',   97.00, 'q9d0e1f2a3b4c506', '季度巡检'),
    (12, 'EQ-XFS-000002', '旧消防稳压泵',     6, 3, 2, '凯泉',   'XBD4.0/10', '扬程 40m',     'XFS2015032', '上海凯泉', '2015-04-10', '2015-05-20', '2020-05-19', 18000.00,  'SCRAPPED', 40.00, 'q1f2a3b4c5d6e708', '超期服役，待报废处置');

-- ---------------------------------------------------------------------
-- 9. 巡检模板与巡检项
-- ---------------------------------------------------------------------
INSERT IGNORE INTO insp_template (id, template_code, template_name, category_id, version, status, item_count, description, publish_time) VALUES
    (1, 'TPL-KT-DAILY',  '空调机组日常巡检', 1, 1, 'PUBLISHED', 8,  '每日对中央空调机组运行状态、温度、压力、电流进行检查', '2026-01-05 09:00:00'),
    (2, 'TPL-PDG-MONTH','配电柜月度巡检',   3, 1, 'PUBLISHED', 10, '每月对低压配电柜进行电气安全与温升检查',             '2026-01-05 09:10:00'),
    (3, 'TPL-XFS-QUART','消防设施季度巡检', 6, 1, 'PUBLISHED', 6,  '每季度对消防水泵、阀门、压力进行检查',               '2026-01-05 09:20:00');

INSERT IGNORE INTO insp_template_item
    (id, template_id, seq_no, item_name, item_type, unit, min_value, max_value, normal_value, enum_options,
     required, check_method, standard_ref, auto_work_order, abnormal_level) VALUES
    (1,  1, 1, '机组运行状态',   'ENUM',   NULL, NULL, NULL, '运行', '["运行","停机","故障"]', 1, '查看控制面板', '设备操作规程 3.1', 1, 'HIGH'),
    (2,  1, 2, '送风温度',       'NUMBER', '℃',  16,   26,   NULL, NULL, 1, '红外测温仪测量', 'GB 50243-2016', 0, 'MEDIUM'),
    (3,  1, 3, '回风温度',       'NUMBER', '℃',  20,   30,   NULL, NULL, 1, '红外测温仪测量', 'GB 50243-2016', 0, 'MEDIUM'),
    (4,  1, 4, '压缩机运行电流', 'NUMBER', 'A',   0,    45,   NULL, NULL, 1, '钳形电流表', '铭牌额定值', 1, 'HIGH'),
    (5,  1, 5, '冷媒压力',       'NUMBER', 'MPa', 0.4,  1.6,  NULL, NULL, 1, '压力表读数', '厂家技术手册', 1, 'HIGH'),
    (6,  1, 6, '冷凝器翅片清洁', 'BOOLEAN',   NULL, NULL, NULL, '1', NULL, 1, '目视检查', '维护手册 4.2', 0, 'LOW'),
    (7,  1, 7, '机组有无异响',   'BOOLEAN',   NULL, NULL, NULL, '0', NULL, 1, '听觉判断', '经验标准', 1, 'HIGH'),
    (8,  1, 8, '现场环境描述',   'TEXT',   NULL, NULL, NULL, NULL, NULL, 0, '文字描述', NULL, 0, 'LOW'),
    (9,  2, 1, '柜体外观检查',   'BOOLEAN',   NULL, NULL, NULL, '1', NULL, 1, '目视检查', 'DL/T 664-2016', 0, 'LOW'),
    (10, 2, 2, '母线温度',       'NUMBER', '℃',  0,    70,   NULL, NULL, 1, '红外测温', 'DL/T 664-2016', 1, 'CRITICAL'),
    (11, 2, 3, '接线端子紧固',   'BOOLEAN',   NULL, NULL, NULL, '1', NULL, 1, '力矩扳手复紧', '电气安装规范', 0, 'MEDIUM'),
    (12, 2, 4, '绝缘电阻',       'NUMBER', 'MΩ', 1,    999,  NULL, NULL, 1, '兆欧表测量', 'GB 50150-2016', 1, 'CRITICAL'),
    (13, 2, 5, '三相电流平衡度', 'NUMBER', '%',   0,    10,   NULL, NULL, 1, '钳形电流表', 'GB 50150-2016', 1, 'HIGH'),
    (14, 2, 6, '指示灯状态',     'ENUM',   NULL, NULL, NULL, '正常', '["正常","异常"]', 1, '目视检查', '设备说明书', 0, 'MEDIUM'),
    (15, 2, 7, '柜内有无异味',   'BOOLEAN',   NULL, NULL, NULL, '0', NULL, 1, '嗅觉判断', '经验标准', 1, 'HIGH'),
    (16, 2, 8, '接地连接检查',   'BOOLEAN',   NULL, NULL, NULL, '1', NULL, 1, '目视+万用表', 'GB 50169-2016', 1, 'HIGH'),
    (17, 2, 9, '仪表读数记录',   'TEXT',   NULL, NULL, NULL, NULL, NULL, 0, '抄录', NULL, 0, 'LOW'),
    (18, 2, 10,'柜内清洁状况',   'BOOLEAN',   NULL, NULL, NULL, '1', NULL, 0, '目视检查', '维护手册', 0, 'LOW'),
    (19, 3, 1, '消防泵运行状态', 'ENUM',   NULL, NULL, NULL, '待机', '["待机","运行","故障"]', 1, '查看控制柜', 'GB 50974-2014', 1, 'CRITICAL'),
    (20, 3, 2, '出口压力',       'NUMBER', 'MPa', 0.4,  0.8,  NULL, NULL, 1, '压力表读数', 'GB 50974-2014', 1, 'CRITICAL'),
    (21, 3, 3, '阀门启闭状态',   'BOOLEAN',   NULL, NULL, NULL, '1', NULL, 1, '手动确认', '维保规程', 0, 'HIGH'),
    (22, 3, 4, '管路渗漏检查',   'BOOLEAN',   NULL, NULL, NULL, '0', NULL, 1, '目视检查', '维保规程', 1, 'HIGH'),
    (23, 3, 5, '控制柜电源',     'BOOLEAN',   NULL, NULL, NULL, '1', NULL, 1, '目视+测量', '维保规程', 1, 'CRITICAL'),
    (24, 3, 6, '季度试运行记录', 'TEXT',   NULL, NULL, NULL, NULL, NULL, 0, '记录试运行情况', NULL, 0, 'LOW');

-- ---------------------------------------------------------------------
-- 10. 巡检计划
-- ---------------------------------------------------------------------
INSERT IGNORE INTO insp_plan
    (id, plan_code, plan_name, template_id, scope_type, scope_config, executor_strategy, executor_ids,
     cycle_type, cycle_config, start_date, end_date, sla_minutes, generate_ahead_hours, status) VALUES
    (1, 'PLAN-KT-DAILY',  '空调机组每日巡检', 1, 'CATEGORY', '{"categoryIds":[1]}',
     'FIXED', '[]', 'DAILY',  '{"time":"08:30"}',                    '2026-01-01', NULL, 480, 24, 'ENABLED'),
    (2, 'PLAN-PDG-MONTH', '配电柜月度巡检',   2, 'CATEGORY', '{"categoryIds":[3]}',
     'FIXED', '[]', 'MONTHLY','{"days":[1],"time":"09:00"}',         '2026-01-01', NULL, 1440, 24, 'ENABLED'),
    (3, 'PLAN-XFS-QUART', '消防设施季度巡检', 3, 'LOCATION', '{"locationIds":[3]}',
     'FIXED', '[]', 'MONTHLY','{"days":[1,4,7,10],"time":"10:00"}',  '2026-01-01', NULL, 2880, 24, 'DISABLED');

INSERT IGNORE INTO insp_plan_equipment (id, plan_id, equipment_id, seq_no) VALUES
    (1, 1, 1, 1), (2, 1, 2, 2), (3, 2, 5, 1), (4, 2, 6, 2), (5, 3, 11, 1);

-- ---------------------------------------------------------------------
-- 11. 备件（3 种低于安全库存，用于演示库存预警）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sp_part
    (id, part_no, part_name, category, spec, unit, brand, safety_stock, current_stock, unit_price,
     warehouse_location, supplier, status) VALUES
    (1,  'SP-000001', '空调压缩机',       '制冷配件', 'ZP182KQE',    '台', '谷轮',   2, 3,  8600.00, 'A-01-01', '格力售后',   1),
    (2,  'SP-000002', '冷媒 R410A',       '耗材',     '10kg/罐',     '罐', '霍尼韦尔',5, 8,   680.00, 'A-01-02', '霍尼韦尔代理',1),
    (3,  'SP-000003', '风机轴承',         '机械配件', '6206-2RS',    '个', 'NSK',    10, 4,    95.00, 'A-02-01', 'NSK 代理',   1),
    (4,  'SP-000004', '空气滤芯',         '耗材',     'GA55 专用',   '个', '阿特拉斯',6, 12,   320.00, 'A-02-02', '阿特拉斯',   1),
    (5,  'SP-000005', '油分离器',         '机械配件', 'GA55 专用',   '个', '阿特拉斯',2, 2,   1450.00, 'A-02-03', '阿特拉斯',   1),
    (6,  'SP-000006', '接触器',           '电气配件', 'LC1D50',      '个', '施耐德',  8, 15,   280.00, 'B-01-01', '施耐德电气', 1),
    (7,  'SP-000007', '断路器',           '电气配件', 'NSX160F',     '个', '施耐德',  4, 6,    760.00, 'B-01-02', '施耐德电气', 1),
    (8,  'SP-000008', '温度传感器',       '电气配件', 'PT100',       '个', '威卡',    6, 2,    210.00, 'B-01-03', '威卡代理',   1),
    (9,  'SP-000009', '机械密封',         '机械配件', 'CR20 专用',   '套', '格兰富',  4, 5,    560.00, 'A-03-01', '格兰富',     1),
    (10, 'SP-000010', '水泵叶轮',         '机械配件', 'CR20-5',      '个', '格兰富',  2, 1,   1280.00, 'A-03-02', '格兰富',     1),
    (11, 'SP-000011', '消防泵压力表',     '仪表',     'Y-100 0-1.6MPa','个','红旗',   6, 9,     85.00, 'C-01-01', '上海红旗',   1),
    (12, 'SP-000012', '密封垫片',         '耗材',     'DN100',       '片', '国产',   20, 35,    12.00, 'C-01-02', '通用供应商', 1);

-- 备件分类归位：从备件档案的分类字符串自动生成分类实体并回填 category_id
INSERT IGNORE INTO sp_part_category (category_name, sort)
SELECT DISTINCT category, 0 FROM sp_part
 WHERE category IS NOT NULL AND category <> '' AND deleted = 0;

UPDATE sp_part p
  JOIN sp_part_category c ON c.category_name = p.category
   SET p.category_id = c.id
 WHERE p.category_id IS NULL AND p.deleted = 0;

-- ---------------------------------------------------------------------
-- 12. SLA 政策（工时类数据，均以分钟计）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sla_policy (id, policy_name, level, response_minutes, resolve_minutes, verify_minutes, warn_percent, escalate_role_code, enabled, remark) VALUES
    (1, '紧急工单 SLA', 'P1', 30,  240,  120, 80, 'MANAGER',     1, '设备停机、安全风险'),
    (2, '严重工单 SLA', 'P2', 60,  480,  240, 80, 'MANAGER',     1, '主要功能受损'),
    (3, '一般工单 SLA', 'P3', 240, 1440, 480, 80, 'EQUIP_ADMIN', 1, '局部功能异常'),
    (4, '轻微工单 SLA', 'P4', 480, 2880, 960, 80, 'EQUIP_ADMIN', 1, '不影响使用');
