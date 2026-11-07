# v0.2.0 Release 正文（GitHub Release 复制用）

> 用法：发布 checklist（docs/18）执行完后，GitHub Releases → Draft a new release →
> Tag v0.2.0 → 粘贴下方正文（从 `---` 开始）。

---

# 巡维云 EquipCare v0.2.0 — 架构跃迁：事件驱动 + AI 副驾 + 多租户就绪

从「能跑的巡检系统」到「设备域业务框架」的一次完整跃迁。

## ⚡ 架构

- **Maven 六模块**：common / system / framework / business / ai / admin，依赖方向由 ArchUnit 在 CI 强制
- **事务性 Outbox**：领域事件与业务同事务落库，崩溃自动补投（指数退避/判死），
  消息 / SSE / 外发统一经事件总线分发
- **SSE 实时推送**：一次性票据鉴权、心跳与连接生命周期治理
- **多租户就绪**：33 表 tenant_id 行级隔离 + TenantLineInnerInterceptor + JWT tid claim
  （单租户模式零功能变化，docs/17 提供第三步演进路线）

## 🤖 AI 副驾（双端）

- 故障诊断（SSE 流式 Markdown：根因/步骤/备件/安全事项）
- 智能派单 TOP3（算法预打分 + LLM 理由，模型失败降级纯算法）
- 维修报告一键生成（Markdown 可复制下载）
- 巡检拍照缺陷识别（多模态视觉）+ 维修知识库 RAG（诊断自动引用）
- OpenAI 兼容协议：豆包 / 智谱 / DeepSeek 任选，默认关闭 fail-open

## 🖥 Vue 3 新前端（equipcare-web，22 页）

登录 / 驾驶舱 / 设备台账+详情 / 工单列表+详情+维修过程 / 验收 / 评价 /
巡检任务+执行+异常+计划+模板编辑 / 备件 / 健康 / 消息中心 / 用户 / 角色 / 审计日志。
工单闭环与巡检执行闭环在 Vue 端完整可用；Element Plus 按需引入（主包 gzip 101KB）。

## 🔒 安全（渗透测试整改，docs/15）

- 登录暴力破解防护 Redis 故障降级兜底（内存计数）
- CORS credentials 关闭 + 来源配置化；网关三级限流
- 上传 contentType 服务端重映射；工单详情数据范围收紧（策略类 + 穷举单测）
- 性能索引补充（EXPLAIN 驱动）；巡检异常列表附件 N+1 消除

## 📦 测试与工程

- 测试 200+：后端 116（含 ArchUnit 架构守护）+ 旧前端 65 + Vue 12 + Playwright e2e
- CI 三流水线：CI（三 job）/ Pages 展示站 / ZAP 基线扫描 / e2e 周跑
- Dependabot 依赖监控；CHANGELOG 与发布 checklist 齐备

## ⬇️ 升级

从 v0.1.0 升级：执行 `database/08~12` 幂等增量脚本即可（schema.sql 已包含全部变更）。
无破坏性变更；所有新能力（AI/通知外发/多租户）默认关闭或单租户模式。

---
**Full Changelog**: CHANGELOG.md
