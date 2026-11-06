# 数据一致性审计（阶段十二 · 第三阶段）

> 审计对象：全部多表更新业务（22 个 ServiceImpl 逐方法核对事务标注）+ 库存/工单/健康分实际数据核对。
> 测试佐证：Stage12IT 五条端到端流程 + Stage11IT 并发用例，197/197 通过。

## 一、事务边界审计结果

| 业务 | 多表写 | 事务保障 | 本轮处置 |
|---|---|---|---|
| 工单全状态流转 | wo_work_order + wo_status_log + wo_dispatch_log + 通知 | @Transactional + CAS（WHERE status=from AND version） | 正常 |
| 备件出库/退料 | sp_part + sp_stock_txn + sp_work_order_part + 工单成本 | @Transactional + 原子 SQL（`WHERE current_stock>=qty`、GREATEST 兜底）+ CAS 状态迁移 | 正常（Stage11IT 并发双退恰一成一败） |
| 设备状态变更 | eq_equipment（乐观锁 version） | @Transactional | 正常 |
| **健康分重算** | eq_health_snapshot + eq_equipment.health_score | 单台 @Transactional；**批量路径自调用失效** | **修复 Q-07**：改 TransactionTemplate，批量重算每台独立事务 |
| **计划作用域同步** | insp_plan_equipment 插新+删旧 | 原为两步独立提交 | **修复 Q-04**：TransactionTemplate 收敛同事务，消除半同步快照窗口 |
| 巡检任务生成 | insp_task 多条 + 水位 | 有意逐条提交 + `uk_insp_task_plan_equip_date` + INSERT IGNORE 幂等 | 维持原设计（注释声明），唯一键兜底，重启自愈 |
| 用户禁用/改密 | sys_user + Redis 全量吊销 | @Transactional + removeAllByUser | 正常 |
| 消息/审计 | 单表 + 异步线程池 | 异步隔离 | 正常 |

## 二、库存一致性（E2E 断言，Stage12IT 流程四）

- 入库 +5 → 领料出库 4 → 退料 4：台账 `current_stock` 与流水 `after_stock` **逐笔一致**（25 → 21 → 25）；
- 全库流水 `after_stock < 0` 记录数 = **0**；
- 工单 `spare_cost` 与领料金额一致（100.00×4=400），退料后冲减为 0；
- 并发场景（Stage11IT）：并发双退恰一成一败、并发出库成本精确累加 265.50。

## 三、本轮数据一致性修复明细

### Q-02 盘点调整可产生负库存（P0）
- **原缺陷**：`adjust(quantity=-5, positive=true)` 走 increaseStock，绕过 `current_stock>=qty` 防负闸。
- **修复**：控制器 @Min(1) + 服务层显式 `quantity<=0` 拒绝（双重防线）。
- **回归**：Stage12IT Q-02 用例（负数/零 422，正数盘盈盘亏正确）。

### Q-03 巡检任务 start 竞态（P1）
- **原缺陷**：start 先读状态再 updateById，与并发双开 / 调度器 markPendingExpired 竞态可覆盖状态。
- **修复**：改为 CAS 条件更新（`WHERE status IN (PENDING, EXPIRED)`），0 行受影响即拒绝。
- **回归**：Stage4IT 既有用例全绿（含重复开始拒绝路径）。

### Q-05 工单单号并发冲突（P1）
- **原缺陷**：「查最大值+1」并发报修撞 `uk_wo_order_no` → 用户报修 409 失败。
- **修复**：insertWithOrderNoRetry 撞键重取号最多 3 次（人工报修 + 异常转单两条路径）。

### Q-07 健康分批量重算无事务（P1）
- 见第一节；单接口路径行为不变。

## 四、数据核对（开发库 equipcare）

| 核对项 | 方法 | 结果 |
|---|---|---|
| 库存流水 vs 台账 | Stage12IT 流程四逐笔断言 | 一致 |
| 工单状态 vs 状态日志 | Stage12IT 流程三 to_status 子序列断言 | 一致（PENDING_ASSIGN→…→COMPLETED） |
| 评价唯一性 | uk_wo_eval_order + 重复评价 2004 | 一致 |
| 巡检提交唯一性 | countByTask + uk_insp_task_item 双闸 + 重复提交 2004 | 一致 |
| append-only 防篡改 | sys_audit_log / wo_status_log / sp_stock_txn 禁改触发器 | 在位 |

## 五、遗留风险（如实记录）

1. **巡检生成链路仍为逐条提交**（有意设计）：`syncScopeEquipment` 已收事务，但 insp_task 批量生成中途宕机可能少生成，下一轮调度自愈（INSERT IGNORE 幂等）。风险评估：低。
2. **Redis 短暂宕机窗口**：关键操作 fail-closed（6001 拒绝，本轮实测验证），非关键查询降级。不存在绕过认证风险。
3. 统计口径（巡检统计 / 成本统计）与页面数字的全量比对列入 07-known-issues 未验证清单。
