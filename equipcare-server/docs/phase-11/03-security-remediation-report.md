# 巡维云 EquipCare · Phase 11 安全整改报告

> 生成时间：2026-10-31
> 对应计划：`docs/phase-11/02-security-remediation-plan.md`
> 结论：**8 项 P1 问题全部修复并通过真实测试验证；原有功能全量回归未受影响。**

---

## 一、修复总览

| # | 问题 | 修复方式 | 验证结果 |
|---|------|----------|----------|
| P1-1 | 扫码页 XSS | scan.html 全量重写为 DOM API + textContent 渲染，URL token 白名单校验 | Playwright 安全测试 4/4 通过 |
| P1-2 | Redis 故障全站 500 | TokenStore 内部降级（非关键）+ requireAvailable 探活 fail-closed（关键）+ 503 兜底 | 降级单测 10/10 通过 |
| P1-3 | 登出未吊销 Refresh Token | 按用户登记刷新令牌集合，登出/改密/禁用全量吊销；刷新一次性使用 | Stage11IT 令牌生命周期 5/5 通过 |
| P1-4 | 生产默认密钥/密码 | application-prod.yml 无默认值 + ProdSecurityGuard 启动强校验 | 默认密钥拒绝启动实测通过 |
| P1-5 | Swagger 生产暴露 | prod 关闭 springdoc + 网关条件放行（equipcare.apidocs.enabled） | prod 下 swagger 401 实测通过 |
| P1-6 | Docker 跑 dev profile | SPRING_PROFILES_ACTIVE=prod + backend healthcheck + 依赖条件 | compose config 校验通过 |
| P1-7 | 退料并发双退 | 原子状态迁移（UPDATE ... WHERE issue_status=ISSUED）| 并发双退测试：恰一成一败 ✓ |
| P1-8 | 工单成本并发丢失 | 原子成本调整 SQL（GREATEST 兜底不为负）+ 流水号 FOR UPDATE 当前读 | 并发出库成本累计准确 ✓ |

**修复数量：8/8。** 未修复项：无。遗留风险见第五节。

---

## 二、逐项详情

### P1-1 扫码页 XSS（高危）

- **原始问题**：`m/scan.html` 用 `innerHTML` 模板拼接服务端数据（设备名称/资产编号/位置/维修工单标题/错误消息），含 HTML 的字段可注入脚本；URL `token` 参数未校验。
- **修复**：所有渲染改 `createElement` + `textContent`（新增 `el()` 辅助）；异常消息按文本渲染；token 校验 `^[A-Za-z0-9_-]{6,64}$`，非法 token 不发起任何请求。
- **测试**（`scripts/verify-phase11-xss.py`，Playwright 真实浏览器）：
  - 设备名 `<img src=x onerror=window.__xss=1>设备<svg onload=...>` → 字面渲染、脚本未执行 ✓
  - 设备名含 `<script>` → 未执行 ✓
  - URL token `"><script>alert(1)</script>` → 显示「二维码无效」且未发起扫码请求 ✓
  - 正常设备扫码功能（完整档案入口）不受影响 ✓
- **数据库变更**：无。

### P1-2 Redis 故障降级（高危）

- **原始问题**：`JwtAuthenticationFilter` 的黑名单查询、`AuthServiceImpl` 的失败计数/刷新校验在 Redis 宕机时直接抛 `RedisConnectionFailureException` → 全站已登录请求 500。
- **修复**（区分必需/非必需）：
  - 非关键路径降级：`isBlacklisted` 失败返回 false、`getLoginUser` 失败返回 null（触发既有数据库会话回源）、会话写入/黑名单写入/登录失败计数静默降级 + 节流 WARN（每分钟 1 条，不含敏感值）。**认证不跳过**：JWT 签名验证 + 数据库实时加载用户与权限，禁用用户照常拦截；
  - 关键路径 fail-closed：新增 `TokenStore.requireAvailable()`（PING 探活）；刷新令牌与登出在 Redis 不可用时抛新错误码 `SERVICE_UNAVAILABLE(6001, 503)`，绝不降级为无验证模式；
  - `GlobalExceptionHandler` 增加 Redis 异常兜底 → 503，不泄漏堆栈/连接串。
- **测试**：`TokenStoreDegradationTest`（Mockito，10 用例）：黑名单/会话/计数降级不抛异常、探活 false、Redis 恢复后语义恢复、刷新令牌登记校验安全失败。
- **数据库变更**：无。

### P1-3 Refresh Token 生命周期（高危）

- **原始问题**：`logout` 只拉黑 access token jti；refresh token（7 天有效）登出后仍可无限续签。
- **修复**：
  - `RedisKeys.userRefreshTokens(userId)`：按用户登记有效刷新令牌 jti（TTL = 刷新令牌有效期）；
  - 登录/刷新登记新 jti；刷新为一次性使用（旧 jti 拉黑 + 移出集合，防重放）；
  - 刷新校验增强：jti 必须仍在登记集合中（集合缺失 → 拒绝，要求重新登录）；
  - `logout`：拉黑 access + **全量吊销该用户 refresh token**（客户端回传 refreshToken 或按 access 主体均可）；Redis 不可用时登出安全失败（503），不静默丢失吊销；
  - 禁用用户 / 改密：`removeAllByUser` 扩展为同时吊销全部刷新令牌；
  - 前端 `app.js` 登出请求携带 refreshToken。
- **测试**（Stage11IT）：正常登录→刷新→重放旧令牌被拒(1006)；登出后 access(1004) 与 refresh(1006) 均失效；不带 refreshToken 的登出同样全量吊销；用户隔离（吊销 A 不影响 B）；禁用用户刷新被拒(1002)且吊销后恢复状态也不能再用；过期 refreshToken 被拒。
- **数据库变更**：无。

### P1-4 敏感配置治理（中高）

- **修复**：新增 `application-prod.yml`（JWT 密钥、DB 密码无默认值）与 `config/ProdSecurityGuard`（`@Profile("prod")` 启动校验：开发默认 JWT 密钥 / 弱 DB 口令 / 数据库不可达 → 拒绝启动并给出明确原因）。开发环境默认值保留，本地 dev 启动流程不受影响（已实测）。
- **实测**：
  - prod 无 `EQUIPCARE_JWT_SECRET` → 启动失败「secret 未配置或长度不足 32 字节」✓
  - prod 注入开发默认密钥 → 启动失败「生产环境检测到未更换的 JWT 密钥」✓
  - prod 注入随机强密钥 → 启动成功，日志输出「生产环境安全基线校验通过」✓
- **数据库变更**：无。

### P1-5 生产 Swagger 限制（中高）

- **修复**：`SecurityConfig` 白名单按 `equipcare.apidocs.enabled` 条件放行；`application-prod.yml` 同时关闭 `springdoc.api-docs` / `swagger-ui`。三重限制：端点 404 + 网关 401 + 文档生成关闭。
- **实测**（prod profile 启动）：`/swagger-ui.html` → 401、`/v3/api-docs` → 401、业务登录 API → 200 ✓。dev 环境 Swagger 不受影响（回归通过）。
- **数据库变更**：无。

### P1-6 Docker 生产配置（中）

- **修复**：compose 注入 `SPRING_PROFILES_ACTIVE=prod`；backend 增加 healthcheck（curl /actuator/health，Dockerfile 运行阶段安装 curl）；frontend `depends_on` 改 `service_healthy`。`:?` 强制环境变量、MySQL/Redis 健康检查、数据卷持久化沿用既有设计。
- **验证**：`docker compose config` 语法校验通过；缺 `.env` 必填变量时报错（强制注入生效）。本机无 Docker daemon，未做容器级启动演练，如实记录。

### P1-7 备件退料并发双退（高危）

- **原始问题**：`returnPart` 先读状态后更新（check-then-act），并发双退会双倍回增库存 + 双倍冲减成本。
- **修复**：新增 `SpWorkOrderPartMapper.transitionStatus(id, from, to)` 原子迁移；`issuePart` / `returnPart` / `rejectApply` 统一改为迁移先行（影响行数 0 → 2004「不能重复退料」）；库存回增复用原子 `increaseStock`；顺带修复并发生成流水号的快照读问题（`selectMaxTxnNoByPrefixForUpdate`，FOR UPDATE 当前读）。
- **测试**（Stage11IT，真实 MySQL 并发）：双线程同时退同一记录 → 恰好一成一败；库存只回增一次（6→10）；成本只冲减一次（354→0）；RETURN 流水恰一条。重复退料第二次被拒。事务回滚一致性由既有 Stage6IT 覆盖（继续通过）。
- **数据库变更**：无 DDL。

### P1-8 工单成本并发一致性（高危）

- **原始问题**：`issuePart`/`returnPart` 成本为 select→计算→updateById 覆盖写，并发丢账。
- **修复**：新增 `WoWorkOrderMapper.adjustSpareCost(orderId, delta)`：`spare_cost = GREATEST(COALESCE(spare_cost,0) + ?, 0)` 单条原子 UPDATE，DECIMAL 精度无浮点；出库传 `+amount`、退料传 `-amount`；成本可由 `sp_work_order_part` 流水追溯（`sumIssuedAmount` 校验口径保留）；未清空未重算既有数据。
- **测试**：同工单并发审批两笔（88.50×1、88.50×2）→ `spare_cost = 265.50` 精确无丢失 ✓。
- **数据库变更**：无 DDL。

---

## 三、修改文件清单

**后端（10 个修改 + 1 新增）**

| 文件 | 变更 |
|------|------|
| `security/TokenStore.java` | 降级策略 + requireAvailable + 刷新令牌登记/吊销 |
| `security/service/AuthService.java` | logout 签名增加 refreshToken 参数 |
| `security/service/impl/AuthServiceImpl.java` | 登录登记刷新令牌、刷新登记校验/一次性使用、登出全量吊销、失败计数降级调用 |
| `security/controller/AuthController.java` | logout 接受可选 body |
| `security/JwtAuthenticationFilter.java` | 注释澄清 DB 回源语义（代码逻辑未变） |
| `security/SecurityConfig.java` | apidocs 条件白名单 |
| `common/constant/RedisKeys.java` | 新增 userRefreshTokens |
| `common/result/ResultCode.java` | 新增 SERVICE_UNAVAILABLE(6001, 503) |
| `common/exception/GlobalExceptionHandler.java` | Redis 异常兜底 503 |
| `inventory/mapper/SpWorkOrderPartMapper.java` | 新增 transitionStatus |
| `inventory/mapper/SpStockTxnMapper.java` | 新增 selectMaxTxnNoByPrefixForUpdate |
| `workorder/mapper/WoWorkOrderMapper.java` | 新增 adjustSpareCost |
| `inventory/service/impl/InventoryServiceImpl.java` | 原子迁移/原子成本/当前读流水号/空 remark 修复 |
| `config/ProdSecurityGuard.java` | **新增** |

**配置与部署（4 个）**：`application-prod.yml`（新增）、`docker-compose.yml`、`Dockerfile.backend`、`deploy/env.example`

**前端（2 个）**：`static/m/scan.html`（重写脚本）、`static/js/app.js`（登出携带 refreshToken）

**测试（2 个新增）**：`src/test/java/com/equipcare/security/TokenStoreDegradationTest.java`、`src/test/java/com/equipcare/Stage11IT.java`

**脚本（2 个新增）**：`scripts/verify-phase11-xss.py`、`scripts/probe-scan.py`（辅助探针）

## 四、数据库变更说明

**无表结构变更、无数据迁移、无 DDL。** 所有修复基于现有列与现有 Redis 键空间；回滚方式 = 还原代码版本，无数据兼容问题。

## 五、遗留问题与残余风险（如实记录）

1. **Redis 宕机期间黑名单暂不可查**：访问令牌拉黑在故障窗口内不生效（fail-open）。认证仍需签名验证 + 数据库回源，不构成认证绕过；恢复后立即生效。刷新/登出等关键操作已 fail-closed。
2. **Redis 重启丢数据**：刷新令牌登记集合随之丢失 → 全部用户需重新登录（安全优先取舍）。
3. **Docker 容器级启动演练未执行**：本机无 Docker daemon；compose 语法与变量强校验已验证，容器编排实际启动需部署环境复核。
4. **Git 历史密钥审计未做**：本次改动未引入任何真实密钥；历史提交是否含敏感值超出本批范围，标记为待验证。
5. **本地开发库 root 弱口令**：仅本地环境；生产 compose 使用独立应用账号 + 强密码强制注入。
