# 前端功能回归报告（阶段十二 · 第五阶段）

> 回归方式：Vitest 单元 60 用例 + Playwright 浏览器级 8 套件全量回归（桌面 1600 / 平板 800 / 移动 390 三视口）。
> 结论：**41 条路由全部可达（34 真实页面 + 7 占位页），0 console 错误，无横向溢出。**

## 一、本轮修复的前端缺陷（均带回归脚本）

| 缺陷 | 根因 | 修复 | 验证 |
|---|---|---|---|
| 备件出入库表单卡片被拉伸、按钮下方大片空白 | `.grid-2` 默认 stretch，右侧 8 条流水撑高行 | `.no-stretch { align-self:start }` + `card-body` 包裹（18px 内边距） | verify-phase11-ui-padding.py：内边距 18px、卡高 446 < 记录卡 592 |
| 出入库表单字段/按钮贴卡片边缘 | formHost 直挂 .card 无内边距 | 同上 | 同上 |
| 设备位置/设备生命周期/维修记录打开报「页面加载失败」 | `/equipment/:id` 参数路由截胡静态路由，以 id="location" 打接口 400 | matchRoute 两段匹配（静态优先） | verify-phase11-route-fix.py 13/13 |
| 侧栏「设备档案+设备位置」双高亮 | isNavActive 前缀匹配未让位精确匹配 | claimed→exactExists→前缀 三级判定 | 同上 |
| 顶栏用户名不可见 / 折叠侧栏只剩点 | color-scheme dark 遗留 + 折叠无文字 | color-scheme:light + nav-abbr 首字徽标 | Phase 11 修复，本轮回归确认 |

## 二、页面交互回归矩阵（Playwright 实测）

| 套件 | 覆盖 | 结果 |
|---|---|---|
| verify-phase10-dash | 数据驾驶舱五层（指标卡/图表/预警/待办/趋势） | 0 console 错误 |
| verify-phase10-workorder | 工单看板 5 列 35 卡、详情双栏、返回按钮 | 0 console 错误 |
| verify-phase10-inspect | 巡检双视图 Tab、状态筛选、日历 34 格、执行页校验 | 0 console 错误 |
| verify-phase10-mobile | 390px 五场景（扫码/执行/异常/工单/维修）无横向溢出 | 0 console 错误 |
| verify-phase10-light | 浅色主题全站 + 平板 800px | 0 console 错误 |
| verify-phase11-xss | 扫码页 XSS（字面渲染/恶意 token/正常功能） | 4/4 |
| verify-phase11-route-fix | 静态路由优先 + 导航单高亮 | 13/13 |
| verify-phase11-ui-padding | 表单内边距 + 卡片高度 + 脏数据清理 | 6/6 |

## 三、表单与状态覆盖（Vitest）

- 巡检执行页提交校验（必检项跳过原因/异常说明/照片）9 用例；
- 入库页表单验证 5 用例；
- 统一 API 客户端：401 静默续期（单飞）、续期失败回登录、业务错误处理 11 用例；
- XSS 转义 / 空表格 / 分页 / 格式化 15 用例；
- 工单状态映射与后端 7 态对齐 10 用例。

## 四、按钮与空态核查

- 所有占位菜单（设备位置/生命周期/维修记录/故障分析/维修成本/运维报表/系统配置）均为「建设中」空态，无假数据、无死按钮；
- 列表页均有空态（table-empty）、加载骨架（skeleton）、请求失败红字提示；
- 事件绑定：路由切换执行 pageCleanups 钩子（图表实例/定时器释放），无重复绑定与泄漏路径（渲染令牌 renderToken 防竞态）。

## 五、遗留观察（记录待办）

- 工单详情 realName N+1（后端性能，前端无感）；
- 巡检统计/成本统计页面数字与 DB 的全量口径比对未执行（见 07）。
