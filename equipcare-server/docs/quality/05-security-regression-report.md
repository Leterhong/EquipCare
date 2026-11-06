# 安全回归报告（阶段十二 · 第六阶段）

> 原则：对 Phase 11 审计的 P1 问题逐项回归验证，不假设已修复；本轮新发现问题立即整改。
> 全部结论有自动化测试佐证（197 后端 + 60 前端 + 8 Playwright 套件）。

## 一、Phase 11 整改项回归验证（全部有效）

| Phase 11 项 | 回归证据 | 结论 |
|---|---|---|
| 扫码页 XSS（DOM API + token 白名单） | verify-phase11-xss.py 4/4（恶意 HTML 字面渲染、恶意 token 拒绝、无脚本执行） | 有效 |
| Redis 故障降级（非关键降级/关键 fail-closed） | TokenStoreDegradationTest 10/10；**本轮 Redis 实际宕机事故：refresh 全部 6001 拒绝、无任何绕过认证路径** | 有效（真实故障演练通过） |
| Refresh Token 生命周期（登出吊销/一次性/禁用拦截/隔离） | Stage11IT 4 用例 + 本轮回归全绿 | 有效 |
| 生产密钥治理（ProdSecurityGuard 启动拒绝默认值） | 组件在位（Phase 11 实测），本轮未改动 | 有效 |
| Swagger 生产关闭 | application-prod.yml + 条件白名单（Phase 11 实测 401） | 有效 |
| 退料并发双退 / 出库成本原子 | Stage11IT 并发用例本轮复跑通过 | 有效 |
| JWT 密钥与 DB 口令环境变量注入 | application-prod.yml 无默认值 | 有效 |

## 二、本轮新发现与整改

### Q-01 文件服务水平越权 IDOR（P0，已修复）
- **原缺陷**：GET /api/files/{id} 无归属/权限校验，任意登录用户可按自增 id 枚举下载他人附件（巡检现场照片、工单照片）；上传无权限码。
- **修复**：上传 bizType 白名单（EQUIPMENT/INSPECT/WORK_ORDER）+ 按业务域映射操作权限；下载三路放行（上传人本人 / ADMIN·EQUIP_ADMIN / 所属业务域查看权限），未知 bizType 一律拒绝。
- **回归**：Stage12IT Q-01 两用例（越权 403、白名单 400/403/200）。

### Q-02 盘点调整负库存（P0，已修复）→ 见 03 文档。

### 测试数据污染事件（记录）
- Stage12IT 首轮「viewer 越权下载成功」实为 Stage5IT 遗留的 VIEWER 角色带 `wo:workorder:view`（ensureRole 复用旧角色）。修复为 ST12_VIEWER 专属角色码 + 清理，越权正确返回 403。**非产品缺陷**，已写入测试注释防复发。

## 三、在防控制核对（源码级）

| 项 | 状态 |
|---|---|
| SQL 注入 | 全部 MyBatis-Plus / 参数化 SQL，无字符串拼接（抽查确认） |
| XSS | 前端 h() 默认 esc 转义，唯二 html: 白名单（statusTag/优先级标签）为受控内容 |
| 越权访问 | @PreAuthorize 100% 业务端点 + 状态机角色校验 + 归属校验（消息/评价/任务） |
| CSRF | 无 Cookie 会话（Bearer header + 无状态 JWT），CSRF 不适用 |
| 敏感信息日志 | 密码字段 @JsonIgnore 不入缓存；审计切面密码脱敏；登录失败计数 |
| 文件上传 | 扩展名白名单 + 10MB 上限 + UUID 存储名 + 目录穿越防护 + **本轮新增业务权限** |

## 四、遗留（如实记录，见 07）

1. dev profile 默认弱口令（已有 prod 防线 + ProdSecurityGuard 拒绝启动）——部署期替换，非代码缺陷；
2. Git 历史密钥扫描未执行（本轮新增密钥均走环境变量）；
3. Redis 宕机窗口内黑名单查询降级依赖签名+DB 校验（设计权衡，已在 Phase 11 记录）。
