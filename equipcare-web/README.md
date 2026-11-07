# equipcare-web · Vue 3 管理端（首批迁移）

Vue 3 + Vite + TypeScript + Element Plus + Pinia 的新版前端。**与旧原生前端（`equipcare-admin/src/main/resources/static/`）共存**，分批迁移，旧版 28 页功能完整可用。

## 新旧共存策略

| 环境 | 旧前端（原生 ES Module） | 新前端（Vue 3） |
|---|---|---|
| dev | 后端 8080 直接服务 | `npm run dev` → 5173，`/api` 代理到 8080 |
| prod | 继续从后端 `classpath:/static/` 服务 | `npm run build` → `dist/`，由 Nginx 挂 `/web/` 或独立域名单独发布 |

登录态共享：localStorage 键名与旧前端一致（`ec_access_token` 等），同一浏览器登录一次两边通用。

## 首批已迁移页面

- 登录页（双令牌 + 静默续期，行为与旧版对齐）
- 布局壳（服务端菜单 `/api/auth/menus` 按权限渲染；未迁移菜单自动折叠为「旧版入口」链接）
- 数据驾驶舱（指标卡 + 状态分布）
- 设备台账（搜索/状态筛选/健康分着色）
- 维修工单列表（分页/关键字/状态筛选）
- 巡检任务列表（状态筛选/只看我的/逾期标记）
- 备件台账（搜索/安全库存着色）

## 命令

```bash
npm install
npm run dev        # http://localhost:5173（需后端 8080 在跑）
npm run build      # 产物 dist/
```

## 迁移约定

1. 只迁移，不改后端：新页面必须复用既有 `/api` 接口与权限码；
2. 迁移完一个页面，在 `router/index.ts` 注册并在 `MIGRATED` 常量加路径前缀；
3. 旧前端在迁移完成前保持冻结（bug 修复除外）。
