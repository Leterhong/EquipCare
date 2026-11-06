-- =====================================================================
-- 巡维云 EquipCare · 数据库结构定义
-- 文件：database/schema.sql
-- 说明：仅包含表结构（DDL），不含任何业务数据；可重复执行（CREATE TABLE IF NOT EXISTS）。
-- 数据库创建与账号授权见 database/00-create-database.sql。
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci · 引擎：InnoDB · 时区：Asia/Shanghai
--
-- 外键策略说明：
--   1) 主数据与主从关系使用物理外键，删除行为统一为 RESTRICT，防止误删主数据；
--   2) 高写入日志类表（状态日志、库存流水）同样建立外键并限制删除，保证"历史不可随意删除"；
--   3) 存在循环引用的关系（insp_anomaly.work_order_id 与 wo_work_order.anomaly_id）
--      使用逻辑关联 + 唯一索引约束，避免循环外键导致插入顺序死锁。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 一、系统域
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_dept (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级部门ID，0为根',
    dept_name      VARCHAR(80)     NOT NULL COMMENT '部门名称',
    dept_code      VARCHAR(50)     NOT NULL COMMENT '部门编码',
    leader_user_id BIGINT UNSIGNED NULL COMMENT '负责人用户ID',
    sort           INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_by      BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dept_code (dept_code),
    KEY idx_sys_dept_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门';

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(50)     NOT NULL COMMENT '登录名',
    password        VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希，禁止明文',
    real_name       VARCHAR(50)     NOT NULL COMMENT '真实姓名',
    phone           VARCHAR(20)     NULL COMMENT '手机号',
    email           VARCHAR(100)    NULL COMMENT '邮箱',
    dept_id         BIGINT UNSIGNED NULL COMMENT '所属部门',
    avatar          VARCHAR(300)    NULL COMMENT '头像地址',
    skill_tags      VARCHAR(200)    NULL COMMENT '技能标签，逗号分隔，派单参考',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    last_login_time DATETIME        NULL COMMENT '最近登录时间',
    last_login_ip   VARCHAR(50)     NULL COMMENT '最近登录IP',
    create_by       BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_phone (phone),
    KEY idx_sys_user_dept (dept_id),
    CONSTRAINT fk_sys_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code  VARCHAR(50)     NOT NULL COMMENT '角色编码，如 ADMIN',
    role_name  VARCHAR(50)     NOT NULL COMMENT '角色名称',
    data_scope VARCHAR(20)     NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL/DEPT/SELF',
    builtin    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否内置角色：1是（不可删除）',
    sort       INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark     VARCHAR(200)    NULL COMMENT '备注',
    create_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联';

CREATE TABLE IF NOT EXISTS sys_permission (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级权限ID，0为根',
    perm_code VARCHAR(80)     NOT NULL COMMENT '权限码，如 wo:workorder:dispatch',
    perm_name VARCHAR(50)     NOT NULL COMMENT '权限名称',
    perm_type VARCHAR(20)     NOT NULL COMMENT '类型：MENU/BUTTON/API',
    path      VARCHAR(120)    NULL COMMENT '前端路由路径（MENU）',
    component VARCHAR(120)    NULL COMMENT '前端组件路径（MENU）',
    icon      VARCHAR(50)     NULL COMMENT '菜单图标',
    sort      INT             NOT NULL DEFAULT 0 COMMENT '排序',
    visible   TINYINT         NOT NULL DEFAULT 1 COMMENT '菜单是否可见',
    status    TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_by BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (perm_code),
    KEY idx_sys_permission_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_perm (role_id, permission_id),
    KEY idx_sys_role_perm_perm (permission_id),
    CONSTRAINT fk_srp_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_srp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色权限关联';

CREATE TABLE IF NOT EXISTS sys_dict (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    dict_code  VARCHAR(50)     NOT NULL COMMENT '字典编码，如 fault_type',
    dict_name  VARCHAR(80)     NOT NULL COMMENT '字典名称',
    remark     VARCHAR(200)    NULL COMMENT '备注',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_code (dict_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典类型';

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    dict_code  VARCHAR(50)     NOT NULL COMMENT '字典编码',
    item_value VARCHAR(50)     NOT NULL COMMENT '字典值',
    item_label VARCHAR(80)     NOT NULL COMMENT '字典标签',
    sort       INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item (dict_code, item_value),
    KEY idx_sys_dict_item_code (dict_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典项';

CREATE TABLE IF NOT EXISTS sys_file (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    file_name      VARCHAR(200)    NOT NULL COMMENT '原始文件名',
    stored_name    VARCHAR(200)    NOT NULL COMMENT '存储文件名',
    file_path      VARCHAR(300)    NOT NULL COMMENT '相对路径',
    file_url       VARCHAR(300)    NOT NULL COMMENT '访问地址',
    file_size      BIGINT          NOT NULL COMMENT '字节数',
    content_type   VARCHAR(100)    NULL COMMENT 'MIME 类型',
    md5            VARCHAR(32)     NULL COMMENT '内容 MD5',
    biz_type       VARCHAR(30)     NULL COMMENT '业务类型',
    biz_id         BIGINT UNSIGNED NULL COMMENT '业务主键',
    upload_user_id BIGINT UNSIGNED NULL COMMENT '上传人',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id),
    KEY idx_sys_file_biz (biz_type, biz_id),
    KEY idx_sys_file_md5 (md5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '附件元数据';

CREATE TABLE IF NOT EXISTS sys_message (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    receiver_id BIGINT UNSIGNED NOT NULL COMMENT '接收人',
    title       VARCHAR(120)    NOT NULL COMMENT '标题',
    content     VARCHAR(500)    NULL COMMENT '内容',
    msg_type    VARCHAR(20)     NOT NULL COMMENT '类型：TASK/WORKORDER/SLA/SYSTEM',
    biz_type    VARCHAR(30)     NULL COMMENT '业务类型',
    biz_id      BIGINT UNSIGNED NULL COMMENT '业务主键',
    read_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    read_time   DATETIME        NULL COMMENT '阅读时间',
    send_status VARCHAR(20)     NOT NULL DEFAULT 'SENT' COMMENT '发送状态：SENT/FAILED/DEAD',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_message_receiver (receiver_id, read_flag),
    KEY idx_sys_message_receiver_time (receiver_id, create_time DESC),
    KEY idx_sys_message_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站内消息';

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id       VARCHAR(40)     NULL COMMENT '链路追踪ID',
    user_id        BIGINT UNSIGNED NULL COMMENT '操作人ID',
    username       VARCHAR(50)     NULL COMMENT '操作人账号快照',
    module         VARCHAR(50)     NOT NULL COMMENT '模块',
    action         VARCHAR(50)     NOT NULL COMMENT '动作',
    request_method VARCHAR(10)     NULL COMMENT 'HTTP 方法',
    request_uri    VARCHAR(200)    NULL COMMENT '请求路径',
    request_param  TEXT            NULL COMMENT '脱敏后的请求参数',
    result_status  VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS/FAIL',
    error_msg      VARCHAR(1000)   NULL COMMENT '错误信息',
    ip             VARCHAR(50)     NULL COMMENT '客户端IP',
    user_agent     VARCHAR(300)    NULL COMMENT 'UA',
    cost_ms        INT             NULL COMMENT '耗时毫秒',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_sys_audit_user_time (user_id, create_time),
    KEY idx_sys_audit_module_time (module, create_time),
    KEY idx_sys_audit_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作审计日志（只允许插入）';

CREATE TABLE IF NOT EXISTS sys_job_lock (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_name      VARCHAR(60)     NOT NULL COMMENT '任务名',
    lock_until    DATETIME        NULL COMMENT '锁到期时间',
    owner         VARCHAR(80)     NULL COMMENT '持有者',
    last_run_time DATETIME        NULL COMMENT '上次执行时间',
    last_result   VARCHAR(200)    NULL COMMENT '上次执行结果',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_job_lock_name (job_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '定时任务锁';

-- ---------------------------------------------------------------------
-- 二、设备域
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS eq_category (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级类别ID，0为根',
    category_code       VARCHAR(30)     NOT NULL COMMENT '类别编码，资产编号前缀',
    category_name       VARCHAR(60)     NOT NULL COMMENT '类别名称',
    default_template_id BIGINT UNSIGNED NULL COMMENT '默认巡检模板',
    maintain_cycle_days INT             NULL COMMENT '建议保养周期（天）',
    sort                INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_by           BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_category_code (category_code),
    KEY idx_eq_category_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备类别';

CREATE TABLE IF NOT EXISTS eq_location (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级位置ID，0为根',
    location_code VARCHAR(30)     NOT NULL COMMENT '位置编码',
    location_name VARCHAR(60)     NOT NULL COMMENT '位置名称',
    location_type VARCHAR(20)     NOT NULL DEFAULT 'ROOM' COMMENT '类型：PARK/BUILDING/FLOOR/ROOM',
    full_path     VARCHAR(300)    NOT NULL COMMENT '全路径，避免递归查询',
    sort          INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_by     BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_location_code (location_code),
    KEY idx_eq_location_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '位置';

CREATE TABLE IF NOT EXISTS eq_equipment (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_no            VARCHAR(40)     NOT NULL COMMENT '资产编号，唯一',
    equipment_name      VARCHAR(100)    NOT NULL COMMENT '设备名称',
    category_id         BIGINT UNSIGNED NOT NULL COMMENT '设备类别',
    location_id         BIGINT UNSIGNED NOT NULL COMMENT '所在位置',
    dept_id             BIGINT UNSIGNED NULL COMMENT '归属部门',
    brand               VARCHAR(60)     NULL COMMENT '品牌',
    model               VARCHAR(80)     NULL COMMENT '型号',
    spec                VARCHAR(120)    NULL COMMENT '规格参数',
    serial_no           VARCHAR(80)     NULL COMMENT '出厂序列号',
    manufacturer        VARCHAR(100)    NULL COMMENT '制造商',
    supplier            VARCHAR(100)    NULL COMMENT '供应商',
    purchase_date       DATE            NULL COMMENT '采购日期',
    install_date        DATE            NULL COMMENT '安装日期',
    warranty_end_date   DATE            NULL COMMENT '保修截止日期',
    original_value      DECIMAL(12, 2)  NULL COMMENT '设备原值（元）',
    responsible_user_id BIGINT UNSIGNED NULL COMMENT '设备责任人',
    status              VARCHAR(20)     NOT NULL DEFAULT 'IN_USE' COMMENT '状态：IN_USE/IDLE/REPAIRING/SCRAPPED',
    health_score        DECIMAL(5, 2)   NOT NULL DEFAULT 100.00 COMMENT '健康评分 0-100',
    last_inspect_time   DATETIME        NULL COMMENT '最近巡检时间',
    next_inspect_time   DATETIME        NULL COMMENT '下次应巡检时间',
    qr_token            VARCHAR(64)     NOT NULL COMMENT '二维码令牌',
    remark              VARCHAR(500)    NULL COMMENT '备注',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_by           BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_equipment_asset_no (asset_no),
    UNIQUE KEY uk_eq_equipment_qr (qr_token),
    KEY idx_eq_equipment_category (category_id),
    KEY idx_eq_equipment_location (location_id),
    KEY idx_eq_equipment_status (status),
    KEY idx_eq_equipment_dept (dept_id),
    KEY idx_eq_equipment_health (health_score),
    CONSTRAINT fk_eq_equipment_category FOREIGN KEY (category_id) REFERENCES eq_category (id) ON DELETE RESTRICT,
    CONSTRAINT fk_eq_equipment_location FOREIGN KEY (location_id) REFERENCES eq_location (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备台账';

CREATE TABLE IF NOT EXISTS eq_equipment_file (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    equipment_id BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    file_id      BIGINT UNSIGNED NOT NULL COMMENT '文件ID（sys_file.id）',
    file_type    VARCHAR(20)     NOT NULL DEFAULT 'ATTACHMENT' COMMENT '用途：IMAGE 设备图片 / ATTACHMENT 附件',
    sort_no      INT             NOT NULL DEFAULT 0 COMMENT '排序号，升序展示，首张图片作为设备封面',
    create_by    BIGINT UNSIGNED NULL COMMENT '上传人',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_equipment_file (equipment_id, file_id),
    KEY idx_eq_equipment_file_type (equipment_id, file_type, sort_no),
    CONSTRAINT fk_eq_equipment_file_equipment FOREIGN KEY (equipment_id) REFERENCES eq_equipment (id) ON DELETE CASCADE,
    CONSTRAINT fk_eq_equipment_file_file FOREIGN KEY (file_id) REFERENCES sys_file (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备图片与附件关联';

CREATE TABLE IF NOT EXISTS eq_health_snapshot (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    equipment_id    BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    stat_date       DATE            NOT NULL COMMENT '统计日期',
    health_score    DECIMAL(5, 2)   NOT NULL COMMENT '健康分',
    fault_score     DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT '故障扣分',
    timeout_score   DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT '超时扣分',
    anomaly_score   DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT '异常扣分',
    overdue_score   DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT '逾期扣分',
    bonus_score     DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT '巡检加分',
    work_order_count INT            NOT NULL DEFAULT 0 COMMENT '窗口内工单数',
    anomaly_count   INT             NOT NULL DEFAULT 0 COMMENT '窗口内异常数',
    open_wo_count   INT             NOT NULL DEFAULT 0 COMMENT '未完成维修工单数（待派单/待接单/维修中/待验收）',
    idle_days       INT             NULL COMMENT '距最近一次完成维修的天数（从未维修为 NULL）',
    risk_detail     MEDIUMTEXT      NULL COMMENT '逐项扣分明细 JSON：factor/deduction/detail/suggestion',
    level           VARCHAR(20)     NOT NULL COMMENT '等级：HEALTHY/SUB_HEALTHY/WARNING/DANGER',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_health_equip_date (equipment_id, stat_date),
    KEY idx_eq_health_date (stat_date),
    CONSTRAINT fk_eq_health_equipment FOREIGN KEY (equipment_id) REFERENCES eq_equipment (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备健康快照';

-- ---------------------------------------------------------------------
-- 三、巡检域
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS insp_template (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_code VARCHAR(40)     NOT NULL COMMENT '模板编码',
    template_name VARCHAR(100)    NOT NULL COMMENT '模板名称',
    category_id   BIGINT UNSIGNED NULL COMMENT '适用设备类别',
    version       INT             NOT NULL DEFAULT 1 COMMENT '版本号',
    status        VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED',
    item_count    INT             NOT NULL DEFAULT 0 COMMENT '巡检项数量',
    description   VARCHAR(500)    NULL COMMENT '说明',
    publish_time  DATETIME        NULL COMMENT '发布时间',
    publisher_id  BIGINT UNSIGNED NULL COMMENT '发布人',
    create_by     BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_template_code (template_code),
    KEY idx_insp_template_category (category_id),
    KEY idx_insp_template_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '巡检模板';

CREATE TABLE IF NOT EXISTS insp_template_item (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_id     BIGINT UNSIGNED NOT NULL COMMENT '所属模板',
    seq_no          INT             NOT NULL COMMENT '序号',
    item_name       VARCHAR(100)    NOT NULL COMMENT '检查项名称',
    item_type       VARCHAR(20)     NOT NULL COMMENT '类型：NUMBER/BOOL/ENUM/TEXT',
    unit            VARCHAR(20)     NULL COMMENT '单位',
    min_value       DECIMAL(12, 3)  NULL COMMENT '数值下限',
    max_value       DECIMAL(12, 3)  NULL COMMENT '数值上限',
    normal_value    VARCHAR(50)     NULL COMMENT '正常值',
    enum_options    VARCHAR(500)    NULL COMMENT '枚举项 JSON',
    required        TINYINT         NOT NULL DEFAULT 1 COMMENT '是否必填（必检项不允许跳过）',
    check_method    VARCHAR(200)    NULL COMMENT '检查方法',
    standard_ref    VARCHAR(200)    NULL COMMENT '检查标准/标准依据',
    abnormal_rule   VARCHAR(200)    NULL COMMENT '异常判定规则说明（数值区间之外的人工判定口径）',
    auto_work_order TINYINT         NOT NULL DEFAULT 0 COMMENT '异常是否自动转工单',
    photo_required  TINYINT         NOT NULL DEFAULT 0 COMMENT '判为异常时是否必须上传现场照片',
    abnormal_level  VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM' COMMENT '默认异常等级',
    create_by       BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_item_template_seq (template_id, seq_no),
    KEY idx_insp_item_template (template_id),
    CONSTRAINT fk_insp_item_template FOREIGN KEY (template_id) REFERENCES insp_template (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '巡检模板项';

CREATE TABLE IF NOT EXISTS insp_plan (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_code            VARCHAR(40)     NOT NULL COMMENT '计划编码',
    plan_name            VARCHAR(100)    NOT NULL COMMENT '计划名称',
    template_id          BIGINT UNSIGNED NOT NULL COMMENT '巡检模板',
    scope_type           VARCHAR(20)     NOT NULL COMMENT '作用域：EQUIPMENT/CATEGORY/LOCATION',
    scope_config         VARCHAR(500)    NULL COMMENT '作用域配置 JSON',
    executor_strategy    VARCHAR(20)     NOT NULL DEFAULT 'FIXED' COMMENT '执行人策略：FIXED/DEPT_OWNER/ROUND_ROBIN',
    executor_ids         VARCHAR(300)    NULL COMMENT '执行人ID列表 JSON',
    cycle_type           VARCHAR(20)     NOT NULL COMMENT '周期：DAILY/WEEKLY/MONTHLY',
    cycle_config         VARCHAR(300)    NULL COMMENT '周期配置 JSON',
    start_date           DATE            NOT NULL COMMENT '生效开始日期',
    end_date             DATE            NULL COMMENT '生效结束日期',
    sla_minutes          INT             NOT NULL DEFAULT 480 COMMENT '执行时限（分钟）',
    generate_ahead_hours INT             NOT NULL DEFAULT 24 COMMENT '提前生成小时数',
    status               VARCHAR(20)     NOT NULL DEFAULT 'DISABLED' COMMENT '状态：ENABLED/DISABLED',
    next_generate_time   DATETIME        NULL COMMENT '下次生成时间',
    last_generate_time   DATETIME        NULL COMMENT '上次生成时间',
    creator_id           BIGINT UNSIGNED NULL COMMENT '创建人',
    create_by            BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by            BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_plan_code (plan_code),
    KEY idx_insp_plan_status_next (status, next_generate_time),
    KEY idx_insp_plan_template (template_id),
    CONSTRAINT fk_insp_plan_template FOREIGN KEY (template_id) REFERENCES insp_template (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '巡检计划';

CREATE TABLE IF NOT EXISTS insp_plan_equipment (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_id      BIGINT UNSIGNED NOT NULL COMMENT '计划ID',
    equipment_id BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    seq_no       INT             NOT NULL DEFAULT 0 COMMENT '轮询顺序',
    assignee_id  BIGINT UNSIGNED NULL COMMENT '预分配执行人',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '展开时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_plan_equip (plan_id, equipment_id),
    KEY idx_insp_plan_equip_equipment (equipment_id),
    CONSTRAINT fk_ipe_plan FOREIGN KEY (plan_id) REFERENCES insp_plan (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ipe_equipment FOREIGN KEY (equipment_id) REFERENCES eq_equipment (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '计划作用域设备快照';

CREATE TABLE IF NOT EXISTS insp_task (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_code      VARCHAR(40)     NOT NULL COMMENT '任务编号',
    plan_id        BIGINT UNSIGNED NULL COMMENT '来源计划，手工创建为空',
    equipment_id   BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    template_id    BIGINT UNSIGNED NOT NULL COMMENT '模板版本快照',
    plan_date      DATE            NOT NULL COMMENT '计划执行日',
    assignee_id    BIGINT UNSIGNED NOT NULL COMMENT '执行人',
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/IN_PROGRESS/SUBMITTED/COMPLETED/OVERDUE/CANCELLED',
    source         VARCHAR(20)     NOT NULL DEFAULT 'AUTO' COMMENT '来源：AUTO/MANUAL',
    start_time     DATETIME        NULL COMMENT '开始时间',
    submit_time    DATETIME        NULL COMMENT '提交时间',
    finish_time    DATETIME        NULL COMMENT '完成时间',
    result         VARCHAR(20)     NULL COMMENT '结果：NORMAL/ABNORMAL',
    abnormal_count INT             NOT NULL DEFAULT 0 COMMENT '异常项数',
    sla_due_time   DATETIME        NOT NULL COMMENT '应完成时限',
    overdue_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否逾期',
    qr_verified    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否扫码进入',
    remark         VARCHAR(300)    NULL COMMENT '备注',
    create_by      BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_task_code (task_code),
    UNIQUE KEY uk_insp_task_plan_equip_date (plan_id, equipment_id, plan_date),
    KEY idx_insp_task_assignee_status (assignee_id, status),
    KEY idx_insp_task_equipment_date (equipment_id, plan_date),
    KEY idx_insp_task_plan_date (plan_date),
    KEY idx_insp_task_status_due (status, sla_due_time),
    CONSTRAINT fk_insp_task_plan FOREIGN KEY (plan_id) REFERENCES insp_plan (id) ON DELETE RESTRICT,
    CONSTRAINT fk_insp_task_equipment FOREIGN KEY (equipment_id) REFERENCES eq_equipment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_insp_task_template FOREIGN KEY (template_id) REFERENCES insp_template (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '巡检任务';

CREATE TABLE IF NOT EXISTS insp_task_item (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id          BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    template_item_id BIGINT UNSIGNED NOT NULL COMMENT '模板项ID',
    item_name        VARCHAR(100)    NOT NULL COMMENT '名称快照',
    item_type        VARCHAR(20)     NOT NULL COMMENT '类型快照',
    item_value       VARCHAR(500)    NULL COMMENT '填写值',
    result           VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '单项结果：NORMAL/ABNORMAL/SKIPPED',
    is_abnormal      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否异常（由 result 派生）',
    abnormal_level   VARCHAR(20)     NULL COMMENT '异常等级',
    remark           VARCHAR(300)    NULL COMMENT '说明/异常原因',
    skip_reason      VARCHAR(200)    NULL COMMENT '跳过原因（仅 SKIPPED 填写）',
    attachment_ids   VARCHAR(300)    NULL COMMENT '现场照片ID列表',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_task_item (task_id, template_item_id),
    KEY idx_insp_task_item_task (task_id),
    CONSTRAINT fk_insp_task_item_task FOREIGN KEY (task_id) REFERENCES insp_task (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '巡检任务明细';

CREATE TABLE IF NOT EXISTS insp_anomaly (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    anomaly_no       VARCHAR(40)     NOT NULL COMMENT '异常编号',
    task_id          BIGINT UNSIGNED NULL COMMENT '来源任务',
    equipment_id     BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    template_item_id BIGINT UNSIGNED NULL COMMENT '来源巡检项',
    item_name        VARCHAR(100)    NULL COMMENT '名称快照',
    level            VARCHAR(20)     NOT NULL COMMENT '等级：LOW/MEDIUM/HIGH/CRITICAL',
    description      VARCHAR(500)    NULL COMMENT '异常描述',
    attachment_ids   VARCHAR(300)    NULL COMMENT '现场照片',
    status           VARCHAR(20)     NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/CONVERTED/CLOSED',
    work_order_id    BIGINT UNSIGNED NULL COMMENT '关联工单（逻辑关联）',
    reporter_id      BIGINT UNSIGNED NULL COMMENT '上报人',
    report_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
    close_time       DATETIME        NULL COMMENT '关闭时间',
    close_remark     VARCHAR(300)    NULL COMMENT '关闭原因',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_anomaly_no (anomaly_no),
    KEY idx_insp_anomaly_equip_time (equipment_id, report_time),
    KEY idx_insp_anomaly_status (status),
    KEY idx_insp_anomaly_work_order (work_order_id),
    KEY idx_insp_anomaly_task (task_id),
    CONSTRAINT fk_insp_anomaly_task FOREIGN KEY (task_id) REFERENCES insp_task (id) ON DELETE RESTRICT,
    CONSTRAINT fk_insp_anomaly_equipment FOREIGN KEY (equipment_id) REFERENCES eq_equipment (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '巡检异常';

-- ---------------------------------------------------------------------
-- 四、工单域
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS wo_work_order (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no               VARCHAR(40)     NOT NULL COMMENT '工单编号',
    title                  VARCHAR(120)    NOT NULL COMMENT '工单标题',
    source                 VARCHAR(20)     NOT NULL COMMENT '来源：INSPECT/ANOMALY/MANUAL/PLAN_MAINTAIN',
    source_id              BIGINT UNSIGNED NULL COMMENT '来源单据ID',
    equipment_id           BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    anomaly_id             BIGINT UNSIGNED NULL COMMENT '触发异常ID，唯一，防重复建单',
    fault_type             VARCHAR(30)     NOT NULL COMMENT '故障类型（字典）',
    fault_desc             VARCHAR(1000)   NOT NULL COMMENT '故障描述',
    fault_image_ids        VARCHAR(500)    NULL COMMENT '故障现场照片文件ID，逗号分隔',
    level                  VARCHAR(10)     NOT NULL COMMENT '等级：P1/P2/P3/P4',
    priority               VARCHAR(10)     NOT NULL DEFAULT 'NORMAL' COMMENT '维修优先级：HIGH/NORMAL/LOW',
    status                 VARCHAR(30)     NOT NULL COMMENT '状态：PENDING_ASSIGN/PENDING_ACCEPT/IN_PROGRESS/PENDING_REVIEW/COMPLETED/REJECTED/CANCELLED',
    reporter_id            BIGINT UNSIGNED NOT NULL COMMENT '报修人',
    report_time            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报修时间',
    dispatcher_id          BIGINT UNSIGNED NULL COMMENT '派单人',
    dispatch_time          DATETIME        NULL COMMENT '派单时间',
    assignee_id            BIGINT UNSIGNED NULL COMMENT '维修人',
    accept_time            DATETIME        NULL COMMENT '接单时间',
    response_due_time      DATETIME        NULL COMMENT '响应时限',
    resolve_due_time       DATETIME        NULL COMMENT '修复时限',
    verify_due_time        DATETIME        NULL COMMENT '验收时限（提交验收时按政策 verify_minutes 计算）',
    suspend_start_time     DATETIME        NULL COMMENT '最近挂起开始时间',
    suspend_total_minutes  INT             NOT NULL DEFAULT 0 COMMENT '累计挂起分钟数',
    start_repair_time      DATETIME        NULL COMMENT '开始维修时间',
    submit_verify_time     DATETIME        NULL COMMENT '提交验收时间',
    repair_summary         VARCHAR(1000)   NULL COMMENT '维修总结',
    labor_hours            DECIMAL(6, 2)   NULL COMMENT '工时（小时）',
    verify_user_id         BIGINT UNSIGNED NULL COMMENT '验收人',
    verify_time            DATETIME        NULL COMMENT '验收时间',
    verify_result          VARCHAR(20)     NULL COMMENT '验收结果：PASS/REJECT',
    verify_remark          VARCHAR(500)    NULL COMMENT '验收意见',
    quality_score          TINYINT         NULL COMMENT '质量评分 1-5',
    actual_finish_time     DATETIME        NULL COMMENT '实际修复完成时间',
    close_time             DATETIME        NULL COMMENT '关单时间',
    spare_cost             DECIMAL(12, 2)  NOT NULL DEFAULT 0.00 COMMENT '备件成本',
    reject_count           INT             NOT NULL DEFAULT 0 COMMENT '验收驳回次数',
    reject_dispatch_count  INT             NOT NULL DEFAULT 0 COMMENT '拒接次数',
    sla_status             VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT 'SLA：NORMAL/WARN/OVERDUE/PAUSED',
    overdue_flag           TINYINT         NOT NULL DEFAULT 0 COMMENT '是否超时完结',
    prev_equipment_status  VARCHAR(20)     NULL COMMENT '创建前设备状态，取消时回滚',
    cancel_reason          VARCHAR(300)    NULL COMMENT '取消原因',
    version                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_by              BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by              BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wo_order_no (order_no),
    UNIQUE KEY uk_wo_anomaly (anomaly_id),
    KEY idx_wo_equipment_status (equipment_id, status),
    KEY idx_wo_priority_status (priority, status),
    KEY idx_wo_work_order_report_time (report_time),
    KEY idx_wo_assignee_status (assignee_id, status),
    KEY idx_wo_status_time (status, report_time),
    KEY idx_wo_sla (sla_status, resolve_due_time),
    KEY idx_wo_verify_due (verify_due_time),
    CONSTRAINT fk_wo_equipment FOREIGN KEY (equipment_id) REFERENCES eq_equipment (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '维修工单';

CREATE TABLE IF NOT EXISTS wo_status_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    from_status   VARCHAR(30)     NULL COMMENT '原状态，创建时为空',
    to_status     VARCHAR(30)     NOT NULL COMMENT '新状态',
    action        VARCHAR(30)     NOT NULL COMMENT '动作码',
    operator_id   BIGINT UNSIGNED NULL COMMENT '操作人',
    operator_role VARCHAR(50)     NULL COMMENT '操作人角色快照',
    reason        VARCHAR(300)    NULL COMMENT '原因/说明',
    cost_minutes  INT             NULL COMMENT '上一状态停留分钟数',
    create_time   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_wo_status_log_order (work_order_id, create_time),
    CONSTRAINT fk_wosl_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单状态流转日志（只允许插入）';

CREATE TABLE IF NOT EXISTS wo_dispatch_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    dispatch_type VARCHAR(20)     NOT NULL COMMENT '类型：ASSIGN/TRANSFER/REASSIGN/REVOKE',
    from_user_id  BIGINT UNSIGNED NULL COMMENT '原维修人',
    to_user_id    BIGINT UNSIGNED NULL COMMENT '新维修人',
    dispatcher_id BIGINT UNSIGNED NULL COMMENT '派单人',
    dispatch_time DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '派单时间',
    accept_time   DATETIME        NULL COMMENT '接单时间',
    reject_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否被拒接',
    reject_reason VARCHAR(300)    NULL COMMENT '拒接原因',
    remark        VARCHAR(300)    NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_wo_dispatch_order (work_order_id),
    KEY idx_wo_dispatch_to_user (to_user_id),
    CONSTRAINT fk_wodl_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '派单轨迹';

CREATE TABLE IF NOT EXISTS wo_repair_record (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    record_type   VARCHAR(20)     NOT NULL COMMENT '类型：START/DIAGNOSIS/PROCESS/FINISH/OTHER',
    content       VARCHAR(2000)   NOT NULL COMMENT '记录内容',
    before_image_ids VARCHAR(500) NULL COMMENT '维修前照片文件ID，逗号分隔',
    after_image_ids  VARCHAR(500) NULL COMMENT '维修后照片文件ID，逗号分隔',
    cost_minutes  INT             NULL COMMENT '本次耗时（分钟）',
    attachment_ids VARCHAR(300)   NULL COMMENT '附件ID列表',
    operator_id   BIGINT UNSIGNED NULL COMMENT '记录人',
    create_time   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录时间',
    PRIMARY KEY (id),
    KEY idx_wo_repair_order_type (work_order_id, record_type),
    KEY idx_wo_repair_order_time (work_order_id, create_time),
    CONSTRAINT fk_worder_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '维修过程记录';

CREATE TABLE IF NOT EXISTS wo_verify_record (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id  BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    verify_user_id BIGINT UNSIGNED NOT NULL COMMENT '验收人',
    verify_result  VARCHAR(20)     NOT NULL COMMENT '结果：PASS/REJECT',
    quality_score  TINYINT         NULL COMMENT '质量评分 1-5',
    remark         VARCHAR(500)    NULL COMMENT '验收意见',
    attachment_ids VARCHAR(300)    NULL COMMENT '验收照片',
    verify_time    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '验收时间',
    PRIMARY KEY (id),
    KEY idx_wo_verify_order (work_order_id),
    KEY idx_wo_verify_user (verify_user_id),
    CONSTRAINT fk_wovr_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '验收记录';

CREATE TABLE IF NOT EXISTS wo_evaluation (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id  BIGINT UNSIGNED NOT NULL COMMENT '工单ID，一张工单只允许一条评价',
    evaluator_id   BIGINT UNSIGNED NOT NULL COMMENT '评价人（报修人或管理员）',
    attitude_score TINYINT         NOT NULL COMMENT '服务态度 1-5',
    quality_score  TINYINT         NOT NULL COMMENT '维修质量 1-5',
    speed_score    TINYINT         NOT NULL COMMENT '响应速度 1-5',
    content        VARCHAR(500)    NULL COMMENT '文字评价',
    create_time    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wo_eval_order (work_order_id),
    KEY idx_wo_eval_score (quality_score),
    CONSTRAINT fk_woeval_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单服务评价';

-- ---------------------------------------------------------------------
-- 五、备件库存域
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

CREATE TABLE IF NOT EXISTS sp_part (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    part_no            VARCHAR(40)     NOT NULL COMMENT '备件编码',
    part_name          VARCHAR(100)    NOT NULL COMMENT '备件名称',
    category           VARCHAR(50)     NULL COMMENT '分类（历史快照，管理走 category_id）',
    category_id        BIGINT UNSIGNED NULL COMMENT '分类ID（分类实体）',
    spec               VARCHAR(120)    NULL COMMENT '规格型号',
    unit               VARCHAR(20)     NOT NULL DEFAULT '个' COMMENT '单位',
    brand              VARCHAR(60)     NULL COMMENT '品牌',
    safety_stock       INT             NOT NULL DEFAULT 0 COMMENT '安全库存',
    current_stock      INT             NOT NULL DEFAULT 0 COMMENT '当前库存',
    unit_price         DECIMAL(12, 2)  NOT NULL DEFAULT 0.00 COMMENT '单价（元）',
    warehouse_location VARCHAR(60)     NULL COMMENT '库位',
    supplier           VARCHAR(100)    NULL COMMENT '供应商',
    status             TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_by          BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sp_part_no (part_no),
    KEY idx_sp_part_status_stock (status, current_stock),
    KEY idx_sp_part_category (category_id),
    CONSTRAINT chk_sp_part_stock CHECK (current_stock >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '备件档案';

CREATE TABLE IF NOT EXISTS sp_stock_txn (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    txn_no       VARCHAR(40)     NOT NULL COMMENT '流水号',
    part_id      BIGINT UNSIGNED NOT NULL COMMENT '备件ID',
    txn_type     VARCHAR(20)     NOT NULL COMMENT '类型：IN/OUT/RETURN/ADJUST',
    biz_type     VARCHAR(30)     NULL COMMENT '业务类型：WORK_ORDER/PURCHASE/RETURN/CHECK',
    biz_id       BIGINT UNSIGNED NULL COMMENT '业务单ID',
    quantity     INT             NOT NULL COMMENT '变动数量',
    before_stock INT             NOT NULL COMMENT '变动前库存',
    after_stock  INT             NOT NULL COMMENT '变动后库存',
    unit_price   DECIMAL(12, 2)  NULL COMMENT '单价快照',
    amount       DECIMAL(12, 2)  NULL COMMENT '金额',
    operator_id  BIGINT UNSIGNED NULL COMMENT '操作人',
    txn_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    remark       VARCHAR(300)    NULL COMMENT '备注/冲正说明',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sp_txn_no (txn_no),
    KEY idx_sp_txn_part_time (part_id, txn_time),
    KEY idx_sp_txn_txn_time (txn_time),
    KEY idx_sp_txn_biz (biz_type, biz_id),
    CONSTRAINT fk_sp_txn_part FOREIGN KEY (part_id) REFERENCES sp_part (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存流水（不可修改，只能冲正）';

CREATE TABLE IF NOT EXISTS sp_work_order_part (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    part_id       BIGINT UNSIGNED NOT NULL COMMENT '备件ID',
    part_name     VARCHAR(100)    NOT NULL COMMENT '备件名称快照',
    quantity      INT             NOT NULL COMMENT '申请数量',
    unit_price    DECIMAL(12, 2)  NOT NULL COMMENT '单价快照',
    amount        DECIMAL(12, 2)  NOT NULL COMMENT '金额',
    apply_user_id BIGINT UNSIGNED NULL COMMENT '申请人',
    apply_time    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    audit_user_id BIGINT UNSIGNED NULL COMMENT '审批人',
    audit_time    DATETIME        NULL COMMENT '审批时间',
    issue_status  VARCHAR(20)     NOT NULL DEFAULT 'APPLIED' COMMENT '状态：APPLIED/ISSUED/REJECTED/RETURNED',
    issue_time    DATETIME(3)     NULL COMMENT '出库时间',
    txn_id        BIGINT UNSIGNED NULL COMMENT '对应出库流水',
    remark        VARCHAR(300)    NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_sp_wop_order (work_order_id),
    KEY idx_sp_wop_part (part_id),
    KEY idx_sp_wop_status (issue_status),
    CONSTRAINT fk_sp_wop_order FOREIGN KEY (work_order_id) REFERENCES wo_work_order (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sp_wop_part FOREIGN KEY (part_id) REFERENCES sp_part (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工单备件领用';

-- ---------------------------------------------------------------------
-- 六、SLA 域
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sla_policy (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    policy_name      VARCHAR(60)     NOT NULL COMMENT '政策名称',
    level            VARCHAR(10)     NOT NULL COMMENT '适用等级：P1/P2/P3/P4',
    response_minutes INT             NOT NULL COMMENT '响应时限（分钟）',
    resolve_minutes  INT             NOT NULL COMMENT '修复时限（分钟）',
    verify_minutes   INT             NOT NULL DEFAULT 240 COMMENT '验收时限（分钟）：提交验收后必须在此窗口内完成验收',
    warn_percent     INT             NOT NULL DEFAULT 80 COMMENT '预警阈值百分比',
    escalate_role_code VARCHAR(50)   NULL COMMENT '超时升级通知角色',
    enabled          TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark           VARCHAR(300)    NULL COMMENT '说明',
    create_by        BIGINT UNSIGNED NULL COMMENT '创建人',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        BIGINT UNSIGNED NULL COMMENT '更新人',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sla_policy_level (level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SLA 政策';

-- SLA 超时预警记录：同单同阶段每天至多一条（唯一键防刷屏），跨天仍会重新提醒
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
-- 七、不可篡改约束：审计日志、工单状态轨迹、库存流水「只允许插入」
--
-- 为什么用触发器而不是回收权限：
--   MySQL 在库级授权（GRANT ... ON db.*）之上无法按表回收权限（ERROR 1147），
--   而触发器对所有账号（包括 root）生效，能真正挡住 UPDATE / DELETE。
--   这对应第一阶段「工单操作历史不可随意删除」「库存流水必须支持追溯」的要求。
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS trg_sys_audit_log_no_update;
DROP TRIGGER IF EXISTS trg_sys_audit_log_no_delete;
DROP TRIGGER IF EXISTS trg_wo_status_log_no_update;
DROP TRIGGER IF EXISTS trg_wo_status_log_no_delete;
DROP TRIGGER IF EXISTS trg_sp_stock_txn_no_update;
DROP TRIGGER IF EXISTS trg_sp_stock_txn_no_delete;

DELIMITER $$
CREATE TRIGGER trg_sys_audit_log_no_update BEFORE UPDATE ON sys_audit_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is append-only: UPDATE is forbidden';
END$$

CREATE TRIGGER trg_sys_audit_log_no_delete BEFORE DELETE ON sys_audit_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is append-only: DELETE is forbidden';
END$$

CREATE TRIGGER trg_wo_status_log_no_update BEFORE UPDATE ON wo_status_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wo_status_log is append-only: UPDATE is forbidden';
END$$

CREATE TRIGGER trg_wo_status_log_no_delete BEFORE DELETE ON wo_status_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wo_status_log is append-only: DELETE is forbidden';
END$$

CREATE TRIGGER trg_sp_stock_txn_no_update BEFORE UPDATE ON sp_stock_txn
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sp_stock_txn is append-only: UPDATE is forbidden, use a reversing transaction';
END$$

CREATE TRIGGER trg_sp_stock_txn_no_delete BEFORE DELETE ON sp_stock_txn
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sp_stock_txn is append-only: DELETE is forbidden, use a reversing transaction';
END$$
DELIMITER ;
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
-- =====================================================================
-- 巡维云 EquipCare · AI 深化增量脚本（第九期）
-- 文件：database/09-ai-deepen.sql
-- 说明：维修知识库（RAG）文档表。幂等可重复执行；不写死库名。
-- 设计：轻量 RAG —— 文档向量以 JSON 存于行内，检索时全量载入内存
--       计算余弦相似度（知识库规模为百级文档，无需向量引擎）。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_kb_document (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识文档主键',
    title       VARCHAR(191)    NOT NULL COMMENT '标题（如：主轴异响排查手册）',
    content     TEXT            NOT NULL COMMENT '正文（建议单篇 200~4000 字，过长自动截断）',
    embedding   JSON            NULL COMMENT '标题+正文的嵌入向量（JSON 数组，检索用）',
    enabled     TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用（停用不参与检索）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_kb_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'AI 维修知识库（RAG 文档 + 行内向量）';
