# API 质量检查（阶段十二 · 第四阶段）

> 检查范围：20 个 Controller / 126 个端点，逐一核对权限注解、参数校验、返回结构、异常处理。
> 结论：**全部端点保持既有路径与响应结构兼容，未做破坏性修改**；本轮修复 2 处安全/校验缺陷（见第四节）。

## 一、总体结论

| 检查项 | 结论 |
|---|---|
| 请求方式 | 正确（GET 查询 / POST 动作 / PUT 全量 / PATCH 状态，语义统一） |
| 返回结构 | 统一 `Result{code, message, data, timestamp}`；文件下载返回流 + Content-Disposition（合理特例） |
| 异常处理 | GlobalExceptionHandler 覆盖 14 类异常（Biz/参数/405/404/406/403/401/409/413/Redis 503/500 兜底 traceId） |
| 权限注解 | 业务端点 100% @PreAuthorize；登录级端点（/auth/me、/messages、/depts 树、/users/options）有意放宽且服务层做归属/用途校验 |
| 分页 | MybatisPlus 分页插件统一钳制（单页上限 500），PageQuery 收口 |
| 重复/无用接口 | 未发现重复端点；占位页无假接口 |
| 数据范围 | 数据权限走 data_scope + 服务层归属校验（消息、工单动作、评价人） |

## 二、参数校验覆盖情况（@Valid）

**已覆盖（ requestBody DTO 均有 @Valid + 约束）**：登录/刷新/改密、设备增改、模板/计划、工单全动作、备件出入库 DTO（@Min(1)）、用户/角色、SLA 政策。

**本轮补齐**：
1. `POST /api/inventory/stock/adjust` —— quantity 原为裸 @RequestParam 无下限，补 `@Min(1)` + 控制器级 `@Validated`（修复 Q-02，见 03 文档）。

**已核实无需修改**（原可疑点，实际有防线）：
- `PATCH /api/inspection/plans/{id}/status`：status 裸参数，但服务层 `EnumCode.contains(PlanStatus...)` 白名单校验，非法值返回 400（InspectionPlanServiceImpl:252）。
- `PATCH /api/system/users/{id}/status`：服务层校验 0/1。
- 巡检日历 month 参数：服务层手工 parse，非法值返回 400。

## 三、已知行为特性（记录，不视为缺陷）

1. **校验先于鉴权**：Spring MVC 参数解析（含 @Valid）先于 @PreAuthorize 执行，因此非法负载对无权限用户返回 422 而非 403。不泄露数据、不影响正确性；合法负载下鉴权必然生效（Stage12IT 流程五已验证 403 全链路）。
2. **@Valid 个别缺口**：`POST /tasks/{id}/start`、`POST /anomalies/{id}/work-order` 的 DTO 无 @Valid，但二者必填字段在服务层显式校验，行为一致。
3. **登出 refreshToken body 未标 @Valid**：body 本身可选（不传则按 access token 主体全量吊销），无需校验。

## 四、本轮接口变更清单（兼容性评估）

| 端点 | 变更 | 兼容性 |
|---|---|---|
| POST /api/inventory/stock/adjust | quantity 增加 @Min(1) | **行为收紧**：原先可传负数（缺陷），现返回 422；无合法调用方受影响 |
| POST /api/files/upload | bizType 白名单 + 业务权限映射 | **行为收紧**：未知 bizType / 无对应业务权限返回 400/403；前端三个调用点（EQUIPMENT/INSPECT/WORK_ORDER）全部兼容 |
| GET /api/files/{id} | 下载鉴权（上传人/管理员/业务域查看权限） | **行为收紧**：无权限用户由 200 变 403；前端所有图片加载均携带业务域所需权限的登录态，已回归验证 |

其余 123 个端点零修改。
