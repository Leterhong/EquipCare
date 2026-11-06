# 巡维云 EquipCare · Phase 11 第一阶段：全项目架构审计报告

> 审计日期：2026-10-31
> 审计方式：三路并行只读审计（后端架构与安全 / 前端架构 / 数据库与部署），未修改任何业务代码
> 审计范围：Spring Boot 后端 318 个 Java 文件、前端 41 个页面模块 + 3 个核心共享模块、数据库脚本（database/ 全量 DDL + 7 个增量脚本 + seed）、Docker 部署编排
> 状态：**审计完成，等待确认后进入修复阶段**

---

## 一、当前项目架构

### 1.1 总体形态

- **架构风格**：模块化单体（modular monolith），按业务域分包：`asset`（设备资产）/ `inspection`（巡检）/ `workorder`（维修工单）/ `inventory`（备件）/ `analytics`（驾驶舱）/ `system`（用户权限通知审计）/ `security`（认证）/ `export`（导出）/ `common`（通用件）
- **分层纪律**：Controller 层干净（仅参数绑定 + 委托 Service）；Entity / DTO / VO 三层分包分离清晰，未发现 Entity 直接出参
- **前端**：原生 ES Module 零依赖（hash 路由 + NAV_TREE 站点地图 + 设计令牌驱动），41 页面模块；ECharts 本地内置
- **部署**：docker-compose 四服务编排（MySQL + Redis + backend + Nginx/frontend），env 注入密钥，命名卷持久化

### 1.2 技术栈及依赖（pom.xml 核实）

| 组件 | 版本 | 评估 |
|---|---|---|
| Spring Boot | 3.5.6 | 较新，无已知高危 CVE |
| MyBatis-Plus | 3.5.9 | 正常 |
| jjwt | 0.12.6 | 正常 |
| Apache POI | 5.3.0 | 正常 |
| springdoc-openapi | 2.8.9 | 正常（但生产暴露问题见下） |
| ZXing | 3.5.3 | 正常 |
| MySQL / Redis | compose 编排 8.x / 7.x | 正常 |

### 1.3 核心业务模块与质量基线（正面确认）

审计确认以下机制**已正确实现**，属于不建议改动的稳定资产：

1. **工单状态机**：14 条状态转移穷举 + CAS 更新（`WHERE status=? AND version=?`），非法跳转后端拒绝（错误码 3001）；「开始维修」用 `isNull(start_repair_time)` 条件更新保证幂等
2. **库存扣减**：原子 `UPDATE ... SET current_stock = current_stock - ? WHERE current_stock >= ?`，并发下**不会产生负库存**；`sp_part.version` 乐观锁 + `CHECK(current_stock>=0)` 双保险
3. **SQL 注入面**：全库 Mapper 零 `${}` 拼接，动态 SQL 全部 `#{}`；排序字段走 `PageQuery.safeOrderBy()` 白名单
4. **认证基线**：BCrypt 密码哈希；登录 5 次锁 10 分钟；改密/禁用强制下线；refresh token 一次性使用；`@PreAuthorize` 方法级权限覆盖率 100%
5. **数据库设计**：11 个业务唯一键齐全（assetNo/username/partNo/orderNo/planCode 等）；高频列索引覆盖良好；金额一律 DECIMAL(12,2)；全部脚本幂等可重复执行；6 个 append-only 触发器保障审计记录不可篡改
6. **前端转义**：`esc()` 转义覆盖率高，`renderTable` 默认路径安全；ECharts dispose / renderToken 竞态防护到位

---

## 二、代码质量问题清单（按分级）

> P0=严重（越权/数据丢失/核心不可用）P1=重要（交付前必须修）P2=一般 P3=优化建议
> 已确认 P0：无（未发现可立即利用的越权数据丢失级漏洞；scan.html XSS 因需要后台可编辑字段写入权限，定级 P1）

### P1 —— 交付前必须修复（10 项）

| 编号 | 领域 | 问题 | 位置 |
|---|---|---|---|
| P1-01 | 安全 | **m/scan.html 存储型 XSS**：扫码落地页整页未做 HTML 转义，`row()`、巡检/维修记录、模板主体把 equipmentName/assetNo/位置/备注等直接插 innerHTML，而这些都是后台可编辑字段 | `static/m/scan.html:68-130` |
| P1-02 | 稳定性 | **Redis 宕机 → 全站不可用**：Token 校验路径（isBlacklisted/getLoginUser）硬依赖 Redis，连接失败异常穿透过滤器，所有已登录请求 500（不止登录；本次审计期间实际发生并复现） | `JwtAuthenticationFilter.java:60-99`、`TokenStore.java` |
| P1-03 | 安全 | **登出未吊销 refreshToken**：logout 只拉黑 accessToken，旧 refreshToken 7 天内可经 `/api/auth/refresh` 换取全新令牌对，登出形同虚设 | `AuthServiceImpl.java:143-156`、`JwtProperties.java:23` |
| P1-04 | 安全 | **JWT 密钥 / DB 密码默认值提交入库**：`EquipCare-Dev-Secret-Key-Change-Me-In-Production-2026` 与 `123456`，生产忘配环境变量时静默生效，等同可伪造任意用户 token | `application.yml:117,32` |
| P1-05 | 安全 | **Swagger/OpenAPI 生产匿名暴露**：`/v3/api-docs/**`、`/swagger-ui/**` 放行且无 profile 开关，API 结构全量公开 | `SecurityConfig.java:36-38`、`application.yml:98-108` |
| P1-06 | 部署 | **生产容器跑 dev profile**：`spring.profiles.active: dev` 为默认且无 prod profile，compose 未传 `SPRING_PROFILES_ACTIVE` → 生产全量 SQL stdout + DEBUG 日志 | `application.yml:26,227-242`、`docker-compose.yml:73` |
| P1-07 | 前端 | **statusTag 字典字段错配**：`STOCK_TXN_TYPE`/`SLA_STATUS`/`HEALTH_LEVEL` 样式字段叫 `cls` 而 `statusTag()` 只读 `tag` → 渲染 `<span class="tag undefined">`，库存流水/SLA/健康等级三组页面彩色标签全部失效 | `ui.js:591-629` |
| P1-08 | 前端 | **access + refresh 双令牌存 localStorage**：任何 XSS 可窃取并长期重放（与 P1-01 组合成现实威胁链）；零依赖架构下至少评估 refresh token 改 HttpOnly Cookie | `api.js:11-15` |
| P1-09 | 并发 | **退料接口并发双退**：`returnPart` 检查 ISSUED 后无条件回增库存，两个并发请求都读到 ISSUED → 库存双倍回增、成本双倍冲减（应改条件更新 `WHERE issue_status='ISSUED'`） | `InventoryServiceImpl.java:295-309` |
| P1-10 | 并发 | **工单备件成本 read-modify-write**：`spare_cost` 走 selectById 后 updateById 加减，并发出库丢失累加（应改 `SET spare_cost = spare_cost + ?` 原子 SQL） | `InventoryServiceImpl.java:268-274` |

### P2 —— 尽快处理（14 项）

| 编号 | 问题 | 位置 |
|---|---|---|
| P2-01 | CORS `allowedOriginPatterns("*") + allowCredentials(true)` 教科书式错误组合 | `WebConfig.java:29-35` |
| P2-02 | 文件下载 IDOR：任意登录用户可下载任意 fileId（他人报修照片/维修凭证），无归属校验 | `FileController.java:57-78` |
| P2-03 | 工单 detail/list 无数据归属隔离：普通维修人可浏览全员工单详情（仅功能权限校验） | `WorkOrderServiceImpl.java:174,162-171` |
| P2-04 | Excel 导出公式注入（CSV Injection）：`=+-@` 开头的用户字段直接写单元格 | `ExportServiceImpl.java:200-215` |
| P2-05 | 前端路由 404/403 分支不执行 pageCleanups → dashboard ECharts 实例与 resize 监听泄漏累积 | `app.js:432-451` |
| P2-06 | objectURL 系统性泄漏：`api.objectUrl()` 无 revoke 约定，photoPicker/attachmentThumbs 每次 paint 全量重建 | `api.js:191-195`、`workorder-shared.js:74-96`、`inspect-execute.js:340-360` |
| P2-07 | 前端模块级缓存永不失效（templateCache/userCache 等），新建模板/用户后下拉看不到，需整页刷新；失败一次后整个会话空下拉 | `inspect-plan.js:16-45`、`inspect-template.js:24-36` |
| P2-08 | backend 服务无 healthcheck，编排无法感知后端假死；Dockerfile 无 USER 指令以 root 运行 | `docker-compose.yml:61-102` |
| P2-09 | 无 `.dockerignore`：构建上下文打包 target/logs/data/frontend-tests | 项目根目录 |
| P2-10 | `EQUIPCARE_SCAN_BASE_URL` 默认空 → 扫码落地页打不开（演示高频踩坑点） | `docker-compose.yml:89`、`env.example:25` |
| P2-11 | 备份只备数据库不备附件卷，恢复后设备照片/维修凭证全丢 | `deploy/backup.sh` |
| P2-12 | `selectOverdue` 无 ORDER BY：逾期扫描返回批次不确定，可能重复/遗漏 | `InspTaskMapper.java:136-141` |
| P2-13 | 工单明细 N+1：每条轨迹单独查用户表（50 条轨迹 = 50+ 次查询）；`evaluationMapper.selectByOrder` 同单查两次 | `WorkOrderServiceImpl.java:1067-1144,197,218` |
| P2-14 | 前端可访问性：modal 无焦点圈禁；可点击表格行无键盘可达；`workorder-timeline.js:49` 缺 return 导致空数据 TypeError；`spare-shared.js:83` 的 `chip-active` 类不存在（备件工具条高亮从未生效） | 多处 |

### P3 —— 优化建议（择要 12 项）

- 架构：`WorkOrderServiceImpl` 1400 行上帝类，14 个依赖注入；跨域直依赖其它模块 Mapper
- 并发：单号 `MAX+1` 无锁并发重号（有唯一键兜底但体验差）；流水 before/after 本地推算可能错位；定时任务默认单线程池 + 无分布式锁（单实例安全）
- 安全：`refresh` 接口无频率限制；IP 取自可伪造的 X-Forwarded-For；登录密码策略（6-64）与改密策略（8-32）不一致；`NoResourceFoundException` 回显资源路径
- 数据库：软删未纳入唯一键（同编码重建被挡）；`sp_work_order_part.txn_id`、`eq_equipment.responsible_user_id` 缺索引；19 处 `SELECT *`（含 risk_detail MEDIUMTEXT）；`00-create-database.sql` 的 `CREATE USER IF NOT EXISTS` 不重置密码
- 前端：CSS 阶段追加重复覆盖（`.input:focus` 旧蓝主题残留、`.scan-row`/`.chip.active` 双定义、空规则块）；树扁平化 5 处重复实现；`ROLE_LABEL` 字典重复两份；110 处非 var() 颜色（多为设计内）
- 部署：MySQL healthcheck 命令行暴露 root 密码；Redis 未设 maxmemory；`/upload/**` 未走 Nginx 反代（当前 UI 走 /api/files 不受影响）；schema 升级无自动迁移入口

---

## 三、潜在风险（业务闭环视角，待第二阶段验证）

以下风险点在架构审计中已初步识别，将在**第二阶段核心业务闭环审计**中通过真实操作逐项验证：

1. 已有维修记录的设备是否可被错误删除（软删级联影响）
2. 设备停用后是否仍能创建巡检任务
3. 巡检定时任务重复生成的防护（已有 `INSERT IGNORE`/唯一键，需实测）
4. 已完成任务重复提交（已有状态机拦截，需实测越权路径）
5. 逾期任务识别正确性（与 P2-12 关联）
6. 权限变更后 Redis 会话 TTL 内不生效（P3，实测影响窗口）

---

## 四、不建议修改的稳定模块

| 模块 | 理由 |
|---|---|
| 工单状态机与 CAS 更新（`WorkOrderStateMachine`、`WoWorkOrderMapper`） | 设计正确且有完整测试覆盖，重写风险大于收益 |
| 库存原子扣减 SQL（`SpPartMapper.java:18-35`） | 并发安全的核心保障，只需在**退料路径**补条件更新（P1-09），不动扣减路径 |
| append-only 审计触发器（schema.sql L820-863） | 审计完整性保障，禁止触碰 |
| 数据库 schema 全量结构 | 不做破坏性变更；仅新增缺失索引（P3 项）走增量脚本 |
| 前端 esc()/renderTable/guard() 体系 | 转义纪律良好，仅补 scan.html 缺口，不重构 |
| 全部既有接口契约 | Phase 11 全程不变更 URL/参数/响应结构 |

---

## 五、建议优先处理顺序（等确认后执行）

**第一批（安全 + 稳定性，P1-01~06 + P2-01~04）**：scan.html 转义 → Redis 降级策略 → refreshToken 吊销 → 敏感默认值清除 + fail-fast → Swagger 生产关闭 → prod profile + compose 传递 → CORS 收敛 → 文件下载归属校验 → Excel 公式注入防护

**第二批（并发正确性，P1-09/10）**：退料条件更新 + 成本原子累加 + 补充并发测试

**第三批（前端正确性，P1-07/08 + P2-05~07、14）**：statusTag 字段修复 → localStorage 评估 → pageCleanups/objectURL/缓存失效

**第四批（部署就绪，P2-08~11）**：healthcheck + .dockerignore + SCAN_BASE_URL + 备份补附件卷

**第五批（性能与打磨，P2-12/13 + P3 择要）**：N+1 批量化、ORDER BY、缺失索引增量脚本

---

## 六、审计期间的意外发现（已实际发生）

1. **本机 Redis 未启动导致登录 500**：审计期间实测复现（Redis 是登录/鉴权硬依赖），佐证 P1-02；已临时启动便携 Redis（`workbuddy-tools/redis`）恢复验证环境。该现象本身证明了 fail-fast/降级策略的必要性。
2. **两处 UI 缺陷已修复并验证**（用户报告）：顶栏用户名白字白底不可见（根因：阶段八遗留 `body { color-scheme: dark }`，UA 据此把 button 默认文字渲染为白色）；侧栏折叠后只剩一列小点（根因：折叠态只留 nav-dot）。修复：`color-scheme: light` + `.user-chip` 显式色 + 折叠态首字徽标（`nav-abbr`），Playwright 截图验证通过（`docs/screenshots/phase11/fix-*.png`）。

---

## 七、结论

**架构总体评价：B+（良好，具备修复到交付标准的条件）**

- 业务核心（状态机、库存一致性、SQL 注入面、权限注解覆盖）质量明显高于同类项目平均水平
- 风险集中在：安全收尾（XSS/登出/密钥/暴露面）、Redis 单点、两处并发正确性、部署"最后一公里"
- 无 P0 级问题；P1 共 10 项，预计 2-3 个工作批次可全部修复并回归

**建议：按第五节顺序执行修复，每批完成后跑全量回归（Vitest 60 + mvn verify 171 + Playwright 7 脚本）。请领导确认修复计划后开始第一批。**
