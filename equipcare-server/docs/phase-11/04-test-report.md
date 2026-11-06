# 巡维云 EquipCare · Phase 11 测试报告

> 生成时间：2026-10-31
> 环境本机：Windows / JDK 17（temurin-24 运行时构建）/ MySQL 8（127.0.0.1:3306）/ Redis 5.0.14（127.0.0.1:6379）
> 原则：全部为真实执行结果；未执行项如实标注。

---

## 一、总览

| 层级 | 命令 | 结果 |
|------|------|------|
| 后端全量（单测 + 集成） | `mvn verify` | **189/189 通过**（单测 121 + 集成 68），BUILD SUCCESS |
| 前端单测 | `npx vitest run`（frontend-tests） | **60/60 通过**，0 未处理错误 |
| 前端 E2E 回归 | 8 个 Playwright 脚本 | 全部通过，**0 控制台错误** |
| prod 安全基线 | jar 启动实测 | 4 项启动/拒绝场景全部符合预期 |
| Docker 配置 | `docker compose config` | 语法与变量强校验通过 |

## 二、新增测试明细

### 2.1 TokenStoreDegradationTest（批二 · Mockito 单元测试，10/10）

| 用例 | 结果 |
|------|------|
| Redis 宕机：黑名单查询降级 false | ✓ |
| Redis 宕机：会话读取降级 null（触发 DB 回源） | ✓ |
| Redis 宕机：会话/黑名单写入静默降级 | ✓ |
| Redis 宕机：登录失败计数降级，登录可继续 | ✓ |
| Redis 宕机：探活 false（关键操作 fail-closed） | ✓ |
| Redis 正常：PING 探活成功 | ✓ |
| Redis 恢复后：黑名单语义恢复 | ✓ |
| 刷新令牌登记集合缺失 → 拒绝刷新 | ✓ |
| 刷新令牌已登记 → 校验通过 | ✓ |
| Redis 宕机：刷新登记校验安全失败 | ✓ |

### 2.2 Stage11IT（批三/七/八 · 真实 MySQL+Redis 集成，8/8）

| 用例 | 结果 |
|------|------|
| 1 正常登录与刷新，旧 refreshToken 重放被拒（1006） | ✓ |
| 2 登出吊销：access(1004)/refresh(1006) 均失效；不带 body 登出同样全量吊销 | ✓ |
| 3 用户隔离：吊销 A 不影响 B 刷新 | ✓ |
| 4 禁用用户刷新被拒(1002)，恢复状态后旧令牌仍不可用 | ✓ |
| 5 过期 refreshToken 被拒 | ✓ |
| 6 并发双退：恰一成一败、库存/成本各只变一次、RETURN 流水一条 | ✓ |
| 7 重复退料第二次被拒(2004)，库存成本一致 | ✓ |
| 8 并发出库记账：成本 = 88.50×1 + 88.50×2 = 265.50 无丢失 | ✓ |

### 2.3 verify-phase11-xss.py（批一 · Playwright，4/4）

恶意设备名（img/svg onerror、script 标签）字面渲染不执行 ×2；恶意 URL token 显示无效且零请求；正常扫码功能可用。截图存 `docs/screenshots/phase11/xss-*.png`。

## 三、全量回归（防破坏验证）

| 套件 | 关键断言 | 结果 |
|------|----------|------|
| Stage3-Stage7IT（既有 60 项集成） | 登录权限 / 设备 / 巡检 / 工单状态机 / 库存闭环 / SLA / 导出 | 全部通过 |
| WorkOrderStateMachineTest + JwtTokenProviderTest 等 121 项单测 | 状态机 / 令牌签名 | 全部通过 |
| verify-phase10-light | 浅色主题 `rgb(245,247,250)`、0 控制台错误 | ✓ |
| verify-phase10-nav | 32 项导航树、占位页、带参直达 | ✓ |
| verify-phase10-dash | 驾驶舱 4 核心卡 + 9 图 + 14 指标 + 动态流 | ✓ |
| verify-phase10-equip | 设备统计卡 + 详情 6 Tab | ✓ |
| verify-phase10-inspect | 双视图 Tab、状态 chips、日历 | ✓ |
| verify-phase10-workorder | 看板 5 泳道 35 卡、详情双栏、库存浅橙预警 | ✓ |
| verify-phase10-mobile | 390px 五场景无溢出、8 项交互基建 | ✓ |

## 四、生产配置验证（真实执行）

| 场景 | 预期 | 实测 |
|------|------|------|
| prod 无 EQUIPCARE_JWT_SECRET | 拒绝启动 | ✓「secret 未配置或长度不足 32 字节」 |
| prod 注入开发默认密钥 | 拒绝启动 | ✓「生产环境检测到未更换的 JWT 密钥」 |
| prod 注入随机强密钥 | 正常启动 | ✓ 日志「安全基线校验通过」 |
| prod 下 /swagger-ui.html、/v3/api-docs | 匿名被拒 | ✓ 401 |
| prod 下 /api/auth/login | 正常 | ✓ 200 |
| docker compose config（缺 .env） | 报错提示 | ✓ 强制变量校验生效 |

## 五、未执行 / 无法验证项（如实说明）

1. **Docker 容器级启动演练**：本机无 Docker daemon，未执行 `docker compose up` 全链路。已验证 compose 语法、变量强校验、Dockerfile 逻辑审查；需在部署环境复核。
2. **真实 Redis 停机集成演练**：Redis 故障行为由 Mockito 单测覆盖（10 用例）；未在集成环境物理断开 Redis 重放全链路（避免影响共享测试基础设施），该局限如实记录。
3. **Git 历史敏感信息扫描**：未执行（超出本批范围）。

## 六、结论

- 后端 `mvn verify` 189/189、前端 Vitest 60/60、Playwright 8 套件 0 控制台错误，**无一项被跳过或禁用**；
- 8 项 P1 修复均有对应自动化测试或真实启动验证；
- **可以进入下一阶段。**
