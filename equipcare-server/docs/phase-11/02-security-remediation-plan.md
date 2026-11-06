# 巡维云 EquipCare · Phase 11 安全整改计划

> 生成时间：2026-10-31
> 性质：整改执行计划。所有问题均已**重新核对当前源码**（非照搬 01 审计报告结论），代码位置为整改前实际行号。
> 执行策略：按批次修复，每批附测试方案；不重写项目、不换技术栈、不改接口契约（仅安全缺陷除外）。

---

## 一、问题清单（当前源码核实结果）

| # | 等级 | 问题 | 真实代码位置 | 状态 |
|---|------|------|--------------|------|
| P1-1 | 高 | 扫码页存储型/反射型 XSS：服务端数据与错误消息未转义直接插入 innerHTML | `static/m/scan.html` L46-56、L68-70、L83-93、L95-130 | 待修（批一） |
| P1-2 | 高 | Redis 故障导致全站已登录请求 500：过滤器黑名单检查异常未捕获 | `security/JwtAuthenticationFilter.java` L68、L74；`AuthServiceImpl.java` L98/L111/L122/L171；`TokenStore.java` L40-43 | 待修（批二） |
| P1-3 | 高 | 登出未吊销 Refresh Token：登出后 7 天内旧 refreshToken 仍可换取新令牌 | `AuthServiceImpl.logout` L143-156（只拉黑 access jti）；`AuthController.logout` 仅取 header；前端 `js/app.js` L561 登出不传 refreshToken | 待修（批三） |
| P1-4 | 中高 | 生产环境 JWT 密钥/DB 密码存在默认值兜底，无 prod profile 强制校验 | `application.yml` L32（DB 密码默认 123456）、L117（JWT dev 默认密钥）；无 application-prod.yml | 待修（批四） |
| P1-5 | 中高 | Swagger / OpenAPI 生产环境匿名开放 | `SecurityConfig.WHITE_LIST` L36-38；`application.yml` L98-109 springdoc 全局启用 | 待修（批五） |
| P1-6 | 中 | Docker 容器默认跑 dev profile（全量 SQL 打印）；backend 无健康检查 | `docker-compose.yml` backend.environment（缺 SPRING_PROFILES_ACTIVE）；backend/frontend 服务定义 | 待修（批六） |
| P1-7 | 高 | 备件退料并发双退：check-then-act 状态检查 + 非原子库存回增 | `InventoryServiceImpl.returnPart` L295-321 | 待修（批七） |
| P1-8 | 高 | 工单备件成本并发丢失：读取-计算-覆盖写 | `InventoryServiceImpl.issuePart` L268-274、`returnPart` L312-319 | 待修（批八） |

### 核实中确认不属实的项（无需修改）

- **SQL 注入**：全库 Mapper 均使用 `#{}` 预编译（含注解 text block），未发现 `${}` 拼接用户输入。
- **密码存储**：BCryptPasswordEncoder（SecurityConfig L55），无明文。
- **库存扣减**：`SpPartMapper.deductStock` 已是原子条件 UPDATE（`current_stock >= #{qty}`），不会负库存。
- **方法级权限**：`@EnableMethodSecurity` + `@PreAuthorize` 全覆盖（01 报告结论属实）。

---

## 二、修复方案

### 批一：扫码页 XSS（P1-1）

**原因**：`scan.html` 用 `innerHTML` 模板字符串渲染接口返回的 `assetNo`、`equipmentName`、`locationPath`、维修工单 `title`、异常消息 `error.message` 等，任何含 HTML 的值都会被执行；URL 参数 `token` 未校验格式。

**方案**：
1. 全部渲染改为 DOM API（`createElement` + `textContent`），新增 `el(tag, props, children)` 辅助函数；
2. 唯一需要 HTML 的位置（巡检异常高亮）改为创建 `<b>` 元素 + textContent；
3. URL `token` 参数严格校验 `^[A-Za-z0-9_-]{6,64}$`，不合法直接显示「二维码无效」，不发起请求；
4. `fail()` 错误卡片同样改为 DOM 构建，`error.message` 以 textContent 渲染。

**影响范围**：仅 `m/scan.html`；业务功能（扫码查看设备档案、跳转登录回跳、完整档案入口）不变。

**测试**：Playwright 安全测试脚本——用后端接口造一条 `equipmentName` 含 `<img src=x onerror=...>` / `<script>` 的设备（经 API 创建），扫码页断言：文本按字面显示、无脚本执行、无新增告警 dialog；另断言恶意 token 参数（`"><script>`）不发起 API 请求。

### 批二：Redis 故障降级（P1-2）

**原因**：`TokenStore` 所有方法直接调用 RedisTemplate，Redis 宕机时抛 `RedisConnectionFailureException`；过滤器/auth 服务未捕获 → 500。会话回源数据库的逻辑已存在（Filter L74-82）但被前一步黑名单检查的异常挡住。

**方案**（区分必需/非必需）：
1. `TokenStore` 内部统一捕获 `DataAccessException`：
   - `isBlacklisted` 失败 → 返回 false + WARN（降级：黑名单暂不可查，认证仍走「签名验证 + DB 会话回源」，不跳过认证）；
   - `getLoginUser` 失败 → 返回 null（触发既有 DB 回源）；
   - `saveLoginUser` / `blacklist` / 登录失败计数 → 捕获 + WARN 降级为 no-op；
   - 新增 `requireAvailable()`：PING 探活，供**安全关键操作** fail-closed。
2. `AuthServiceImpl.refresh`：刷新前 `requireAvailable()`，Redis 不可用抛新错误码 `SERVICE_UNAVAILABLE(6001, 503)`——刷新令牌吊销依赖 Redis，**安全失败**而非降级为无验证模式；
3. `GlobalExceptionHandler` 增加 Redis 异常兜底 → 503「服务暂不可用」，不泄漏堆栈；
4. 所有降级路径输出 WARN 日志（含操作名），不输出密码/Token 明文。

**测试**：Mockito 单测——mock RedisTemplate 抛连接异常，断言：过滤器路径（isBlacklisted=false + getLoginUser=null → DB 回源可继续）、login 计数降级不抛异常、refresh 抛 6001；mock 恢复后行为正常。集成层面：Redis 在线时全量 Stage IT 回归。

### 批三：Refresh Token 生命周期（P1-3）

**原因**：`logout` 只将 access token jti 拉黑，refresh token（有效期 7 天）登出后仍可无限续签。

**方案**（沿用 Redis 存储机制）：
1. `RedisKeys` 新增 `userRefreshTokens(userId)`：`equipcare:auth:refresh-tokens:{userId}`，Set 结构，TTL = refresh 有效期；
2. 登录/刷新成功时登记新 refresh jti；刷新时旧 jti 已拉黑 + 从集合移除（一次性使用，防重放）；
3. 刷新校验增强：jti 必须仍在该用户的 refresh 集合中（集合存在但不含 → 拒绝），登出/改密后旧令牌即使未过期也不能用；
4. `logout`：拉黑 access jti + 撤销该用户全部 refresh token + 清除会话；
5. `changePassword` / 管理员禁用用户：同步撤销全部 refresh token（复用 removeAllByUser 扩展）；
6. 前端 `app.js` 登出时 POST body 携带 refreshToken；后端 logout 接受**可选** body（兼容旧调用）。

**影响范围**：TokenStore、AuthServiceImpl、AuthController、RedisKeys、`js/app.js`。登录响应结构不变。

**测试**（Stage11 集成测试）：正常登录→正常刷新→正常登出→旧 refreshToken 刷新被拒；重复刷新（重放旧 jti）被拒；过期 refreshToken 被拒；管理员禁用用户后其 refreshToken 刷新被拒；不同用户 refresh token 隔离；改密后旧 refresh 被拒。

### 批四：敏感配置治理（P1-4）

**原因**：`application.yml` 中 DB 密码默认 `123456`、JWT 密钥有内置默认值；无 prod profile；无启动期安全校验，误用开发默认值不会报错。

**方案**：
1. 新建 `application-prod.yml`：`EQUIPCARE_JWT_SECRET`、`EQUIPCARE_DB_PASSWORD` **无默认值**（未注入则启动失败）；关闭 SQL 日志；
2. 新增 `ProdSecurityGuard`（`@Profile("prod")` 启动校验）：JWT secret 等于已知开发默认值 → 拒绝启动并给出明确错误；DB 密码为空 → 拒绝启动；
3. 主配置 dev 默认值保留（不破坏本地开发流程），注释明确标注「仅限本地开发」；
4. `deploy/env.example` 复查：仅占位符，无真实密钥（已达标，补充 profile 说明）；
5. 日志复查：登录/认证日志不输出密码与 Token（现有实现已达标，整改报告记录验证结果）。

**测试**：prod guard 单测（默认密钥 → 启动失败；随机密钥 → 通过）；本地 dev 启动回归确认不受影响。

### 批五：生产环境 Swagger 限制（P1-5）

**方案**：
1. `SecurityConfig` 白名单按配置项 `equipcare.apidocs.enabled` 条件化（默认 true 保持开发体验；false 时 swagger/api-docs 路径不再匿名放行）；
2. `application-prod.yml`：`springdoc.api-docs.enabled=false`、`springdoc.swagger-ui.enabled=false`、`equipcare.apidocs.enabled=false`（三重限制：端点 404 + 网关不放行）；
3. 业务 API 不受影响。

**测试**：prod 配置单测/profile 断言 + dev 环境回归确认 swagger 仍可用。

### 批六：Docker 生产配置（P1-6）

**原因**：compose 未注入 `SPRING_PROFILES_ACTIVE`，Spring 落到主配置默认 `dev`（SQL 全量 stdout）；backend 服务无健康检查，frontend `depends_on` 无条件。

**方案**：
1. compose backend.environment 增加 `SPRING_PROFILES_ACTIVE: prod`；
2. backend 增加 healthcheck（`curl /actuator/health`），`Dockerfile.backend` 运行阶段安装 curl；
3. frontend `depends_on.backend` 改 `condition: service_healthy`；
4. 保持其余设计不变（环境变量强制注入 `:?` 已达标、数据卷持久化已达标）。

**测试**：`docker compose config` 语法校验；不实际起容器（本机无 Docker daemon 时如实说明）。

### 批七：备件退料并发双退（P1-7）

**原因**：`returnPart` 先 `requireApply(ISSUED)` 读状态、后 `updateById(RETURNED)`，两步之间无并发保护；两个并发请求都读到 ISSUED → 双倍库存回增 + 双倍成本冲减 + 两条 RETURN 流水。

**方案**：
1. `SpWorkOrderPartMapper` 新增原子状态迁移：`UPDATE sp_work_order_part SET issue_status=#{to} WHERE id=#{id} AND issue_status=#{from}`，影响行数 0 → 抛「该记录已被处理」；
2. `returnPart` / `issuePart` / `rejectApply` 统一改为原子迁移先行，库存与流水在迁移成功后写；
3. 库存回增复用原子 `increaseStock`；流水保留（append-only）。

### 批八：工单备件成本并发一致性（P1-8）

**原因**：`issuePart`/`returnPart` 成本更新为 select→计算→updateById 覆盖写，并发下互相覆盖丢账。

**方案**：
1. `WoWorkOrderMapper` 新增原子成本调整：`UPDATE wo_work_order SET spare_cost = GREATEST(COALESCE(spare_cost,0) + #{delta}, 0) WHERE id=#{id}`（数据库 DECIMAL 精度，Java 侧 BigDecimal 传参，无浮点）；
2. `issuePart` 传 `+amount`，`returnPart` 传 `-amount`；成本始终可由 `sp_work_order_part` 流水追溯（`sumIssuedAmount` 校验口径保留）；
3. 不清空、不重算现有成本数据。

**批七/八测试**（Stage11 集成测试，真实 MySQL）：
- 并发退料：ExecutorService 双线程对同一 ISSUED 记录同时 returnPart → **恰好一成功一失败**；库存只回增一次；成本只冲减一次；RETURN 流水恰一条；
- 正常退料 / 超量（无此路径，全量退）/ 重复退料（第二次被拒）；
- 并发出库记账：同工单并发 issue 两笔 → spare_cost = 两笔之和，无丢失；
- 退料后成本变化正确；事务回滚后库存/成本/流水一致。

---

## 三、批次执行顺序与依赖

批一（前端独立）→ 批二（TokenStore 基础）→ 批三（依赖批二的 requireAvailable）→ 批四 → 批五 → 批六（独立）→ 批七 → 批八（依赖批七的原子迁移）→ 批九全量回归 + 文档。

每批完成后编译验证；批三/七/八完成后统一跑全量测试（`mvn verify`），避免重复长构建。

## 四、数据库变更

**无表结构变更、无数据迁移。** 批七/八仅新增 Mapper 方法（原子 UPDATE 语句），全部基于现有列（`issue_status`、`spare_cost`）。回滚方式：还原代码即可，无 DDL。

## 五、预计修改文件

| 批次 | 文件 |
|------|------|
| 一 | `static/m/scan.html`；新增 `scripts/verify-phase11-xss.py` |
| 二 | `security/TokenStore.java`、`security/service/impl/AuthServiceImpl.java`、`common/result/ResultCode.java`、`common/exception/GlobalExceptionHandler.java`；新增 `security/TokenStoreDegradationTest.java` |
| 三 | `common/constant/RedisKeys.java`、`security/TokenStore.java`、`security/service/impl/AuthServiceImpl.java`、`security/controller/AuthController.java`、`js/app.js`；新增 `Stage11IT.java` |
| 四 | 新增 `application-prod.yml`、`config/ProdSecurityGuard.java`；`application.yml`（注释）；`deploy/env.example`（注释） |
| 五 | `security/SecurityConfig.java`、`application-prod.yml` |
| 六 | `docker-compose.yml`、`Dockerfile.backend`、`deploy/env.example` |
| 七/八 | `inventory/mapper/SpWorkOrderPartMapper.java`、`workorder/mapper/WoWorkOrderMapper.java`、`inventory/service/impl/InventoryServiceImpl.java` |
| 九 | 新增 `03-security-remediation-report.md`、`04-test-report.md`、修改文件清单 |
