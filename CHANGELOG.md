# 更新日志 · Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式，
版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 计划中
- 多租户第三步：租户管理页 / 配额 / 注册入口（step1 基础设施与 step2 登录链路已完成）
- Vue 迁移收尾：工单转派完整版 / 巡检模板与计划编辑表单 / 系统管理操作页
- 认证态 ZAP 深扫（ZAP context + 登录脚本）

## [0.2.0] - 2026-11-07

### Added（架构与平台能力）
- **Maven 多模块化**：单模块重构为六模块（common/system/framework/business/ai/admin），
  依赖方向由 ArchUnit 测试在 CI 强制
- **事件驱动架构**：领域事件（5 类）+ 事务性 Outbox（event_outbox，崩溃补投/指数退避/判死）
  + 事件桥接（站内消息/SSE/外发统一收口）
- **SSE 实时推送**：一次性票据鉴权、心跳与连接生命周期治理、Vue 与原生 JS 双端接入
- **AI 智能副驾**（equipcare-ai 模块）：故障诊断（SSE 流式）、智能派单 TOP3（算法预打分
  + LLM 理由）、维修报告生成；OpenAI 兼容多 Provider、默认关闭 fail-open
- **AI 深化**：巡检拍照缺陷识别（多模态视觉）、维修知识库 RAG（诊断自动引用）
- **预测性维护**：MTBF/MTTR（90 天窗口）+ 健康分线性外推 7 天 + 风险分级
- **多租户基础设施**：33 表 tenant_id 行级隔离 + TenantLineInnerInterceptor
  + JWT tid claim（单租户模式零功能变化，docs/17）
- **安全告警**：账号锁定企微/邮件通知（节流防刷屏）

### Added（功能）
- Vue 3 + Vite + TS 新前端（equipcare-web）：22 页（登录/驾驶舱/设备台账+详情/
  工单列表+详情+维修过程/巡检任务+执行+异常+计划/备件/健康/消息中心/用户/角色/审计）
- 前端体验：暗色模式、PWA、摄像头扫码（BarcodeDetector）、SSE 实时铃铛
- 通知外发：企业微信群机器人 / SMTP 邮件（事件监听即插即用）
- 数据范围收紧：工单详情可见性与列表数据范围对齐（WorkOrderAccessPolicy）

### Changed
- 去演示化：空库启动，seed 仅含系统基础数据（角色/权限/字典/SLA 政策）
- Element Plus 按需引入（Vue 主包 2.4MB → 419KB）

### Security（渗透测试整改，docs/15）
- 登录暴力破解防护 Redis 故障降级失效 → 进程内内存计数兜底（P1）
- CORS 任意来源+携带凭据 → credentials 关闭 + 来源配置化（P2）
- 上传 contentType 服务端按扩展名重映射（P3）
- 网关限流：登录 10r/m / 上传 20r/m / 一般 API 30r/s（nginx）

### 测试
- 后端 116（含架构守护/Outbox 退避/AI 向量/可见性策略/告警节流）
- 旧前端 65 + Vue 12（http 401 刷新链路/路由守卫/auth store）
- Playwright e2e：关键路径 + 巡检执行 + 维修流程（周调度）

## [0.1.0] - 2026-10

### Added
- 首版功能闭环：设备台账/二维码扫码巡检/七态工单状态机/SLA 预警/备件库存/
  健康分/站内消息/操作审计/Excel 导出
- 37 表 MySQL schema + 幂等增量脚本 + Docker Compose 一键部署
