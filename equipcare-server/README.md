# 巡维云 EquipCare · 后端服务

设备巡检与维修管理平台的服务端，Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis + JWT。

| 阶段 | 交付范围 | 状态 |
|---|---|---|
| 第一阶段 | PRD、业务流程与状态机、技术架构、ER 与表设计、接口规划、验收标准（见 `docs/*.md`） | 已交付 |
| 第二阶段 | 后端基础工程 + 数据库 + 统一基础能力 + 身份认证 + 巡检/工单/库存/健康分核心业务 | 已交付 |
| 第三阶段 | 用户与权限管理、设备资产中心（分类 / 档案 / 图片附件 / 维修巡检历史）、ZXing 设备二维码与扫码落地页、10 个前端页面 | 已交付 |
| 第四阶段 | 智能巡检中心：模板、计划、定时任务自动生成（幂等/时区/重启恢复）、三态巡检执行、异常转工单、日历与统计、8 个前端页面 | 已交付 |

前端为**零依赖原生实现**（原生 ES Module + hash 路由，无 Node / 无构建，图表用 CSS 手绘），由 Spring Boot 直接托管静态资源，打包后即为完整体。

---

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 及以上 | 编译目标为 17；实测 JDK 24 亦可（pom 已显式配置 Lombok 注解处理器） |
| Maven | 3.8+ | 本项目使用 3.9.11 验证 |
| MySQL | 8.0+ | 需支持 `utf8mb4_0900_ai_ci`、CHECK 约束（8.0.16+） |
| Redis | 5.0+ | 存放登录会话、令牌黑名单、登录失败计数 |

> 本项目为**模块化单体**：单个 Maven 模块，按包做模块隔离（`common` / `config` / `security` / `system` / `asset` / `inspection` / `workorder` / `inventory` / `analytics` / `notification` / `audit`）。
> 不使用微服务，避免个人开发下的分布式复杂度。

---

## 2. 目录结构

```
equipcare-server/                  # Maven 多模块父工程（packaging=pom）
├── equipcare-common/              # 通用基础：统一响应/异常/分页/工具、基础实体、@AuditLog 注解、
│                                  #   登录上下文（LoginUser/SecurityUtils/TokenStore）
├── equipcare-system/              # 系统管理：用户、角色、权限、部门、文件
├── equipcare-framework/           # 框架核心：JWT/Spring Security、MyBatis-Plus/Jackson/Web/异步/
│                                  #   OpenAPI 配置、操作审计（AOP 切面 + 审计日志）
├── equipcare-business/            # 业务模块（按业务域聚合，事件驱动：领域事件经事务性 outbox 投递）
│   └── src/main/java/com/equipcare/
│       ├── asset/       # 设备台账、类别、位置、图片附件、维修/巡检历史、ZXing 二维码
│       ├── inspection/  # 巡检模板、计划、任务生成（定时）、执行、异常单、日历统计
│       ├── workorder/   # 维修工单 + 状态机（核心）
│       ├── inventory/   # 备件档案、出入库、工单领用与退料
│       ├── analytics/   # 设备健康分与风险预警
│       ├── notification/# 站内消息
│       └── export/      # 通用数据导出（Excel）
├── equipcare-ai/                  # AI 智能副驾（第八期）：故障诊断（流式）/智能派单/维修报告，
│                                  #   OpenAI 兼容多 Provider，默认关闭、fail-open
├── equipcare-admin/               # 启动打包：主类、管理员引导、配置、静态前端、集成测试
│   └── src/main/resources/
│       ├── static/                # 第三/四阶段前端（原生 ES Module，无构建）
│       │   ├── index.html         # 登录页
│       │   ├── app.html           # 主控台（hash 路由容器）
│       │   ├── css/app.css        # 设计令牌与全站样式（含 A4 打印、扫码页、响应式）
│       │   ├── js/app.js          # 路由表、服务端菜单映射、权限守卫
│       │   ├── js/api.js          # 统一请求封装（Bearer、401 自动刷新、二进制转 blob）
│       │   ├── js/ui.js           # 表格/分页/弹层/提示等组件、字典与格式化工具
│       │   ├── js/pages/*.js      # 28 个业务页面 + 工单共用组件（巡检中心 8 页、工单中心 9 页）
│       │   └── m/scan.html        # 二维码扫码落地页
│       └── application.yml
├── database/
│   ├── 00-create-database.sql   # 建库 + 应用账号（DBA 执行一次）
│   ├── schema.sql               # 33 张表 DDL + 只插入触发器
│   ├── 03-stage3.sql            # 第三阶段增量：eq_equipment_file（已有库升级用，幂等）
│   ├── 04-stage4.sql            # 第四阶段增量：巡检判定规则/三项结果/日历统计权限/水泵演示数据（幂等，不写死库名）
│   ├── 05-stage5.sql            # 第五阶段增量：工单优先级/故障照片/维修前后照片/评价表/七态收敛/毫秒精度（幂等，不写死库名）
│   └── seed.sql                 # 演示基础数据（部门/角色/权限/字典/设备/备件），不含任何账号密码
├── scripts/
│   ├── smoke-e2e.py             # 第二阶段闭环冒烟（巡检→异常→工单→验收）
│   ├── stage3-smoke.py          # 第三阶段端到端冒烟（34 项断言）
│   ├── stage4-smoke.py          # 第四阶段端到端冒烟（35 项断言）
│   ├── stage5-smoke.py          # 第五阶段端到端冒烟（71 项断言：报修→派单→维修→验收→评价）
│   ├── ui-verify.py             # Playwright 真实浏览器联调 + 16 张截图
│   ├── ui-verify-stage4.py      # Playwright 巡检中心 8 页联调 + 15 张截图
│   └── ui-verify-stage5.py      # Playwright 工单中心 9 页三角色联调 + 19 张截图
└── equipcare-admin/target/equipcare-server.jar   # 构建产物（可执行 fat jar）
```

模块依赖自底向上无环：`common ← system ← framework`、`common ← system ← business`、`admin → 全部`。
每个业务模块统一分层：`controller` / `service` / `service.impl` / `mapper` / `entity` / `dto` / `vo`。

---

## 3. 快速开始

### 3.1 初始化数据库

```bash
# 1) 创建库与应用账号（把 ChangeMe_2026 改成你自己的密码）
mysql -u root -p < database/00-create-database.sql

# 2) 建表（33 张表 + 6 个只插入触发器）
mysql -u root -p equipcare < database/schema.sql

# 3) 导入演示基础数据（4 部门 / 6 角色 / 53 权限 / 12 台设备 / 12 种备件）
mysql -u root -p equipcare < database/seed.sql
```

**已有第二阶段库升级到第三阶段**：`schema.sql` 已包含新表，无需重跑。增量执行即可：

```bash
mysql -u root -p equipcare < database/03-stage3.sql       # 业务库
mysql -u root -p equipcare_test < database/03-stage3.sql  # 集成测试库
```

该脚本只新增 `eq_equipment_file`（设备图片/附件关联表），不影响既有数据。设备维修与巡检历史
未新增索引，直接复用第二阶段的 `idx_wo_equipment_status`、`idx_insp_task_equipment_date`。

**已有第三阶段库升级到第四阶段**（智能巡检中心）：

```bash
mysql -u root -p equipcare      < database/04-stage4.sql   # 业务库
mysql -u root -p equipcare_test < database/04-stage4.sql   # 集成测试库
```

脚本做四件事：给 `insp_template_item` 加「异常判定规则」「异常需现场照片」，给 `insp_task_item`
加「三项判定结果」「跳过原因」，回填历史数据的判定规则，再补「巡检日历 / 巡检统计」两个菜单与权限。

两点注意：

- **库名必须写在命令行**。脚本内不写 `USE`，`mysql` 的库名参数在管道后仍然生效，所以同一条命令
  可以落到任意目标库；漏写库名会直接报 `No database selected` 而不是静默写错库。
- **演示数据按前置条件守护**。脚本最后会尝试写入「工业水泵」巡检模板（对应验收示例的 5 个检查项）
  和一条每周巡检计划，但包在存储过程里，仅当目标库存在 `eq_category.id = 4` 且未删除时才写。
  测试库没有业务分类，会自动跳过，不会撞外键。

全新部署不需要这条脚本：`schema.sql` 已包含本阶段全部字段，`04-stage4.sql` 只服务于已上线环境的增量升级。

**已有第四阶段库升级到第五阶段**（维修工单中心）：

```bash
mysql -u root -p equipcare      < database/05-stage5.sql   # 业务库
mysql -u root -p equipcare_test < database/05-stage5.sql   # 集成测试库
```

脚本做五件事：

1. **加列**：`wo_work_order.priority`（维修优先级，默认 `NORMAL`）、`wo_work_order.fault_image_ids`（故障照片）、
   `wo_repair_record.before_image_ids` / `after_image_ids`（维修前后照片）；补 `idx_wo_priority_status`。
2. **加表**：`wo_evaluation`（一单一评，唯一索引 `uk_wo_eval_order` + 外键 `fk_woeval_order`）。
3. **收敛状态码**：`PENDING_DISPATCH→PENDING_ASSIGN`、`PENDING_VERIFY→PENDING_REVIEW`、`SUSPENDED→IN_PROGRESS`
   （同时回填 `start_repair_time`、清空 `suspend_*`、`sla_status` 由 `PAUSED` 改回 `NORMAL`）。
4. **提升时间精度**：5 张表的 7 个时间列由 `DATETIME` 升到 `DATETIME(3)`——秒级精度会让同一秒内的
   建单/派单/接单在时间线上乱序，毫秒是「时间线可读」的硬前提。
5. **补权限**：新增 `wo:workorder:workbench` / `wo:workorder:board` / `wo:workorder:evaluate`，按角色授予。

**状态码只在主表收敛，历史轨迹不动**：`wo_status_log` 有 append-only 触发器，旧状态码保留原样，
展示时由 `WorkOrderStatus.labelOf()` 映射成中文（如「待派单（历史）」）。迁移脚本末尾会自检
「遗留旧状态工单 = 0」，不为 0 就说明还有在途单没迁干净。

脚本全部用 `SET NAMES utf8mb4` + 反引号包裹列名，Windows 控制台（GBK）下也不会因中文 `COMMENT` 报 1064/1366。
加列与加索引都走存储过程判存在，重复执行安全。

**关于管理员账号**：`seed.sql` 刻意不创建任何账号与密码。首次部署请用一次性引导参数创建管理员：

```bash
java -jar equipcare-admin/target/equipcare-server.jar \
  --equipcare.bootstrap.enabled=true \
  --equipcare.bootstrap.username=admin \
  --equipcare.bootstrap.password='<引导时设置一个强口令>' \
  --equipcare.bootstrap.real-name=系统管理员
```

引导逻辑幂等：仅当该用户名不存在时创建，创建后请立即把 `bootstrap.enabled` 改回 `false`。

### 3.2 启动

```bash
export EQUIPCARE_JWT_SECRET='请替换为不少于32字节的随机字符串'
mvn spring-boot:run -pl equipcare-admin
# 或
mvn clean package -DskipTests && java -jar equipcare-admin/target/equipcare-server.jar
```

> 多模块工程：`mvn package` 会按 `common → system → framework/business → admin` 顺序构建，
> 可执行 jar 产物固定在 `equipcare-admin/target/equipcare-server.jar`。
> dev profile 的静态资源路径相对启动工作目录（`./equipcare-admin/src/main/resources/static/`），
> 请在 `equipcare-server/` 根目录启动，改前端刷新即生效。

启动成功后：

- 服务地址：<http://127.0.0.1:8080>
- 接口文档：<http://127.0.0.1:8080/swagger-ui.html>
- OpenAPI JSON：<http://127.0.0.1:8080/v3/api-docs>
- 健康检查：<http://127.0.0.1:8080/actuator/health>

### 3.3 验证登录

```bash
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<引导时设置的强口令>"}'
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": { "userId": 1, "username": "admin", "realName": "系统管理员", "roles": ["ADMIN"] }
  },
  "timestamp": 1761811200000
}
```

后续请求带上 `Authorization: Bearer <accessToken>` 即可。

---

## 4. 配置说明

所有敏感项均可通过环境变量覆盖，默认值只适用于本地开发。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `EQUIPCARE_DB_HOST` | `127.0.0.1` | MySQL 地址 |
| `EQUIPCARE_DB_PORT` | `3306` | MySQL 端口 |
| `EQUIPCARE_DB_NAME` | `equipcare` | 业务库名 |
| `EQUIPCARE_TEST_DB_NAME` | `equipcare_test` | 集成测试库名 |
| `EQUIPCARE_DB_USER` | `root` | 数据库账号（生产建议 `equipcare_app`） |
| `EQUIPCARE_DB_PASSWORD` | `123456` | 数据库密码 |
| `EQUIPCARE_REDIS_HOST` / `_PORT` / `_PASSWORD` / `_DB` | `127.0.0.1` / `6379` / 空 / `0` | Redis 连接 |
| `EQUIPCARE_JWT_SECRET` | 开发用占位值 | **必须替换**，长度 < 32 字节会拒绝启动 |
| `EQUIPCARE_UPLOAD_DIR` | `./data/upload` | 上传文件根目录 |
| `EQUIPCARE_SCAN_PREFIX` | `/m/scan.html` | 二维码扫码落地页路径前缀（令牌以 `?token=` 追加） |
| `EQUIPCARE_SCAN_BASE_URL` | 空 | 二维码中编码的站点根地址。**生产必须配置**（如 `https://equipcare.example.com`）；留空则输出相对路径，仅适合同域调试、跨设备扫码打不开 |
| `EQUIPCARE_BOOTSTRAP_ENABLED` | `false` | 是否允许一次性创建管理员 |
| `EQUIPCARE_LOG_DIR` | `./logs` | 日志目录 |
| `EQUIPCARE_INSPECTION_ZONE` | `Asia/Shanghai` | 巡检业务时区。**任务的「今天」由它决定，不跟随 JVM 默认时区** |
| `EQUIPCARE_INSPECTION_SCHEDULER` | `true` | 是否开启巡检任务自动生成（test profile 固定为 `false`） |

巡检调度器自身的节拍在 `application.yml` 的 `equipcare.inspection.scheduler` 下调整：

```yaml
equipcare:
  inspection:
    zone: Asia/Shanghai
    scheduler:
      enabled: true
      interval-ms: 60000        # 每 60 秒扫一轮（fixedDelay，上一轮跑完才开始计时）
      initial-delay-ms: 10000   # 启动后延迟 10 秒再跑第一轮，等连接池与缓存就绪
      backfill-days: 7          # 每轮回补最近 7 天，覆盖服务停机、计划补启用等场景
```

第六阶段新增两个调度器，同样在 `application.yml` 调整（`test` profile 已关闭，集成测试自行控制扫描时机）：

```yaml
equipcare:
  sla:
    scheduler:
      enabled: true          # SLA 超时扫描（每分钟一轮，同单同阶段同天只通知一次）
      interval-ms: 60000
      initial-delay-ms: 15000
  stock:
    alert:
      enabled: true          # 库存不足提醒（每小时一轮，同备件同天只提醒一次）
      interval-ms: 3600000
      initial-delay-ms: 20000
```

配置文件内置 3 个 profile：`dev`（打印 SQL）、`test`（测试库 + Redis db1）、默认（生产）。

---

## 5. 接口地址说明

统一前缀 `/api`，除登录与刷新外全部需要 JWT。统一响应结构：

```json
{ "code": 200, "message": "success", "data": {}, "timestamp": 1761811200000 }
```

分页响应 `data` 结构：`{ records, total, pageNum, pageSize, pages }`。

### 5.1 认证 `/api/auth`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录，返回双令牌 | 匿名 |
| POST | `/api/auth/refresh` | 用刷新令牌换新令牌 | 匿名 |
| POST | `/api/auth/logout` | 退出，令牌加入黑名单 | 登录 |
| GET | `/api/auth/me` | 当前用户信息（含部门/手机/邮箱/头像/最后登录时间） | 登录 |
| GET | `/api/auth/menus` | 当前用户菜单树 | 登录 |
| PUT | `/api/auth/password` | 修改密码，成功后强制下线 | 登录 |
| PUT | `/api/auth/profile` | 修改个人资料（姓名必填，手机/邮箱/头像可空） | 登录 |

### 5.2 系统管理 `/api/system`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST | `/api/system/users` | 用户分页 / 新增 |
| GET | `/api/system/users/options?keyword=` | 用户下拉选项（设备责任人选择用，最多 50 条，登录即可） |
| GET / PUT / DELETE | `/api/system/users/{id}` | 详情 / 编辑 / 删除 |
| PATCH | `/api/system/users/{id}/status?status=1` | 启用禁用（禁用即踢下线） |
| PUT | `/api/system/users/{id}/password/reset` | 重置密码 |
| GET / POST | `/api/system/roles` | 角色列表（含用户数） / 新增 |
| GET / PUT / DELETE | `/api/system/roles/{id}` | 角色详情 / 编辑 / 删除（内置或仍有用户占用时拒绝） |
| PUT | `/api/system/roles/{id}/permissions` | 分配权限 |
| GET | `/api/system/permissions` | 权限清单（含每个权限被多少角色引用） |
| GET | `/api/system/permissions/tree` | 权限树 |
| GET | `/api/system/depts/tree` | 部门树 |
| GET | `/api/system/audit-logs` | 审计日志分页 |
| GET | `/api/system/messages` | 我的消息 |
| GET | `/api/system/messages/unread-count` | 未读数 |
| POST | `/api/system/messages/{id}/read` | 标记已读 |
| POST | `/api/system/messages/read-all` | 全部已读 |
| POST | `/api/files/upload` | 上传文件 |
| GET | `/api/files/{id}` | 文件元信息 |

### 5.3 设备台账 `/api/asset`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/asset/equipment` | 设备分页（关键字/类别/位置/状态/部门/健康分区间） |
| GET | `/api/asset/equipment/{id}` | 设备详情 |
| POST | `/api/asset/equipment` | 新增（资产编号留空自动生成） |
| PUT | `/api/asset/equipment/{id}` | 编辑（不含状态） |
| PATCH | `/api/asset/equipment/{id}/status` | 状态变更（维修中禁止手工设置，报废必填原因） |
| DELETE | `/api/asset/equipment/{id}` | 逻辑删除（维修中不可删） |
| GET | `/api/asset/equipment/{id}/files` | 图片与附件列表（图片在前，按 `sort_no`） |
| POST | `/api/asset/equipment/{id}/files` | 绑定已上传文件（`fileType` = `IMAGE` / `ATTACHMENT`） |
| DELETE | `/api/asset/equipment/{id}/files/{fileId}` | 解绑（只删关联，不删物理文件） |
| GET | `/api/asset/equipment/{id}/repair-history?limit=20` | 维修历史（只读联查工单表） |
| GET | `/api/asset/equipment/{id}/inspect-history?limit=20` | 巡检历史（只读联查巡检表） |
| GET | `/api/asset/equipment/{id}/qrcode` | 二维码内容（扫码地址 + 令牌） |
| GET | `/api/asset/equipment/{id}/qrcode.png?size=320&margin=1` | 服务端渲染的 PNG 二维码（`Cache-Control: no-store`） |
| POST | `/api/asset/equipment/{id}/qrcode/regenerate` | 重置二维码令牌（旧码立即失效） |
| GET | `/api/asset/equipment/scan?token=` | 扫码聚合查询（需 `asset:equipment:view`） |
| GET | `/api/asset/categories/tree` | 类别树 |
| POST / PUT / DELETE | `/api/asset/categories[/{id}]` | 类别维护 |
| GET | `/api/asset/locations/tree` | 位置树 |
| POST / PUT / DELETE | `/api/asset/locations[/{id}]` | 位置维护（全路径自动刷新） |

**设备与系统模块的权限码**（后端 `@PreAuthorize` 硬校验，前端隐藏按钮只是体验优化）：

| 权限码 | 覆盖接口 |
|---|---|
| `asset:equipment:view` | 设备分页/详情、图片附件读取、维修与巡检历史、扫码查询、类别与位置树读取 |
| `asset:equipment:create` / `:update` / `:delete` | 新增 / 编辑（含附件绑定解绑）/ 逻辑删除 |
| `asset:equipment:status` | 状态变更（停用、报废等） |
| `asset:equipment:qrcode` | 二维码内容、PNG 图片、重置令牌 |
| `asset:category:manage` / `asset:location:manage` | 类别 / 位置维护 |
| `system:user:view` / `system:user:manage` | 用户查询 / 新增编辑禁用删除重置密码 |
| `system:role:manage` | 角色增删改、权限分配、权限清单 |
| `system:audit:view` | 操作记录查询 |

> `/api/system/users/options` 只要求登录——设备责任人下拉需要它，而巡检员、维修工并不该拥有用户管理权限。
> 该接口只返回 `id / 姓名 / 部门名`，不含手机号、状态等字段。

### 5.4 巡检 `/api/inspection`

**任务与执行**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/inspection/tasks` | 任务分页（状态/日期/设备/计划/`mine=1` 我的待执行） | `inspect:task:view` |
| POST | `/api/inspection/tasks` | 手工发起巡检（扫码/临时补检），模板为空取类别默认模板 | `inspect:task:execute` |
| GET | `/api/inspection/tasks/{id}` | 任务详情（检查项标准、判定规则、已产生异常、现场照片） | `inspect:task:view` |
| POST | `/api/inspection/tasks/{id}/start` | 开始巡检（可传 `qrToken` 防代签） | `inspect:task:execute` |
| POST | `/api/inspection/tasks/{id}/submit` | 提交巡检，**返回本次判为异常的项数**（不是填报项总数） | `inspect:task:execute` |
| GET | `/api/inspection/tasks/{id}/equipment-qrcode.png` | 任务对应设备的二维码 PNG（执行页扫码核对用，只产出 `image/png`） | `inspect:task:view` |

**异常单**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/inspection/anomalies` | 异常单分页（含对应工单号与状态） | `inspect:anomaly:view` |
| POST | `/api/inspection/anomalies/{id}/close?remark=` | 关闭异常（已转工单的不可直接关） | `inspect:anomaly:close` |
| POST | `/api/inspection/anomalies/{id}/work-order` | 异常转维修工单，可传等级/故障类型；同设备同故障已有在途工单时合并 | `inspect:anomaly:convert` |

**巡检模板**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/inspection/templates` | 模板分页（名称/分类/状态筛选） | `inspect:template:view` |
| GET | `/api/inspection/templates/options` | 模板下拉（计划绑定用，仅启用中） | `inspect:template:view` |
| GET | `/api/inspection/templates/{id}` | 模板详情（含按 `seq_no` 排序的检查项） | `inspect:template:view` |
| POST / PUT / DELETE | `/api/inspection/templates[/{id}]` | 新建 / 编辑（整表替换检查项） / 删除 | `inspect:template:manage` |
| POST | `/api/inspection/templates/{id}/publish` | 发布（至少一个检查项；发布后才可被计划绑定） | `inspect:template:manage` |
| POST | `/api/inspection/templates/{id}/disable` | 停用（已绑定计划不受影响，新计划不可选） | `inspect:template:manage` |

检查项的「能存进去但现场无法判定」在后端被拦：数值项必须给区间、枚举项必须有选项、
布尔项的「正常值」只能是 0/1、同模板内检查项名称不得重复，否则 400。

**巡检计划**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/inspection/plans` | 计划分页 | `inspect:plan:view` |
| GET | `/api/inspection/plans/{id}` | 计划详情（含作用域设备快照、周期配置、生成水位） | `inspect:plan:view` |
| POST / PUT / DELETE | `/api/inspection/plans[/{id}]` | 新建（**默认暂停**） / 编辑 / 删除 | `inspect:plan:manage` |
| PATCH | `/api/inspection/plans/{id}/status?status=ENABLED` | 启用 / 暂停。暂停立即停止生成，已生成的任务保留 | `inspect:plan:manage` |
| POST | `/api/inspection/plans/{id}/generate` | 立即生成（不等下一轮调度，便于现场验证配置；暂停中的计划会被拒） | `inspect:plan:generate` |

三条前置校验值得提前知道，否则会撞到 403：

- 计划只能绑定**已发布**的模板（`尚未发布，不能用于计划`）；
- 启用时校验模板至少有一个检查项、范围内至少有一台未报废设备；
- **删除前必须先暂停**（`请先暂停计划再删除`），避免删掉正在生成任务的计划。

计划的 `cycleConfig` 是 JSON，按周期类型取不同键：

```json
DAILY   {"time":"08:30"}
WEEKLY  {"time":"09:00","weekDays":[1,4]}
MONTHLY {"time":"09:00","monthDays":[1,15]}
```

`weekDays` 用 ISO 星期（周一 = 1）；`monthDays` 写 31 日在 30 天的月份顺延到当月最后一天，
避免「2 月永远不巡检」。

**日历与统计**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/inspection/calendar?month=yyyy-MM` | 月历聚合：每天的 总数/待执行/进行中/已提交/逾期/异常 | `inspect:calendar:view` |
| GET | `/api/inspection/stats?days=30` | 统计：总体指标、状态分布、每日趋势、计划维度、高频异常项 | `inspect:stats:view` |


### 5.5 维修工单 `/api/workorder`（状态机唯一入口）

**查询**

| 方法 | 路径 | 说明 | 权限码 |
|---|---|---|---|
| GET | `/api/workorder` | 分页。支持 `status` / `priority` / `level` / `keyword` / `assigneeId` / `priorityFirst`（按优先级+等级排序）；每行带 `allowedActions`、`evaluated` | `wo:workorder:view` |
| GET | `/api/workorder/{id}` | 详情：概况 + 流转轨迹 + 维修记录 + 验收记录 + 派单轨迹 + 故障照片 + 备件 + 评价 + 全量时间线 `history` | `wo:workorder:view` |
| GET | `/api/workorder/{id}/timeline` | 全量操作时间线（六类源记录归并，每条带 `sourceType`/`sourceId`） | `wo:workorder:view` |
| GET | `/api/workorder/{id}/parts` | 本单备件使用记录（验收人据此核对用料） | `wo:workorder:view` |
| GET | `/api/workorder/assignees` | 可选维修人，按在办工单数升序（负载低的排前面） | `wo:workorder:view` |
| GET | `/api/workorder/workbench?limit=10` | 工作台：待我接单 / 我维修中 / 待我验收 / 待派单 / 已驳回 / 我最近完成 + 全局状态计数 | `wo:workorder:workbench` |
| GET | `/api/workorder/status-count` | 各状态数量（可带 `assigneeId` 只看某人） | `wo:workorder:view` |

**动作**（全部是「动作接口」，没有也不会有 `PUT /{id}/status`）

| 方法 | 路径 | 动作与目标状态 | 允许角色 | 原因要求 |
|---|---|---|---|---|
| POST | `/api/workorder` | 报修 → `PENDING_ASSIGN` | `wo:workorder:create` | — |
| POST | `/api/workorder/{id}/dispatch` | 派单 / 重新派单 → `PENDING_ACCEPT` | ADMIN / EQUIP_ADMIN | — |
| POST | `/api/workorder/{id}/accept` | 接单 → `IN_PROGRESS` | 维修人本人（REPAIRMAN） | — |
| POST | `/api/workorder/{id}/reject` | 拒接 → `REJECTED` | 维修人本人 | ≥5 字 |
| POST | `/api/workorder/{id}/transfer` | 转派（换人，仍 `PENDING_ACCEPT`） | ADMIN / EQUIP_ADMIN | — |
| POST | `/api/workorder/{id}/revoke` | 撤回派单 → `PENDING_ASSIGN` | ADMIN / EQUIP_ADMIN | — |
| POST | `/api/workorder/{id}/start-repair` | 开始维修（**状态不变**，落 `START` + `DIAGNOSIS` 两条记录并起算工时） | 维修人本人 | 原因/诊断各 ≥5 字，维修前照片可选 |
| POST | `/api/workorder/{id}/records` | 过程记录（`DIAGNOSIS`/`PROCESS`/`FINISH`/`OTHER`），可带前后照片与耗时 | 维修人本人 | 内容非空 |
| POST | `/api/workorder/{id}/submit-verify` | 提交验收 → `PENDING_REVIEW` | 维修人本人 | 见下方前置条件 |
| POST | `/api/workorder/{id}/verify-pass` | 验收通过 → `COMPLETED`（**禁止自验收**，`3003`） | VERIFIER / EQUIP_ADMIN / ADMIN | — |
| POST | `/api/workorder/{id}/verify-reject` | 验收不通过 → `IN_PROGRESS` | 同上 | ≥10 字 |
| POST | `/api/workorder/{id}/cancel` | 取消 → `CANCELLED` | ADMIN / EQUIP_ADMIN | ≥5 字 |
| POST | `/api/workorder/{id}/force-transfer` | 强制转派（维修中换人，状态不变） | 仅 ADMIN | ≥5 字 |
| POST | `/api/workorder/{id}/evaluate` | 服务评价，一单一评 | `wo:workorder:evaluate` + 报修人本人或管理员 | 三维度各 1–5 分 |

补充说明：

- **`/start-repair` 不是状态迁移**。它是「接单之后」的动作，不改变 `status`，因此不进状态机动作表，
  改用 `WHERE start_repair_time IS NULL` 条件更新防重复——同一张单只能开始一次，第二次返回 `2004`。
- **`/submit-verify` 的五条前置条件**：① 已开始维修；② 至少 1 条 `FINISH` 记录；③ `FINISH` 带维修后照片或附件；
  ④ 没有待审批的备件申请；⑤ 维修总结非空。缺任何一条返回 `409 + 2004`，并在 `message` 里说清缺什么。
  状态不对时先报 `3001`（状态机优先），避免把「状态错了」误报成「条件没满足」。
- **派单参数**：`assigneeId`（必填）、`priority`（`HIGH`/`NORMAL`/`LOW`）、`resolveHours`（1–720，留空按故障等级默认时限）、
  `reason`（派单说明，写进派单轨迹）。
- **报修参数**：`equipmentId`、`title`、`faultType`、`faultDesc`、`level`（P1–P4，决定 SLA）、`faultImageIds`（逗号分隔文件 ID）。
- **评价参数**：`attitudeScore` / `qualityScore` / `speedScore`（各 1–5）+ `content`（≤500 字）。

### 5.5.1 状态机一览（7 状态 / 10 动作 / 14 条规则）

```
待派单 PENDING_ASSIGN ──派单──▶ 待接单 PENDING_ACCEPT ──接单──▶ 维修中 IN_PROGRESS
                                  │   ▲                            │
                          拒接 ◀──┘   └──转派/撤回                  │ 提交验收
                          ▼                                        ▼
                     已驳回 REJECTED                        待验收 PENDING_REVIEW
                          │                                        │
                    重新派单└──▶ 待接单                     通过 ──▶ 已完成 COMPLETED
                                                 不通过 ──▶ 维修中（原因≥10字）
```

| # | from | action | to | 角色 | 原因 |
|---|---|---|---|---|---|
| 1 | `PENDING_ASSIGN` | `DISPATCH` | `PENDING_ACCEPT` | ADMIN / EQUIP_ADMIN | — |
| 2 | `REJECTED` | `DISPATCH` | `PENDING_ACCEPT` | ADMIN / EQUIP_ADMIN | — |
| 3 | `PENDING_ACCEPT` | `ACCEPT` | `IN_PROGRESS` | REPAIRMAN（本人） | — |
| 4 | `PENDING_ACCEPT` | `REJECT_DISPATCH` | `REJECTED` | REPAIRMAN（本人） | ≥5 |
| 5 | `PENDING_ACCEPT` | `TRANSFER` | `PENDING_ACCEPT` | ADMIN / EQUIP_ADMIN | — |
| 6 | `PENDING_ACCEPT` | `REVOKE` | `PENDING_ASSIGN` | ADMIN / EQUIP_ADMIN | — |
| 7 | `IN_PROGRESS` | `SUBMIT_VERIFY` | `PENDING_REVIEW` | REPAIRMAN（本人） | — |
| 8 | `PENDING_REVIEW` | `VERIFY_PASS` | `COMPLETED` | VERIFIER / EQUIP_ADMIN / ADMIN | — |
| 9 | `PENDING_REVIEW` | `VERIFY_REJECT` | `IN_PROGRESS` | 同上 | ≥10 |
| 10–13 | `PENDING_ASSIGN` / `REJECTED` / `PENDING_ACCEPT` / `IN_PROGRESS` | `CANCEL` | `CANCELLED` | ADMIN / EQUIP_ADMIN | ≥5 |
| 14 | `IN_PROGRESS` | `FORCE_TRANSFER` | `IN_PROGRESS` | 仅 ADMIN | ≥5 |

未登记的 `(from, action)` 组合一律返回 `3001`，并在 `message` 里列出当前状态允许的动作，
前端据此渲染按钮，后端据此拒绝请求。

### 5.6 备件库存 `/api/inventory`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST / PUT / DELETE | `/api/inventory/categories[/{id}]` | 备件分类（重命名同步档案快照，非空分类不可删） |
| GET | `/api/inventory/parts` | 备件分页（keyword / categoryId / status / belowSafety） |
| GET | `/api/inventory/parts/below-safety` | 低于安全库存的备件 |
| POST / PUT / DELETE | `/api/inventory/parts[/{id}]` | 备件维护（**不含库存字段**，编码唯一） |
| POST | `/api/inventory/stock/in` | 入库 |
| POST | `/api/inventory/stock/out` | 直接出库（库存不足 4001/HTTP 422） |
| POST | `/api/inventory/stock/adjust` | 盘点调整（盘盈/盘亏，query 参数） |
| GET | `/api/inventory/stock/txns?partId=` | 单备件最近流水（含变动前后库存） |
| GET | `/api/inventory/stock/txns/page` | 流水分页（备件 / 类型 / 业务 / 时间段过滤） |
| GET | `/api/inventory/stats/cost` | 维修备件成本统计（按备件聚合工单领用） |
| POST | `/api/inventory/applies` | 工单备件申请（APPLIED，不扣库存） |
| GET | `/api/inventory/applies` | 领用申请分页（审批视角） |
| POST | `/api/inventory/applies/{id}/issue` | 审批出库（原子扣减 + 流水 + 成本归集） |
| POST | `/api/inventory/applies/{id}/reject` | 驳回申请 |
| POST | `/api/inventory/applies/{id}/return` | 退料入库（成本冲减） |

### 5.7 健康分析 `/api/analytics`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/analytics/health/{equipmentId}` | 健康分构成 + 风险明细 + 近 30 天走势 |
| POST | `/api/analytics/health/{equipmentId}/recalculate` | 重算并落快照（ADMIN / EQUIP_ADMIN） |
| POST | `/api/analytics/health/recalculate-all` | 批量重算 |
| GET | `/api/analytics/health/at-risk?threshold=75` | 风险设备列表 |

### 5.8 SLA 超时预警 `/api/sla`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/sla/policies` | 政策列表（P1–P4，响应 / 修复 / 验收时限、预警阈值） |
| PUT | `/api/sla/policies/{level}` | 修改政策（立即对新建工单生效） |
| GET | `/api/sla/active-orders` | 在途工单 SLA 视图（阶段、进度百分比、超时状态） |
| GET | `/api/sla/alerts` | 预警记录分页（阶段 / 等级过滤） |
| POST | `/api/sla/alerts/scan` | 手动触发一轮扫描，返回本轮通知数 |

### 5.9 运维数据驾驶舱 `/api/analytics/dashboard`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/analytics/dashboard/summary` | 14 项指标总览；`startDate`/`endDate` 缺省统计当月，跨度 ≤366 天 |
| GET | `/api/analytics/dashboard/charts` | 八张图表数据；参数约束同上 |

**指标口径**（全部由后端聚合，前端零计算；无数据返回 0/null，前端显示「暂无数据」，不编造）：

- **MTTR** = `AVG(actual_finish_time − start_repair_time)`（周期内完成、两时间非空的工单，单位分钟）；
- **MTBF** = `周期小时 × 正常运行设备数 ÷ 周期内非取消故障工单数`（单位小时）；
- 维修成本 = 周期内完成工单的备件费合计；备件消耗金额 = 周期内出库流水金额合计；
- 设备健康率 = `(IN_USE + IDLE) ÷ 未删除设备总数`；巡检完成率 = `SUBMITTED ÷ 周期内巡检任务`。

### 5.10 通知中心 `/api/system/messages`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/system/messages` | 我的消息分页（类型 / 读写状态过滤） |
| GET | `/api/system/messages/unread-count` | 未读数 |
| PATCH | `/api/system/messages/{id}/read` | 标记单条已读（**仅本人消息**，越权 403） |
| PATCH | `/api/system/messages/read-batch` | 批量标记已读（body 传 id 数组，归属校验逐条生效） |
| PATCH | `/api/system/messages/read-all` | 全部标记已读 |

通知触发点：巡检任务生成/手工建任务 → 执行人「巡检任务提醒」；巡检提交异常 → 管理员「巡检异常通知」；
报修建单/异常转工单 → 管理角色全员「新工单待派单」；派单 → 维修人「新工单待接单」；
SLA 扫描 → 超时预警；库存低于安全线 → 「库存不足」。

### 5.11 报表导出 `/api/export`（Excel）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/export/devices` | 设备清单（当前快照） |
| GET | `/api/export/inspections` | 巡检记录（按计划日期过滤） |
| GET | `/api/export/work-orders` | 维修工单（按报告时间过滤） |
| GET | `/api/export/inventory` | 备件库存（当前快照） |
| GET | `/api/export/cost-stats` | 维修成本统计（按完成时间过滤） |

全部为 POI `SXSSFWorkbook` 流式生成的真实 xlsx（`Content-Disposition: attachment`，文件名 URL 编码）；
每个接口都要求 `analytics:export` **加**对应业务查看权限（如设备清单还需 `asset:equipment:view`），
行数上限 5000——导出是给人看的报表，超量请缩小时间范围。导出动作写审计日志。

### 5.12 阶段八 · 前端统一设计规范（无新增后端接口）

第八阶段是纯前端阶段：全站切换为**深空工业主题**并补齐顶栏能力，后端只复用既有接口。

- **设计令牌**（`css/app.css` `:root`）：深空黑 `--bg:#0b0e14`、石墨灰面板 `--surface:#141821`、
  银灰文字 `--text:#e7eaf0`；强调色为工业绿 `--primary:#22c55e`，蓝色 `--info:#5b8def` 仅用于信息提示；
  全站无紫色（原「待验收/巡检发现」等紫色徽标统一映射为工业蓝灰）、无夸张渐变、打印纸张保持白底。
- **顶栏三件套**：全局搜索（设备/工单/备件三个真实接口并行检索，权限不足的域自动跳过，防抖 350ms）、
  消息铃铛（`unread-count` 30 秒轮询 + 红点徽标，点击进通知中心）、头像菜单（个人中心/消息/退出）。
- **侧边栏八组**：工作台 / 设备资产 / 巡检中心 / 维修工单 / 备品备件 / 运维分析 / 消息通知 / 系统管理，
  分组顺序固定，菜单仍由后端 `/api/auth/menus` 按权限过滤。
- **驾驶舱图表深色化**：ECharts 轴线/图例/提示框统一深色配色，八图共用工业绿调色板（无紫）。
- **异常状态全覆盖**：加载（骨架屏）→ 暂无数据（空态占位）→ 请求失败（toast + 重试）→
  无权限（前端 403 拦截页 + 后端 `@PreAuthorize` 双保险）→ 登录过期（清令牌跳登录页）。
- **移动端**：≤860px 侧边栏转横向滚动条、顶栏收紧、栅格折叠单列。

---

## 6. 前端页面

前端是原生 ES Module 单页应用，由 Spring Boot 从 `classpath:/static/` 直接托管，**无需 `npm install`、无构建步骤**。启动服务后访问 <http://127.0.0.1:8080/> 即为登录页。

### 6.1 页面清单与所需权限

| 页面 | 地址 | 所需权限 |
|---|---|---|
| 登录 | `/index.html` | 匿名 |
| 设备列表 | `/app.html#/equipment` | `asset:equipment:view` |
| 设备新增 | `/app.html#/equipment/new` | `asset:equipment:create` |
| 设备编辑 | `/app.html#/equipment/{id}/edit` | `asset:equipment:update` |
| 设备详情 | `/app.html#/equipment/{id}` | `asset:equipment:view` |
| 设备二维码 | `/app.html#/equipment/{id}/qrcode` | `asset:equipment:qrcode` |
| 设备档案打印 | `/app.html#/equipment/{id}/print` | `asset:equipment:view` |
| 设备分类 | `/app.html#/categories` | `asset:category:manage` |
| 巡检模板 | `/app.html#/inspect/template` | `inspect:template:view` |
| 巡检计划 | `/app.html#/inspect/plan` | `inspect:plan:view` |
| 巡检任务 | `/app.html#/inspect/task` | `inspect:task:view` |
| 巡检任务详情 | `/app.html#/inspect/task/{id}` | `inspect:task:view` |
| 巡检执行 | `/app.html#/inspect/task/{id}/execute` | `inspect:task:execute` |
| 巡检异常 | `/app.html#/inspect/anomaly` | `inspect:anomaly:view` |
| 巡检日历 | `/app.html#/inspect/calendar` | `inspect:calendar:view` |
| 巡检统计 | `/app.html#/inspect/stats` | `inspect:stats:view` |
| 工单列表 | `/app.html#/workorder` | `wo:workorder:view` |
| 报修 | `/app.html#/workorder/new` | `wo:workorder:create` |
| 工单详情 | `/app.html#/workorder/{id}` | `wo:workorder:view` |
| 派单工作台 | `/app.html#/workorder/dispatch` | `wo:workorder:board` |
| 维修人员工作台 | `/app.html#/workorder/workbench` | `wo:workorder:workbench` |
| 维修过程记录 | `/app.html#/workorder/{id}/repair` | `wo:workorder:repair` |
| 维修验收 | `/app.html#/workorder/{id}/verify` | `wo:workorder:verify` |
| 工单操作时间线 | `/app.html#/workorder/{id}/timeline` | `wo:workorder:view` |
| 工单评价 | `/app.html#/workorder/{id}/evaluate` | `wo:workorder:evaluate` |
| 备件管理 | `/app.html#/spare/part` | `spare:part:view` |
| 库存管理 | `/app.html#/spare/stock` | `spare:stock:view` |
| 备件入库 | `/app.html#/spare/stock/in` | `spare:stock:in` |
| 备件出库 | `/app.html#/spare/stock/out` | `spare:issue` |
| 库存流水 | `/app.html#/spare/txn` | `spare:txn:view` |
| 库存预警 | `/app.html#/spare/alert` | `spare:alert:view` |
| SLA 预警中心 | `/app.html#/sla/monitor` | `sla:monitor:view` |
| 设备健康列表 | `/app.html#/health` | `health:view` |
| 设备健康详情 | `/app.html#/health/{id}` | `health:view` |
| 运维数据驾驶舱 | `/app.html#/dashboard` | `dashboard:view` |
| 通知中心 | `/app.html#/messages` | 登录即可（只看自己的消息） |
| 用户管理 | `/app.html#/users` | `system:user:view` |
| 角色管理 | `/app.html#/roles` | `system:role:manage` |
| 权限管理 | `/app.html#/permissions` | `system:role:manage` |
| 用户操作记录 | `/app.html#/audit` | `system:audit:view` |
| 个人资料 | `/app.html#/profile` | 登录即可 |
| 扫码落地页 | `/m/scan.html?token=<令牌>` | `asset:equipment:view` |

### 6.2 实现约定

- **导航由后端菜单驱动**：`/api/auth/menus` 返回当前用户的菜单树，前端按 `path` 映射到本地路由。没有权限的菜单不会出现；手工改 hash 直接访问会渲染 403 说明页，并明确提示"绕过界面直接调接口同样会被拒绝"。
- **令牌不进 URL**：图片与二维码 PNG 用 `fetch` + `Authorization: Bearer` 取回后转 `blob:` 使用，避免令牌落到浏览器历史、代理日志与 `Referer` 里。
- **401 静默刷新**：`js/api.js` 遇到 401 会用刷新令牌重试一次；`1004` / `1005`（账号失效 / 令牌过期）直接清本地令牌并跳登录。
- **A4 打印**：设备档案打印使用 `@page size: A4; margin: 14mm`，打印时只保留档案卡，二维码、20 格信息表、最近巡检与维修、三方签字栏齐全。
- **巡检执行页三态**：正常 / 异常 / 跳过用按钮组而非下拉，减少现场误触；必检项的「跳过」直接禁用并提示原因；
  选「异常」才展开原因输入与照片上传；提交前逐项预检，**但最终判定以服务端返回为准**，前端预检只是为了少跑一趟。
- **统计图表零依赖**：巡检统计的分布条、每日趋势柱、计划维度表全部用 CSS 宽度/高度绘制，没有为四个图表引入 Chart.js。
- **分页上限**：后端 `PageQuery.pageSize` 上限 100，需要全量的地方（如计划编辑器的设备多选）逐页拉取，不要传 500。
- **无障碍降级**：`prefers-reduced-motion` 下关闭全部过渡动画；≤820px 侧边栏收起为顶栏下拉。
- 改前端无需重新打包：dev profile 已把静态资源目录指向 `file:./equipcare-admin/src/main/resources/static/`，刷新即生效。

---

## 7. 统一错误码

| 区间 | 含义 | 示例 |
|---|---|---|
| 200 | 成功 | — |
| 400 / 401 / 403 / 404 / 406 / 409 / 422 / 423 / 500 | 与 HTTP 语义对齐 | 406 响应格式不支持、422 参数校验失败 |
| 1xxx | 认证授权 | `1001` 用户名或密码错误、`1003` 账号锁定、`1005` 令牌过期 |
| 2xxx | 通用数据 | `2001` 数据不存在、`2002` 已存在（如设备编号重复）、`2003` 被引用无法删除、`2004` 业务规则不允许该操作 |
| 3xxx | 工单领域 | `3001` 非法状态流转、`3002` 状态已被他人变更、`3003` 禁止自验收 |
| 4xxx | 库存领域 | `4001` 库存不足 |
| 5xxx | 文件领域 | `5001` 空文件、`5002` 类型不允许、`5003` 超过大小 |

**403 与 2004 的区分**：「没有权限」返回 HTTP 403；「有权限但业务规则不答应」（如内置角色不可删、维修中的设备不能手工改成维修中）返回 HTTP 409 + `2004`。前端据此分别提示，不会把业务拒绝误报成越权。

非法流转示例（HTTP 409）：

```json
{ "code": 3001, "message": "工单当前状态「已完成」不支持动作「派单」，允许的动作：[]", "data": null, "timestamp": 1761811200000 }
```

---

## 8. 关键设计说明

### 8.1 工单状态机（本项目最重要的技术纪律）

- **7 个状态**：`PENDING_ASSIGN` 待派单 / `PENDING_ACCEPT` 待接单 / `IN_PROGRESS` 维修中 /
  `PENDING_REVIEW` 待验收 / `REJECTED` 已驳回 / `COMPLETED` 已完成 / `CANCELLED` 已取消
- **10 个动作，14 条合法转移**，见 `workorder/statemachine/WorkOrderStateMachine` 与 5.5.1 节表格
- 四道防线：
  1. **唯一入口**：`WorkOrderStateMachine.require(from, action)` 是取得目标状态的唯一途径；
  2. **转移表驱动**：未登记的 `(from, action)` 抛 `3001`，且异常信息列出允许动作；
  3. **动作式接口**：不接受前端传目标状态；
  4. **CAS 落库**：`UPDATE ... WHERE id=? AND status=? AND version=?`，影响行数 0 即 `3002`。
- 每次流转写一条 `wo_status_log`（含操作人、角色快照、原因、停留时长），该表由数据库触发器禁止 UPDATE/DELETE。
- **「开始维修」不进状态机**：它不改变状态，只是接单后的业务动作，用 `start_repair_time IS NULL` 做条件更新防重复；
  把它塞进状态机会造出一个「什么都没变」的伪迁移，反而让 7×N 的组合表失真。
- **旧状态码只做展示兜底**：早期数据里的 `PENDING_DISPATCH` / `PENDING_VERIFY` / `SUSPENDED` 已被迁移，
  但历史轨迹表不可改写，展示时由 `WorkOrderStatus.labelOf()` 映射为「待派单（历史）」等；
  这些历史状态**不能再次流转**，尝试流转会明确报错而不是猜一个目标态。

### 8.2 SLA 双轨制

业务状态由人推进，`sla_status`（`NORMAL`/`WARN`/`OVERDUE`）由系统计算。
超时只升级告警，**绝不修改工单业务状态**，避免「靠超时逃避考核」或「设备没修好工单却消失」。

时限来源分两层：**故障等级**（报修人填 P1–P4）决定默认响应/修复时限；**派单人填的 `resolveHours`** 可覆盖修复时限，
用于「这台设备今晚必须恢复」这类临时加压。验收不通过**不重置** SLA，返工时间仍然算在修复时长里——
否则返工越多、指标越好看。

阶段六补充——**时限以 `sla_policy` 表为准**（P1–P4 各自的响应/修复/验收分钟数 + 预警阈值百分比），
表缺行或停用时兜底到等级枚举默认值；政策修改立即对新建工单生效。

四个进行中阶段（待派单 / 待接单 / 维修中 / 待验收）各有「阶段起点 → 阶段截止」窗口，
`SlaServiceImpl.scan()` 全量重算：过线置 `OVERDUE`、达到预警线置 `WARN`、换阶段归位 `NORMAL`，
状态只前进不回退。定时器每分钟扫一轮（`equipcare.sla.scheduler.*` 可关），异常隔离在单轮内。

**同单同阶段同天只通知一次**：`sla_alert_record` 上有唯一键 `(work_order_id, stage, alert_date)`，
扫描时 `INSERT ... ON DUPLICATE KEY UPDATE alert_count = alert_count + 1`——返回 1（今天首次）才发站内通知，
返回 2 只累加计数不再通知。收件人 = 政策升级角色 + ADMIN + 当前维修人，每笔费用与提醒都可追溯。

### 8.3 巡检异常 → 工单闭环

1. 提交巡检时按模板 `abnormal_rule` + `min_value`/`max_value` **服务端复算**每项判定（`NORMAL` / `ABNORMAL` / `SKIPPED`），
   不信任前端传的结论；被判异常但没填原因的直接拒绝提交；
2. 异常项生成 `insp_anomaly`（含检查值、异常描述、现场照片 id、等级）；
3. 两种转单方式：模板项 `auto_work_order=1` 的提交时立即建单；其余由巡检员/管理员在异常页点「转维修工单」
   （`POST /anomalies/{id}/work-order`）。两者走同一个 `createFromAnomaly`；
4. `wo_work_order.anomaly_id` 唯一索引保证「一个异常只能生成一张工单」，重复调用幂等返回已有工单；
5. 同设备同故障类型已有在途工单时**合并**，避免工单风暴；合并后其余异常靠 `insp_anomaly.work_order_id` 反查，
   工单详情用 `mergedInspections` 补齐，**关联不丢、数据不复制**。

### 8.4 库存可追溯

- 库存数量只能通过入库/出库/退料/盘点变动，没有「直接改库存」的接口；
- 出库使用 `UPDATE ... WHERE current_stock >= qty` 原子扣减，配合 `CHECK (current_stock >= 0)`；
- 每条 `sp_stock_txn` 记录 `before_stock` / `after_stock`，可完整重放；
- 流水表由触发器禁止 UPDATE/DELETE，错误用反向流水冲正。

### 8.5 设备健康分（v2：八项可解释规则）

`health = clamp(100 - F - T - A - M - O - I - S + R, 0, 100)`，全部由后端规则计算，每项扣分都带「原始计数 + 计算规则 + 建议措施」（`riskItems`），前端不允许出现固定数字：

| 项 | 计算 | 上限 |
|---|---|---|
| F 故障扣分 | 近 30 天工单数×2 + 紧急工单数×3（取消的也计入窗口） | 30 |
| T 超时扣分 | 超时工单数×5 | 25 |
| A 异常扣分 | 异常数×2 + 严重异常数×2 | 20 |
| M 逾期扣分 | 逾期巡检数×5 | 15 |
| O 在途工单 | 未完成工单数×3（在途即负担，不限窗口） | 15 |
| I 久未维修 | 距上次完成维修 >180 天扣 5、>365 天扣 10，从未维修不扣 | 10 |
| S 运行状态 | 设备处于「闲置」扣 5（维修中不扣，报废不参与计算） | 5 |
| R 巡检加分 | 正常完成巡检数×1 | 10 |

等级：≥90 健康、≥75 亚健康、≥60 预警、其余危险。
逐项明细以 JSON 落 `eq_health_snapshot.risk_detail`，当日快照可随时回查；快照同时回写设备表冗余 `health_score` 供列表直读。

### 8.6 二维码：只编码随机标识

- 二维码内容 = `{scan-base-url}/m/scan.html?token=<32 位随机串>`，**不含账号、口令、JWT、手机号**。令牌存在 `eq_equipment.qr_token`，走唯一索引。
- 静态页 `/m/scan.html` 是公开文件，公开的只是一个空壳；页面拿到 `token` 后调 `/api/asset/equipment/scan`，该接口要求 `asset:equipment:view`——**未登录拿不到，无权限也拿不到**。
- 扫码返回体只含：设备编号、名称、类别、位置、状态、负责人姓名、封面图、最近 5 条巡检、5 条维修。责任人手机号、采购价等敏感字段一律不返回。
- PNG 由服务端 ZXing 渲染（M 级纠错），响应带 `Cache-Control: no-store`；尺寸限 120–1024、静区 0–8，防止构造超大图打爆内存。
- 重置令牌后旧码立即失效：已打印出去的旧标签扫出来只会得到 `2001 数据不存在`，需要重打标签。

### 8.7 权限必须由后端校验

- 所有业务接口标注 `@PreAuthorize("hasAuthority('...')")`，`@EnableMethodSecurity` 生效；前端 `auth.can(code)` 仅用于隐藏按钮。
- 验收用例专门验证"拿低权限角色的令牌直接打接口"返回 403，证明权限不依赖界面：巡检员访问用户管理 403、无 `asset:equipment:create` 时新增设备 403、无 `qrcode` 权限时取二维码 403、匿名 401。
- 权限码与 `@PreAuthorize` 字符串一一对应，因此权限管理页做成**只读清单**——改权限码等于改代码，角色授权统一在角色页完成。

### 8.8 设备图片与附件：先上传，再绑定

- `POST /api/files/upload` 只负责把字节写入 `EQUIPCARE_UPLOAD_DIR` 并登记 `sys_file`；`eq_equipment_file` 只存"设备 ↔ 文件"关联。
- 好处：设备保存与文件上传解耦（不必一次提交整个 multipart 表单）、同一文件可被多处引用、删设备只级联删关联而不误删物理文件。
- `file_type` 区分 `IMAGE` / `ATTACHMENT`，`IMAGE` 要求 `content_type` 为 `image/*`，防止把 PDF 当封面；列表与详情取 `sort_no` 最小的图片作封面。
- 上传即做类型与大小白名单（`.exe` 等直接 `5002`），落盘名统一重命名为 UUID，不使用原始文件名。

### 8.9 角色删除保护

- 内置角色（`builtin = 1`）不可删 → `2004`；仍有用户占用的角色不可删 → `2003`，并提示先解绑。
- 逻辑删除时把 `role_code` 改写为 `CODE_DEL_<id>`：`sys_role.role_code` 上有唯一索引，不释放就再也建不出同名角色。
- 删除同时清理 `sys_role_permission` 与 `sys_user_role`，不留脏授权。

### 8.10 巡检任务自动生成：幂等、时区、重启恢复

这是第四阶段最容易出事故的一段，四条不变量各自有机械保障：

**① 重复生成靠数据库，不靠内存标记**

```sql
UNIQUE KEY uk_insp_task_plan_equip_date (plan_id, equipment_id, plan_date)
```

生成前先 `countPlanEquipmentDate` 探一次（省掉大部分无效插入），再由 `INSERT IGNORE` 兜住并发与多实例。
`insertWithRetry` 专门区分两种失败：查得到 `(plan,equip,date)` 说明是正常重复，计入 `duplicate`；
查不到则是 `task_code` 撞了（同一天多计划编号并发），换编号重试最多 3 次。
**判重依据是「计划 + 设备 + 日期」而非「计划 + 周期」**——这样每月 1 日既是每日计划也是每月计划的执行日，
两条任务各自成立，不会被误判为重复。

**② 时区只认配置，不认 JVM 默认**

`InspectionClock` 绑定 `equipcare.inspection.zone`（默认 `Asia/Shanghai`），`@Scheduled` 也显式带同一个 `zone`。
容器默认 UTC 时若不显式指定，「每天早上 8 点巡检」会在北京时间 16 点生成任务。
库里 `plan_date` 存日期、`occurrence_time` 存当天计划时刻，判定"今天"永远走 `InspectionClock.today()`。

**③ 重启不丢、不重**

不维护任何内存水位。每轮都按 `[今天 - backfillDays(7), now + plan.generate_ahead_hours(默认 24h)]`
重新扫库，窗口起点再被 `plan.start_date` 收敛、终点被 `plan.end_date` 收敛。
好处是服务停机三天再启动，缺的任务自动补齐；而补的过程被唯一索引约束，已生成的一条也不会多。
`insp_plan.last_generate_time` 只作为观测水位，**不作为生成依据**，因此水位丢了对数据无影响。

**④ 异常与暂停都不产生脏数据**

- 单计划生成失败只累加 `errors` 并记日志，不影响同轮其它计划；`tick()` 整体异常被 catch，调度线程不会死。
- 生成与「过期处理」两段互相隔离：过期处理异常不会导致任务不生成，反之亦然。
- 暂停计划（`DISABLED`）不参与生成，但**已生成的任务保留**——现场可能已经在巡检，抹掉会让记录消失。
- 计划范围先展开成设备快照 `insp_plan_equipment`，每轮生成前 `syncScopeEquipment` 增量同步：
  新设备进范围内自动纳入，移出范围的不再生成新任务，历史任务仍指向真实设备。
- `PENDING` 任务超过 `plan_date + sla_minutes` 由 `expireOverdue()` 标为 `EXPIRED`，它不删记录、只改状态，仍可在列表里追责。


---

## 9. 测试

```bash
mvn test          # 单元测试（无需外部依赖）
mvn verify        # 单元测试 + 集成测试（需要 MySQL 与 Redis）
```

| 测试类 | 覆盖内容 |
|---|---|
| `WorkOrderStateMachineTest` | 7 状态 × 10 动作 = **70 种组合穷举**、角色校验、原因长度、终态、旧状态码兜底、全链路用例 |
| `HealthScoreCalculatorTest` | 健康分八项公式（v2）、上限截断、等级边界、闲置/在途/状态扣分 |
| `PageQueryTest` | 分页默认值、排序列 SQL 注入防护、参数校验 |
| `JwtTokenProviderTest` | 签发/解析、令牌类型区分、篡改拒绝、弱密钥拒绝启动 |
| `EquipCareApplicationIT` | 启动、MySQL、Redis、库表与约束、Swagger、异常处理、登录与鉴权、约束生效 |
| `Stage3IT` | 第三阶段 8 个用例：后端权限矩阵、设备编号唯一、状态变更规则、二维码生成与扫码、文件上传绑定解绑、角色删除保护、个人资料与手机号校验、逻辑删除 |
| `Stage4IT` | 第四阶段 9 个用例：模板校验（数值无区间/枚举无选项/布尔正常值非法/名称重复）、计划生命周期与作用域快照、每日/每周/每月三种周期生成、重复生成防护与重启回补、日历与任务二维码、三态提交与必检拦截、结构化项空值拒判正常、异常转工单与证据关联、四角色权限矩阵 |
| `Stage5IT` | 第五阶段 10 个用例：报修→派单→接单→维修→验收→评价全链路、非法状态跳转、防重复接单（并发双线程 CAS 竞争）、防重复提交验收、备件申请未审批阻塞验收、验收退回再维修、评价一单一评、四角色权限矩阵、时间线完整性（六类源记录归并且时间有序）、无直改状态接口（PUT/PATCH → 405） |
| `Stage6IT` | 第六阶段 10 个用例：备件分类与档案（编码唯一、库存字段不可直改）、入库/出库/盘点全落流水、库存不足 422、工单领用闭环（申请→审批出库→流水→成本 60 元）、**并发领料恰好一成一败且库存不为负**、SLA 政策修改即时生效、扫描置 OVERDUE + 预警登记 + 同天去重（upsert 返回值驱动）、健康分规则（干净设备 100 → 95 → 98 精确断言 + riskItems 逐项可解释）、权限矩阵、不存在直改库存入口 |
| `Stage7IT` | 第七阶段 9 个用例：驾驶舱空范围不编造（未来时间段全部归零/为 null、七图全空）、14 指标真实聚合（MTTR 与数据库同口径直算对齐）、八图真实数据（故障趋势/排行/维修人工作量/成本趋势）、消息已读流转（单条/批量/全部已读 + 未读数递减）、通知越权（批量中他人 id 被忽略）、通知触发（报修→「新工单待派单」、手工建任务→「巡检任务提醒」）、审计记录与口令脱敏、五类 Excel 导出（真实 xlsx + 表头 + Content-Disposition）、导出权限 403 |

当前全量 `mvn verify` 结果：**171 个测试全部通过**（单元 111 + `EquipCareApplicationIT` 14 + `Stage3IT` 8 + `Stage4IT` 9 + `Stage5IT` 10 + `Stage6IT` 10 + `Stage7IT` 9）。

集成测试前置：

```bash
mysql -u root -p < database/00-create-database.sql
mysql -u root -p equipcare_test < database/schema.sql
mysql -u root -p equipcare_test < database/03-stage3.sql
mysql -u root -p equipcare_test < database/04-stage4.sql
mysql -u root -p equipcare_test < database/05-stage5.sql
mysql -u root -p equipcare_test < database/06-stage6.sql
mysql -u root -p equipcare_test < database/07-stage7.sql
```

`04-stage4.sql` **不写死库名**，全靠命令行的库名参数落地，因此同一条命令既可用于 `equipcare`
也可用于 `equipcare_test`。脚本里的演示数据（工业水泵模板 + 每周计划）包在存储过程里，
仅当目标库存在「水泵」分类（`eq_category.id = 4`）时才写入，测试库会自动跳过。

测试会自建所需账号、角色、分类与位置并在 `@AfterAll` 清理，不依赖 `seed.sql`。
工单与其流转日志因 append-only 触发器不可删除，测试按业务规则保留，改以逻辑删除上游设备/分类/位置避开外键。

**第三阶段端到端冒烟（34 项断言）**

服务启动后执行，脚本纯标准库实现（无需 `requests`），不硬编码任何口令：

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
python scripts/stage3-smoke.py
```

覆盖：登录 → 新建分类/位置 → 新建设备 → 设备编号唯一性（重复返回 `2002`）→ 详情 →
状态变更（报废缺原因被拒）→ 上传 PNG 并绑定为封面 → 附件上传与解绑 → 二维码内容与 PNG 魔数校验 →
扫码聚合查询 → 完整走一遍用户/角色/权限/审计接口 → 清理。实测 **34/34 通过**。
`EQUIPCARE_BASE_URL` 可指定服务地址，默认 `http://127.0.0.1:8080`。

**前端真实联调（Playwright）**

```bash
pip install playwright
python scripts/ui-verify.py          # 需要本机已装 Chromium
# 或指定浏览器路径
export EQUIPCARE_CHROME='C:/path/to/chrome.exe'
python scripts/ui-verify.py
```

脚本用真实浏览器走完：登录 → 设备列表 → 新增 → 详情 → 上传图片 → 二维码 → 打印预览 →
用户/角色/权限/分类/审计/个人资料 → 手机尺寸下的扫码落地页，共 **16 张截图**输出到
`docs/screenshots/stage3/`，并收集 console 错误与 `pageerror`。实测 **0 控制台错误**。

**第四阶段端到端冒烟（35 项断言）**

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
python scripts/stage4-smoke.py
```

35 项断言，纯标准库实现（multipart 手写，不依赖 `requests`），按下列顺序走完：

1. 登录并按角色校验四个巡检权限点；
2. 建模板（5 个检查项，含数值区间、枚举、布尔、必检、要求留证）；
3. 建计划 → 断言**默认暂停** → 断言作用域快照展开为 2 台设备 → 启用；
4. 立即生成 → 断言首批生成数量 → **再次生成断言 0 新增（重复防护）**；
5. 提交三次：必检项跳过被拒、结构化项空值判正常被拒、正常提交并上传现场照片；
6. 异常转工单 → 断言工单带出 `sourceInspection`（含 `itemValue` 与照片），
   多张异常合并时靠 `mergedInspections` 补齐；
7. 日历与统计接口字段校验；
8. 清理测试数据。

实测 **35/35 通过**。`EQUIPCARE_BASE_URL` 可指定服务地址，默认 `http://127.0.0.1:8080`。

**第四阶段前端联调（Playwright，8 页 15 图）**

```bash
export EQUIPCARE_CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe'
python scripts/ui-verify-stage4.py
```

依次走完巡检模板 → 计划 → 任务 → 任务详情 → 执行 → 异常 → 日历 → 统计八个页面，
截图输出到 `docs/screenshots/stage4/`，同时监听 `console`、`pageerror` 与 HTTP ≥ 400（401 除外）。
实测 **15 张截图 / 0 控制台错误 / 0 异常响应**。脚本会在执行页真实点完三态按钮、
上传一张照片并提交，因此需要目标库处于可写入状态（建议用完清理）。

**第五阶段端到端冒烟（71 项断言）**

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
python scripts/stage5-smoke.py
```

71 项断言，纯标准库实现，用**产品自身的引导参数**创建临时管理员（不写死任何口令）：

```bash
java -jar equipcare-admin/target/equipcare-server.jar --server.port=8080 \
     --equipcare.bootstrap.enabled=true \
     --equipcare.bootstrap.username=smoke5admin \
     --equipcare.bootstrap.password='<一次性口令>'
```

覆盖：建临时角色/工号 → 报修（来源=手动，带故障照片）→ 断言初始 `PENDING_ASSIGN` →
非法状态跳转（未接单就开始维修/提交验收/验收全部 `3001`）→ 派单（优先级/时限/说明）→
派单历史 → 越权矩阵（验收人派单、维修人验收/取消均 403）→ 接单（**并发双线程只允许一人成功**）→
开始维修（第二次返回 2004）→ 过程记录 → 备件申请 → 备件未审批时提交验收被 `409+2004` 拦 →
审批备件 → 提交验收（重复提交 `3001`）→ 验收退回（原因≥10 字，回 `IN_PROGRESS`）→
再次维修再提交 → 验收通过 → 服务评价（评分越界 400、一单一评）→
工作台 / 状态统计 / 按状态筛选 → 清理（**停用**临时工号而非删除，保住审计链）。
实测 **71/71 通过**。

**第五阶段前端联调（Playwright，9 页三角色 19 图）**

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
export EQUIPCARE_CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe'
python scripts/ui-verify-stage5.py
```

四个浏览器上下文分别扮演管理员 / 报修人 / 维修人 / 验收人，真实走完
报修 → 提交 → 派单（弹层）→ 工单列表 → 详情 → 时间线 → 维修工作台接单 → 开始维修（带照片）→
过程记录 → 提交验收 → 验收通过 → 评价（三星级 + 文字）→ 已评价详情，
共 **19 张截图**输出到 `docs/screenshots/stage5/`。
同时监听 `console`、`pageerror` 与 HTTP ≥ 400（401 除外），每次导航失败都会把页面提示条文案带进报错。

**第六阶段端到端冒烟（56 项断言）**

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
python scripts/stage6-smoke.py
```

56 项断言，纯标准库实现，与验收清单一一对应：

1. **库存扣减事务**：入库/出库/盘盈盘亏每步都断言库存值与流水类型，超量出库 4001/HTTP 422 且库存不变；
2. **并发领料**：库存精确降到 1，两个工单的领用同时审批，**恰好一成一败（422 + 4001）**，库存不为负、出库流水只多一条；
3. **库存不足**：零库存直接出库被拒；低于安全库存进入预警列表（`/parts/below-safety`）；
4. **SLA 超时**：P4 响应时限改到 1 分钟，**真实等 65 秒**后手动扫描 → 工单 `OVERDUE`、预警记录恰 1 条、活跃视图标记超时进度、**第二次扫描不再新增记录**（唯一键去重）；政策修改后在收尾恢复原值；
5. **健康评分规则**：干净设备 100 分 → 建在途工单 95 分（故障 -2 + 在途 -3）→ `riskItems` 逐项带明细与建议 → 取消工单后 98 分（取消的工单仍计入故障窗口）。

另有：分类重命名同步快照、档案更新携带 `currentStock` 不生效、退料入库与成本冲减、
成本统计按备件聚合（数量 2 / 金额 60）、维修人 403 矩阵（入库 / 审批 / 改政策）。
实测 **56/56 通过**。

**第六阶段前端联调（Playwright，9 页 14 图）**

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
export EQUIPCARE_CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe'
python scripts/ui-verify-stage6.py
```

依次走完备件管理（分类管理弹层）→ 库存管理（盘点调整弹层）→ 备件入库（**界面真实提交 1 件**）→
备件出库（**界面真实提交 1 件**）→ 库存流水 → 库存预警 → SLA 预警中心（立即扫描）→
设备健康列表 → 设备健康详情（风险明细 + 建议），
截图输出到 `docs/screenshots/stage6/`。入库 / 出库提交后都会通过接口回查库存，
确保「界面操作真的改了数据」。实测 **14 张截图 / 0 控制台错误 / 0 异常响应**。
实测 **19 张截图 / 0 控制台错误 / 0 异常响应**。

**第七阶段端到端冒烟（35 项断言）**

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
python scripts/stage7-smoke.py
```

35 项断言，纯标准库实现，与验收清单一一对应：

1. **驾驶舱真实数据**：14 指标字段齐全、健康率 0~100；未来时间范围完成/超时工单归零、MTTR/MTBF 为 null、七张时间图全空（**不编造**）；跨度 >366 天被 400 拒绝；八图字段齐全且设备状态分布为当前快照；
2. **通知中心**：报修建单后管理员收到「新工单待派单」且未读数增加 → 批量已读恰好减少对应数量 → 越权标记他人消息 403 且状态不变 → 全部已读后未读数为 0；
3. **操作审计**：`module=认证` 可查到登录记录、请求参数不含明文口令、按 `resultStatus` 过滤可用；
4. **Excel 导出五类**：全部为真实 xlsx（PK 头 + `Content-Disposition: .xlsx`）；
5. **权限矩阵**：无角色用户访问驾驶舱 / 导出 / 查审计均 403。

临时数据收尾：报修工单走接口取消、临时用户停用（不删除，保住审计链）。实测 **35/35 通过**。

**第七阶段前端联调（Playwright，3 页 8 图）**

```bash
export EQUIPCARE_ADMIN_USERNAME='<管理员账号>'
export EQUIPCARE_ADMIN_PASSWORD='<该账号口令>'
export EQUIPCARE_CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe'
python scripts/ui-verify-stage7.py
```

依次走完运维驾驶舱（14 KPI + 8 张 ECharts，切「近 90 天」、自定义非法范围提示）→
通知中心（勾选消息批量标记已读）→ 用户操作记录（模块筛选查询），
截图输出到 `docs/screenshots/stage7/`。驾驶舱断言 KPI ≥14 张、图表位 ≥8 个（canvas 或「暂无数据」占位）。
实测 **8 张截图 / 0 控制台错误 / 0 异常响应**。

**第八阶段前端统一优化 + 全面联调（Playwright，多角色）**

```bash
export EQUIPCARE_ADMIN_USERNAME='e2e_admin'
export EQUIPCARE_ADMIN_PASSWORD='<口令>'
python scripts/ui-verify-stage8.py
```

分两层，全部使用真实角色账号（`e2e_admin` / `e2e_repair` / `e2e_verify` / `e2e_inspect`）：

1. **多角色业务闭环（API）**：管理员报修建单 → 派单给维修工（含维修后照片上传）→
   维修工接单/开始维修/过程记录/完工说明/提交验收 → 验收员验收通过，
   最终断言工单状态 `COMPLETED`；每一步以后端 `@PreAuthorize` 独立校验角色。
2. **UI 交互与异常状态（浏览器）**：深色主题生效（body 背景色断言）、顶栏三件套
   （全局搜索 12 条真实命中 / 铃铛跳转通知中心 / 头像菜单进个人中心）、驾驶舱 14 KPI + 8 图、
   设备列表→详情→二维码、闭环工单时间线 11 节点、巡检员越权访问 `/users` 被拦截页拦下、
   登录过期自动跳登录、错误口令明确提示、移动端 375×812 自适应。

实测 **15 张截图 / 0 控制台错误 / 0 非预期 HTTP 错误**（截图输出 `docs/screenshots/stage8/`）。

**第二阶段闭环冒烟（可选）**

```bash
python scripts/smoke-e2e.py
```

脚本会真实走一遍核心闭环：手工发起巡检 → 扫码开始 → 提交时数值超限自动生成异常 →
异常自动转工单 → 派单 → 维修人接单 → 写诊断与完工记录 → 提交验收 →
自验收被拦截 → 管理员验收通过 → 已完成工单再次派单被 3001 拦截 → 查询流转轨迹与健康分。
同样通过 `EQUIPCARE_ADMIN_PASSWORD` 传入口令。

---

## 10. 常见问题

**启动报 `equipcare.jwt.secret 未配置或长度不足 32 字节`**
→ 设置 `EQUIPCARE_JWT_SECRET` 为不少于 32 字节的随机串（如 `openssl rand -base64 48`）。

**`mvn verify` 报 `UnsupportedClassVersionError ... class file version 61.0 ... up to 52.0`**
→ Maven 实际用的是 Java 8（52.0 即 Java 8），而 Spring Boot 3 需要 17+。先确认 `mvn -version` 输出的 Java 版本，
再 `export JAVA_HOME=<JDK 17+ 路径>` 后重跑。本机验证环境为 JDK 24 + `maven-compiler-plugin release 17`。

**启动报 `Access denied for user 'equipcare_app'`**
→ `00-create-database.sql` 中的密码与 `EQUIPCARE_DB_PASSWORD` 不一致；或该账号未被授权访问业务库。

**登录返回 `1003 账号已锁定`**
→ 连续 5 次密码错误触发 10 分钟锁定（Redis 计数）。开发期可用 `redis-cli -n 0 KEYS 'equipcare:auth:login-fail:*'` 查看并删除。

**`Cannot add or update a child row: a foreign key constraint fails`**
→ 演示数据顺序问题：请务必按 `00-create-database.sql` → `schema.sql` → `seed.sql` 顺序执行。

**`/actuator/health` 返回 `503` / `redis: DOWN`**
→ 部分旧版本 Windows 移植版 Redis（如 5.0.14.1）会让 Lettuce 解析 `INFO` 失败并报
`Cannot read Redis info`。本项目已在 `application.yml` 中关闭 Redis 健康指示器，
Redis 的实际可用性由集成测试的 `PING` 断言与真实登录会话验证；若部署在标准 Redis 6+ 上，
可把 `management.health.redis.enabled` 改回 `true`。

**工单流转报 `3002 工单状态已变更`**
→ 乐观锁冲突，说明他人已先一步操作。刷新详情后重试即可，这正是防止「两个维修人同时接单」的保护。

**扫码打开是白屏 / 提示"未登录"**
→ `/m/scan.html` 是公开静态页，但数据接口要 `asset:equipment:view`。未登录时页面会给出"去登录"按钮；
若已登录仍空白，检查该账号所属角色是否授予了设备查看权限。

**手机扫二维码打不开（电脑上能打开）**
→ `EQUIPCARE_SCAN_BASE_URL` 未配置，二维码里编码的是相对路径 `/m/scan.html?...`，手机上没有站点上下文。
生产必须配置成完整域名，如 `EQUIPCARE_SCAN_BASE_URL=https://equipcare.example.com`，然后对受影响设备**重新生成二维码**。

**访问 `/api/asset/equipment/{id}/qrcode.png` 返回 406**
→ 该接口只产出 `image/png`。用 curl / 脚本请求时带上 `-H 'Accept: image/png'`；
浏览器的 `<img src>` 与前端 `fetch` 已自动处理。

**设备详情里上传的图片不显示**
→ 图片接口需要 Bearer 令牌，直接用 `<img src="/api/...">` 会被拦。前端已用 `fetch` 取回后转 `blob:` 显示；
自定义页面请复用 `js/api.js` 的 `api.objectUrl()`。

**删除角色报 `2003` / `2004`**
→ `2003` 表示仍有用户绑定该角色，先去用户管理改掉这些用户的角色；`2004` 表示这是内置角色（如系统管理员），不允许删除。

**新建了巡检计划，但一条任务都没生成**
→ 按这个顺序查：
1. 计划状态。新建计划**默认是暂停**（防止配错范围就大规模生成），确认范围无误后点「启用」或
   `PATCH /plans/{id}/status?status=ENABLED`。
2. 计划起止日期。`start_date` 晚于今天、或 `end_date` 已过，都不会生成。
3. 范围里有没有设备。分类/位置下必须存在未报废设备，否则 `syncScopeEquipment` 展开为空，
   生成时抛「范围内没有可巡检设备」。
4. 执行人解析是否成功。`EXECUTOR_STRATEGY` 为 `FIXED` 时必须指定执行人；解析不到人的计划只累加
   `noExecutor` 计数并跳过，日志里能看到「无执行人 N 条」。
5. 调度器是否被关掉。`EQUIPCARE_INSPECTION_SCHEDULER=false` 或 `test` profile 下不会自动跑，
   可先手动 `POST /plans/{id}/generate` 验证配置。

**首次启用计划后一次多出好几天的任务**
→ 正常。生成窗口是 `[今天 - backfill-days(7), now + generate_ahead_hours(默认 24h)]`，
用于覆盖停机与补启用场景，多出来的都是**过去日期**的任务，会立刻进入待执行/逾期，不会重复。
只想要今天开始的，把 `backfill-days` 调小或把计划的 `start_date` 设为今天。

**提交巡检返回的数字比填报项少**
→ 该接口返回的是**本次判为异常的项数**，不是填报项总数（`200` + `data: 1` 表示「1 项异常」）。

**必检项点「跳过」没反应**
→ 前端按模板 `required` 直接禁用该按钮，后端 `submit` 也会拒绝。必检项只能选正常或异常，
这是「必检」的定义，不是缺陷。

**布尔项/枚举项不填值判「正常」被拒（400）**
→ 结构化项（`BOOLEAN` / `ENUM` / `NUMBER`）的空值判正常等于没检查，历史记录也失去复核依据，
因此服务端强制要求填实测值或选择结果。文本项可由巡检人人工判定，不受此限。

**异常转工单后，工单详情里只看到一条巡检来源**
→ 同设备同故障类型已有在途工单时会**合并**（防工单风暴），`wo_work_order.anomaly_id` 只记住第一张异常单。
其余异常通过 `insp_anomaly.work_order_id` 反查，由 `WorkOrderDetailVO.mergedInspections` 逐条返回，
照片与检查值都在。若前端只读 `sourceInspection` 就会漏，请把两块一起展示。

**列表里出现「待派单(旧)」「待验收(旧)」这类标签**
→ 历史工单的状态日志（`wo_status_log`）由 append-only 触发器保护，里面的
`PENDING_DISPATCH` / `PENDING_VERIFY` / `SUSPENDED` 是旧状态码，**不允许改写历史**。
主表现已收敛为七态，展示层由 `WorkOrderStatus.labelOf()` 与前端字典兜底翻译；
当前没有工单停留在旧状态（`05-stage5.sql` 迁移时已归位并有自检断言）。

**维修人看不到「开始维修」按钮，或点了报「已开始维修」**
→ `/start-repair` 一张单只允许一次（`WHERE start_repair_time IS NULL` 条件更新）。
重复点击返回 `2004` 是防重复提交，不是故障。它也不改变工单状态——状态仍停留在「维修中」。

**验收时看不到备件用了什么**
→ 备件申请必须**全部审批完**才能提交验收（未审批时 `409+2004`）；
验收人在详情或 `/api/workorder/{id}/parts` 核对实际用料后再验收。

**想给工单打分但接口返回 403**
→ 评价是「一单一评」且只有报修人本人或管理员可评（`wo:workorder:evaluate` + 服务端二次校验），
维修人给自己评、或重复评价都会被拒。评分三维度各 1–5 分，越界返回 400。

---

## 11. 与第一阶段文档的对应关系

| 阶段一产物 | 落地位置 |
|---|---|
| A/B PRD 与权限矩阵 | `seed.sql` 的 53 个权限点 + 各 Controller `@PreAuthorize` |
| C 业务流程与状态机 | `WorkOrderStateMachine`（14 条转移表）、`InspectionTaskServiceImpl`（异常转单） |
| D 技术架构 | 本模块分包结构、`config/*` |
| E/F ER 与表设计 | `database/schema.sql`（34 表，含唯一索引与 CHECK） |
| G 后端模块划分 | `src/main/java/com/equipcare/*` 11 个包 |
| I REST API 规划 | 本 README 第 5 节 + Swagger 文档 |
| K 验收标准 | `EquipCareApplicationIT`、`Stage3IT`、`Stage4IT` 与各单元测试 |

第三阶段的页面与验收项对应：

| 第三阶段要求 | 落地位置 |
|---|---|
| 用户新增/编辑/禁用、重置密码、用户角色关联 | `system/controller/UserController` + `js/pages/users.js` |
| 登录、个人资料、修改密码 | `security/controller/AuthController` + `js/pages/profile.js` |
| 角色增删改、权限配置、删除保护 | `system/controller/RoleController` + `js/pages/roles.js` |
| 菜单权限、接口权限（后端校验） | `/api/auth/menus` 驱动导航；`@PreAuthorize` 硬校验；`Stage3IT.permissionEnforcedForInspector` |
| 用户操作记录 | `audit` 模块 + `js/pages/audit.js` |
| 设备分类增删改查 | `asset/controller/CategoryController` + `js/pages/categories.js` |
| 设备档案（新增/编辑/详情/状态/位置/负责人/分页筛选/报废停用） | `asset/controller/EquipmentController` + `equipment-list/form/detail.js` |
| 设备图片与附件上传 | `sys_file` + `eq_equipment_file` + `/files` 接口 + 详情页"图片与附件"页签 |
| 维修历史与巡检历史查看 | `EquipmentHistoryMapper`（只读联查）+ 详情页两个页签 |
| ZXing 二维码、唯一码、扫码鉴权与聚合展示 | `asset/service/QrCodeService` + `/qrcode.png` + `/scan` + `m/scan.html` |
| 设备档案打印 | `/app.html#/equipment/{id}/print`（A4 版式，含二维码与签字栏） |

第四阶段的页面与验收项对应：

| 第四阶段要求 | 落地位置 |
|---|---|
| 巡检模板：名称/分类/说明/检查项/标准/必填/异常判定规则/排序 | `insp_template` + `insp_template_item`（`abnormal_rule`、`require_photo`、`seq_no`）+ `InspectionTemplateController` + `js/pages/inspect-template.js` |
| 巡检计划：设备或分类、绑模板、指定人员、起止时间、每日/周/月周期、启停 | `insp_plan`（`cycle_config` JSON、`generate_ahead_hours`）+ `insp_plan_equipment` 快照 + `InspectionPlanController` + `js/pages/inspect-plan.js` |
| 任务自动生成（Spring 定时任务） | `InspectionTaskScheduler`（`@Scheduled` fixedDelay + 显式 zone）→ `InspectionGenerationServiceImpl.generateDue` |
| 重复生成防护 | 唯一索引 `uk_insp_task_plan_equip_date (plan_id, equipment_id, plan_date)` + `countPlanEquipmentDate` 预判 + `INSERT IGNORE` + `insertWithRetry` 区分编号冲突 |
| 时区问题 | `InspectionClock` 绑定 `equipcare.inspection.zone`（默认 `Asia/Shanghai`），调度与业务共用同一时区 |
| 定时任务异常隔离 | `tick()` 内两段各自 try-catch；单计划失败只计 `errors`，不影响同轮其它计划 |
| 计划暂停 | `POST /plans/{id}/status?status=DISABLED`，暂停即退出生成集合，已生成任务保留 |
| 任务过期 | `expireOverdue()` 按 `plan_date + sla_minutes` 标 `EXPIRED`，不删记录 |

第五阶段的页面与验收项对应：

| 第五阶段要求 | 落地位置 |
|---|---|
| 两种工单来源（手动报修 / 巡检异常转单） | `POST /api/workorder`（来源 `MANUAL`）+ `InspectionTaskServiceImpl` 异常转单（来源 `INSPECTION`，在途合并不丢证据） |
| 工单字段（编号/来源/等级/图片/人员/时限/状态等） | `wo_work_order` + `fault_image_ids` + `priority`（与故障等级分离：等级定 SLA，优先级定派单次序） |
| 七态状态机、禁止直改状态 | `WorkOrderStateMachine`（10 动作 / 14 转移，70 组合穷举单测）；无 `PUT /{id}/status`，Stage5IT 断言 PUT/PATCH → 405 |
| 每次流转记录操作人/时间/原因 | `wo_status_log`（append-only 触发器）+ `wo_dispatch_log`（派单历史） |
| 乐观锁与并发控制 | `casUpdateStatus`（`WHERE id=? AND status=? AND version=?`）；Stage5IT 并发双线程接单仅一人成功 |
| 派单：指定人/优先级/时限/说明/重新派单/派单历史 | `POST /{id}/dispatch`（`REDISPATCH` 类型）+ `GET /{id}` 的 dispatch 轨迹 + `/api/workorder/assignees`（按在办数升序） |
| 维修人员工作台 | `GET /api/workorder/workbench` + `js/pages/workorder-workbench.js` |
| 开始维修/维修记录/前后照片/工时/备件 | `POST /{id}/start-repair`（一单一次）+ `POST /{id}/records`（`START`/`DIAGNOSIS`/`PROCESS`/`FINISH`，带前后照片）+ 备件申请走库存模块 |
| 验收：看记录/照片/备件、通过/不通过、必须填原因 | `POST /{id}/verify-pass`（禁自验收 `3003`）/ `verify-reject`（≥10 字，回维修中）+ `GET /{id}/parts` 核对用料；备件未审批阻塞提交 |
| 服务评价（态度/质量/速度/文字） | `wo_evaluation`（唯一索引一单一评）+ `POST /{id}/evaluate` + `js/pages/workorder-evaluate.js` |
| 工单操作时间线（六类事件全关联真实用户） | `GET /{id}/timeline`：状态日志/派单/维修记录/验收/备件/评价六类源记录只读归并，每条带 `sourceType`+`sourceId` |
| 九个前端页面 | `js/pages/workorder-*.js`（shared/list/form/detail/dispatch/workbench/repair/verify/timeline/evaluate） |
| 验收测试 | `Stage5IT`（10 用例）+ `stage5-smoke.py`（71 断言）+ `ui-verify-stage5.py`（9 页 19 图） |
| 服务重启后的任务恢复 | 不依赖内存水位，每轮按 `[今天-backfill-days, now+generate_ahead_hours]` 扫库补齐，唯一索引保证不重复 |
| 巡检执行：待处理任务、详情、设备二维码、逐项检查、上传图片、异常说明、提交 | `InspectionController` + `InspectionTaskServiceImpl.submit` + `js/pages/inspect-execute.js`（`/tasks/{id}/equipment-qrcode.png`） |
| 正常/异常/跳过三态，必检不可跳过，异常必填原因并留证 | `InspItemResult` + `submit` 服务端复算 + 模板 `required` / `require_photo`；前端只做预检，判定以服务端为准 |
| 异常转维修工单且不复制数据 | `insp_anomaly` 记异常与 `work_order_id`；工单只落 `anomaly_id` + `source_id`，`WoInspectionSourceMapper` 只读联查设备/任务/检查项/照片 |
| 异常转单后证据不丢（在途合并） | `selectByWorkOrderId` → `WorkOrderDetailVO.mergedInspections`，同设备同故障合并进一张工单时逐条补齐来源 |
| 八个前端页面 | `js/pages/inspect-{template,plan,task,task-detail,execute,anomaly,calendar,stats}.js`，路由见 6.1 |
| 验收：日/周/月生成、重复防护、提交、转单、角色权限、真实入库 | `Stage4IT`（9 用例）+ `scripts/stage4-smoke.py`（35 项）+ `scripts/ui-verify-stage4.py`（8 页 15 图） |


## 12. 前端单元测试（第九阶段新增）

前端运行时仍为**零依赖原生 ES Module、无构建**；`frontend-tests/` 目录仅为 Vitest 3 + jsdom 26 的测试工具链（dev 依赖，不影响交付物）。

```bash
cd frontend-tests
npm install        # 首次安装测试依赖
npx vitest run     # 6 个测试文件，60 个用例
```

- 测试直接 `import` 生产静态资源 `../equipcare-admin/src/main/resources/static/js/`，不复制源码，测试的即交付代码。
- 覆盖：登录状态与权限控制（auth）、接口异常处理（Result 解包 / 业务错误 / 401 静默续期并发单飞 / 网络异常 / FormData 上传）、XSS 转义与空态表格、工单状态展示映射（与后端状态机对齐）、巡检结果提交校验（jsdom DOM 级）、备件入库表单验证。
- 接口自动化符合性测试：`python scripts/api-conformance.py`（39 项断言，需先启动服务；账号口令经 `EQUIPCARE_ADMIN_PASSWORD` / `EQUIPCARE_STAFF_PASSWORD` 环境变量传入）。

---

## 12. 创新升级（第八期）：事件驱动 / SSE 实时 / AI 副驾

详细设计与验证记录见 `../docs/12-创新升级说明.md`。要点：

- **领域事件 + 事务性 Outbox**：业务事务内落 `event_outbox`（与状态变更同事务），
  调度器原子认领补投（指数退避/判死），站内消息与 SSE 推送改为事件监听，架构方向由
  ArchUnit 测试守护（`ArchitectureRulesTest`）。
- **SSE 实时推送**：`POST /api/sse/ticket`（JWT 换 30 秒一次性票据）→
  `GET /api/sse/stream?ticket=`；前端 `js/realtime.js` 单例通道，断线指数退避重连、
  失败自动回退 30 秒轮询；铃铛/工单看板/SLA·库存·巡检异常提醒全部实时。
- **AI 智能副驾（`equipcare-ai` 模块）**：故障诊断（SSE 流式 Markdown）、
  智能派单 TOP3（算法预打分 + LLM 理由，失败降级纯算法）、维修报告生成（可复制/下载）。
  OpenAI 兼容协议，任选火山方舟 / 智谱 / DeepSeek：

  ```bash
  # .env 或环境变量（不配置则 AI 入口自动隐藏，系统功能不受影响）
  EQUIPCARE_AI_ENABLED=true
  EQUIPCARE_AI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
  EQUIPCARE_AI_API_KEY=你的Key
  EQUIPCARE_AI_MODEL=doubao-seed-1-6-251015
  ```

- **前端体验**：暗色模式（顶栏 🌙 切换，令牌级覆盖）、摄像头扫码（`/m/scan.html`
  原生 BarcodeDetector）、PWA（manifest + service worker 静态外壳缓存，API/SSE 旁路）。
- **Nginx 注意**：SSE 长连接需 `proxy_buffering off`（`deploy/nginx.conf` 已加
  `/api/sse/` 独立 location，读超时 35 分钟）。
