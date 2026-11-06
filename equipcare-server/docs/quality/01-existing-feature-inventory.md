# 巡维云 EquipCare — 现有功能清单（阶段十二 · 第一阶段）

> 生成时间：2026-10-31 ｜ 审查方式：全量源码审查（20 Controller / 22 ServiceImpl / 37 张表 / 41 前端路由）+ 自动化回归验证
> 状态口径：**正常** = 有自动化测试或本轮实际验证通过；**部分正常** = 核心可用但有已知缺陷（编号见 07-known-issues.md）；**存在问题** = 确认缺陷待修复；**未验证** = 本轮未实际执行；**占位页面** = placeholder，无业务逻辑。
> 铁律：未实际测试过的功能一律不标「正常」。

## 一、模块功能矩阵

### 1. 用户与权限（system / security）

| 模块 | 功能 | 前端页面 | 后端接口 | 数据库 | 当前状态 |
|---|---|---|---|---|---|
| 认证 | 登录 / 退出 / 令牌刷新 / 我的信息 | app.js bootstrap + index.html 登录页 | POST /api/auth/login、/logout、/refresh、GET /me | sys_user + Redis 令牌存储 | 正常（Stage11IT 4 用例 + TokenStoreDegradationTest 10 用例） |
| 认证 | 改密 / 改资料 | profile.js | PUT /api/auth/password、/profile | sys_user | 正常（EquipCareApplicationIT 覆盖） |
| 用户管理 | 增删改查 / 启禁用 / 重置密码 | users.js | /api/system/users 9 端点（system:user:*） | sys_user、sys_user_role | 部分正常（PATCH status 入口为裸参数，服务层已校验 0/1；见 API 清单 #07） |
| 角色权限 | 角色 CRUD / 权限分配 / 权限树 | roles.js、permissions.js | /api/system/roles*、/permissions*（system:role:manage） | sys_role、sys_permission、sys_role_permission | 正常（EquipCareApplicationIT 覆盖） |
| 菜单 | 前端菜单树（按权限过滤） | app.js NAV_TREE | GET /api/auth/menus | — | 正常（Phase10 nav 验证脚本） |
| 接口权限 | @PreAuthorize 100% 业务端点覆盖 | — | — | — | 部分正常（**缺陷 Q-01**：GET /api/files/{id} 下载无归属校验，见 03 文档） |
| 审计 | 操作日志记录与查询 | audit.js | GET /api/system/audit-logs + @AuditLog 切面 | sys_audit_log（禁改触发器） | 正常（异步落库 + 密码脱敏，源码核verify） |

### 2. 设备资产（asset）

| 模块 | 功能 | 前端页面 | 后端接口 | 数据库 | 当前状态 |
|---|---|---|---|---|---|
| 设备台账 | 列表 / 查询 / 新增 / 编辑 / 详情 / 删除 | equipment-list.js、equipment-form.js、equipment-detail.js | /api/asset/equipment 14 端点 | eq_equipment（乐观锁 version、uk 资产编号） | 正常（Phase10/11 UI 回归 + Stage3IT） |
| 设备状态 | 变更状态（白名单 + 乐观锁） | equipment-detail.js | PATCH /{id}/status | eq_equipment.status | 正常（源码核verify CAS） |
| 设备二维码 | 生成 / 图片 / 重置 token | equipment-qrcode.js | /{id}/qrcode* 3 端点 | qr_token | 正常（Stage3IT） |
| 设备图片附件 | 绑定 / 解绑 / 展示 | equipment-detail.js | /{id}/files 3 端点 | eq_equipment_file | 正常（Stage3IT 10 用例） |
| 设备分类 | 树 / CRUD（环校验） | categories.js | /api/asset/categories | eq_category | 正常 |
| 设备位置 | 树 / CRUD | —（位置选择器内嵌表单） | /api/asset/locations | eq_location | 正常（作为表单组件使用；独立管理页未规划，非缺陷） |
| 历史关联 | 设备维修 / 巡检历史 | equipment-detail.js | /{id}/repair-history、/{id}/inspect-history | wo_work_order、insp_task | 正常（逻辑删除设计：设备删除不物理删除历史，源码核verify） |
| 设备打印 | 档案打印页 | equipment-print.js | —（前端渲染） | — | 正常（Phase10 截图回归） |
| 扫码 | 移动端扫码识别 | m/scan.html | GET /scan（qr_token 换设备） | eq_equipment | 正常（verify-phase11-xss.py 4/4） |

### 3. 巡检管理（inspection）

| 模块 | 功能 | 前端页面 | 后端接口 | 数据库 | 当前状态 |
|---|---|---|---|---|---|
| 巡检模板 | CRUD / 检查项维护 / 发布停用 | inspect-template.js | /api/inspection/templates 8 端点 | insp_template、insp_template_item | 正常（Stage4IT） |
| 巡检计划 | CRUD / 启停 / 作用域设备 / 手动生成 | inspect-plan.js | /api/inspection/plans 7 端点 | insp_plan、insp_plan_equipment | 部分正常（**缺陷 Q-04**：作用域设备同步两步写无事务，靠唯一键幂等自愈；PATCH status 裸参数见 API 清单 #07） |
| 任务生成 | 调度生成（fixedDelay 60s）/ 水位 / 幂等 | —（后台） | scheduler → InspectionGenerationService | insp_task（uk_insp_task_plan_equip_date 防重） | 部分正常（幂等有唯一键兜底；单号并发窗口见 **Q-05**） |
| 任务执行 | 开始 / 逐项判定 / 提交（必检+跳过原因+照片） | inspect-task.js、inspect-execute.js | POST /tasks/{id}/start、/submit | insp_task、insp_task_item | 部分正常（提交有唯一键双闸；**缺陷 Q-03**：start 无 CAS 条件更新，并发/与逾期标记竞态） |
| 巡检异常 | 生成 / 列表 / 关闭 / 转工单 | inspect-anomaly.js | /anomalies 4 端点 | insp_anomaly（uk_wo_anomaly 防重复转单） | 正常（Stage4IT） |
| 巡检日历 | 月视图 / 双视图切换 | inspect-calendar.js | GET /calendar | insp_task | 正常（Phase10 验证脚本 34 格） |
| 巡检统计 | 完成率 / 异常率 / 计划维度 | inspect-stats.js | GET /stats | 聚合查询 | 未验证（本轮 E2E 未覆盖统计口径核对，列入 07 待办） |
| 逾期处理 | 调度标记 EXPIRED | —（后台） | markPendingExpired | insp_task | 正常（源码核verify） |

### 4. 维修工单（workorder）

| 模块 | 功能 | 前端页面 | 后端接口 | 数据库 | 当前状态 |
|---|---|---|---|---|---|
| 工单全流程 | 报修→派单→接单→维修→提交验收→验收/驳回→评价→关闭 | workorder-form/list/detail/repair/verify/timeline.js | /api/workorder 21 端点 | wo_work_order + 6 张子表 | 正常（WorkOrderStateMachineTest 85 断言 + Stage5IT + CAS 状态机 + wo_status_log append-only 触发器） |
| 派单 | 指派 / 转派 / 撤回 / 强制转派 | workorder-dispatch.js | /dispatch、/transfer、/revoke、/force-transfer | wo_dispatch_log | 正常（Stage5IT） |
| 维修工作台 | 我的任务队列 | workorder-workbench.js | GET /workbench | wo_work_order | 正常 |
| 工单看板 | 双视图列表/看板 | workorder-list.js | GET / + /status-count | wo_work_order | 正常（Phase10 验证 5 列 35 卡） |
| 异常转工单 | 巡检异常一键转单 | inspect-anomaly.js | POST /anomalies/{id}/work-order | uk_wo_anomaly 防重 | 正常（Stage4IT） |
| 工单评价 | 验收后评价 | workorder-detail.js | POST /{id}/evaluate | wo_evaluation（uk 防重） | 正常（Stage5IT） |

### 5. 备品备件（inventory）

| 模块 | 功能 | 前端页面 | 后端接口 | 数据库 | 当前状态 |
|---|---|---|---|---|---|
| 备件档案 | CRUD / 分类 | spare-part.js | /api/inventory/parts*、/categories | sp_part（uk_sp_part_no）、sp_part_category | 正常（Stage6IT） |
| 入库 / 出库 | 原子增减 + 流水快照 | spare-stock-in.js、spare-stock-out.js | POST /stock/in、/stock/out（@Min(1) DTO） | sp_stock_txn（append-only 触发器） | 正常（Stage6IT + Stage11IT 并发用例） |
| 盘点调整 | 库存调整 | spare-stock.js | POST /stock/adjust | sp_stock_txn | **存在问题（缺陷 Q-02）**：quantity 为裸 @RequestParam 无 @Min，负数+positive 组合可绕过防负库存闸（本轮修复） |
| 工单领料 | 申请 → 出库 → 退料 | workorder-detail.js | /applies 5 端点 | sp_work_order_part | 正常（Stage11IT 并发双退恰一成一败；CAS transitionStatus） |
| 库存预警 | 低于安全库存提醒 | spare-alert.js | GET /parts/below-safety + 调度 | sys_message | 正常（每日去重）；时区硬编码小问题（**Q-06**，低风险） |
| 库存流水 | 查询 / 分页 | spare-txn.js | /stock/txns* 2 端点 | sp_stock_txn | 正常 |
| 成本统计 | 耗材成本 | dashboard.js | GET /stats/cost | 聚合 | 未验证（口径核对列入 07 待办） |

### 6. 驾驶舱 / 健康 / SLA / 通知 / 导出（analytics / sla / notification / export）

| 模块 | 功能 | 前端页面 | 后端接口 | 数据库 | 当前状态 |
|---|---|---|---|---|---|
| 数据驾驶舱 | 五层聚合 / 图表 | dashboard.js | /api/analytics/dashboard/summary、/charts | 聚合查询（07-stage 索引） | 正常（Stage7IT + Phase10 验证；无随机数，全部实查） |
| 设备健康 | 评分 / 快照 / 重算 / 高危列表 | health-list.js、health-detail.js | /api/analytics/health* 4 端点 | eq_health_snapshot、eq_equipment.health_score | 部分正常（**缺陷 Q-07**：recalculateAll 自调用致批量重算无事务，本轮修复） |
| SLA 政策 | 级别时限配置 | sla-monitor.js | /api/sla/policies 2 端点 | sla_policy | 正常（Stage6IT） |
| SLA 监控 | 活跃工单 / 预警 / 扫描 | sla-monitor.js | /active-orders、/alerts、/alerts/scan | sla_alert_record（uk 每日去重） | 正常（调度扫描 Stage6IT 覆盖） |
| 通知中心 | 列表 / 未读数 / 已读 | messages.js | /api/system/messages 5 端点 | sys_message | 正常（服务层归属校验，Stage7IT） |
| 数据导出 | 设备/巡检/工单/库存/成本 xlsx | 列表页导出按钮 | /api/export/* 5 端点 | 流式写出 | 正常（Stage7IT） |
| 文件上传 | 附件上传 / 下载 | 各详情页 | /api/files/upload、GET /{id} | sys_file | **存在问题（缺陷 Q-01）**：下载无归属/权限校验（IDOR 水平越权），上传无权限码（本轮修复） |
| 消息轮询 | 未读数 30s 轮询 | app.js | GET /unread-count | sys_message | 正常 |

### 7. 占位页面（按要求保持原状，不开发不删除）

| path | 页面 | 状态 |
|---|---|---|
| /equipment/location | 设备位置 | 占位页面 |
| /equipment/lifecycle | 设备生命周期 | 占位页面 |
| /workorder/records | 维修记录 | 占位页面 |
| /analytics/fault | 故障分析 | 占位页面 |
| /analytics/cost | 维修成本 | 占位页面 |
| /analytics/report | 运维报表 | 占位页面 |
| /system/config | 系统配置 | 占位页面 |

## 二、量化汇总

| 维度 | 数量 |
|---|---|
| 后端 Controller | 20 |
| 后端端点 | 126（经逐一核对 @PreAuthorize 覆盖，文件/部门树等登录级端点除外） |
| ServiceImpl | 22 |
| 数据库表 | 37（全部 CREATE TABLE IF NOT EXISTS，可重复执行） |
| 前端路由 | 41（34 真实页面 + 7 占位） |
| 定时任务 | 3（巡检生成/逾期、SLA 扫描、库存预警，均可配置关闭） |
| Java 测试 | 12 类 189 用例（本轮全绿） |
| Vitest | 6 文件 60 用例 |
| Playwright 脚本 | 26 |

## 三、缺陷索引（详见 07-known-issues.md）

| 编号 | 严重度 | 一句话 | 本轮处置 |
|---|---|---|---|
| Q-01 | P0 | 文件下载 IDOR 越权 + 上传无权限码 | 修复 |
| Q-02 | P0 | 库存调整无数量校验可致负库存 | 修复 |
| Q-03 | P1 | 巡检任务 start 无 CAS 并发竞态 | 修复 |
| Q-04 | P1 | 计划作用域同步两步写无事务 | 修复（事务边界收敛） |
| Q-05 | P1 | 工单单号并发冲突导致报修 409 | 修复（重试） |
| Q-06 | P2 | 库存预警时区硬编码与调度配置脱节 | 修复 |
| Q-07 | P1 | 健康分批量重算 @Transactional 自调用失效 | 修复 |
| Q-08 | P2 | 计划启停 status 裸参数无入口校验 | 核实非缺陷（服务层 EnumCode 白名单已校验） |
| Q-09 | P2 | 工单详情 realName N+1 查询 | 记录待办（性能，不影响正确性） |
| Q-10 | P2 | 前端 workbench/board 权限码与后端冗余定义 | 记录待办（模型收敛需与权限矩阵联评） |
| Q-11 | P2 | dev 默认口令（已有 prod 防线 + ProdSecurityGuard） | 记录待办（部署期替换） |
