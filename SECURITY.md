# 安全策略 · Security Policy

## 支持版本

| 版本 | 状态 |
|---|---|
| main 分支 | ✅ 接收安全修复 |

## 报告漏洞

**请勿公开 Issue 披露安全漏洞。**

请通过 GitHub Security Advisories（仓库 Security 页 → Report a vulnerability）
私下报告，或邮件联系 1977642959@qq.com（主题注明 `[EquipCare Security]`）。

我们承诺 72 小时内确认、7 天内给出评估与修复计划。

## 已知安全设计

- JWT 密钥 / 数据库口令全部经环境变量注入，`prod` profile 由
  `ProdSecurityGuard` 强校验（默认密钥拒绝启动）；
- 工单状态、审计日志、库存流水均为只插入表（数据库触发器兜底）；
- 设备二维码仅含随机标识（`token`），不含任何账号信息；
- AI 模块默认关闭，API Key 仅经环境变量注入，不落库不进日志。

## 上线前必查

1. 更换 `EQUIPCARE_JWT_SECRET`（≥32 字节随机值）；
2. 修改 MySQL 应用账号与 Redis 口令（拒绝示例弱口令）;
3. 启用 HTTPS（反代终止即可）并关闭 Swagger（prod 已默认关闭）；
4. 管理员引导完成后立即 `EQUIPCARE_BOOTSTRAP_ENABLED=false`。
