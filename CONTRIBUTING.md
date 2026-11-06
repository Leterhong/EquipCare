# 贡献指南 · Contributing

感谢你对 **巡维云 EquipCare** 的关注！无论提交 Issue、补充文档还是贡献代码，都欢迎。

## 提交 Issue

- **Bug**：请附「复现步骤 + 期望行为 + 实际行为 + 后端日志关键行（含 traceId）」；
- **Feature**：先描述要解决的真实痛点，再谈方案；涉及新模块请说明对
  [模块依赖方向](equipcare-server/README.md#2-目录结构) 的影响。

## 本地开发

```bash
# 后端（JDK 17+，本地需 MySQL 8 + Redis）
cd equipcare-server
mvn spring-boot:run -pl equipcare-admin

# 前端测试（Vitest + jsdom，无需启动后端）
cd frontend-tests && npm ci && npx vitest run

# 黑盒冒烟（需先启动后端）
python scripts/smoke-e2e.py
```

## 提交 PR 前的自检清单

- [ ] `mvn test` 全绿（含 `ArchitectureRulesTest` 架构守护）
- [ ] `npx vitest run` 全绿（改动了前端时）
- [ ] 新增接口带 `@PreAuthorize` 权限码与 `@AuditLog` 审计（写操作）
- [ ] 状态类变更通过领域事件发布（见 `docs/12-创新升级说明.md` §2.2），不直调下游
- [ ] 不引入新的顶层依赖；确需引入请在 PR 描述中说明理由
- [ ] 提交信息格式：`type(scope): subject`，如 `feat(workorder): SLA 升级通知支持企业微信`

## 架构红线（改动会被 CI 架构测试拦截）

1. `equipcare-common` 不允许依赖任何上层模块；
2. 业务模块（asset/workorder/inspection/inventory/analytics/export/notification/ai）
   不允许依赖 framework（横切设施经 common 接口反转）；
3. `OutboxEventArrived` 信封只允许 `notification` 模块消费；
4. 工单状态只能经 `WoWorkOrderMapper.casUpdateStatus`（CAS）变更。

## License

提交即表示你同意代码以 [MIT](LICENSE) 协议开源。
