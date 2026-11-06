/**
 * 应用壳：服务端菜单 + 前端路由。
 *
 * 菜单由前端站点地图 NAV_TREE 组织，按当前用户权限表（/api/auth/me）过滤；
 * 即使有人手工改 URL 进入页面，页面内的数据接口仍会被后端 @PreAuthorize 拒绝。
 */

import { api, auth, redirectToLogin } from './api.js';
import { esc, h, toast, toastError, qs } from './ui.js';
import { isRealtimeConnected, resetRealtime, startRealtime, stopRealtime } from './realtime.js';

const ROUTES = [
    { path: '/equipment', title: '设备列表', perm: 'asset:equipment:view', module: () => import('./pages/equipment-list.js') },
    { path: '/equipment/new', title: '新增设备', perm: 'asset:equipment:create', module: () => import('./pages/equipment-form.js') },
    { path: '/equipment/:id/edit', title: '编辑设备', perm: 'asset:equipment:update', module: () => import('./pages/equipment-form.js') },
    { path: '/equipment/:id/qrcode', title: '设备二维码', perm: 'asset:equipment:qrcode', module: () => import('./pages/equipment-qrcode.js') },
    { path: '/equipment/:id/print', title: '设备档案打印', perm: 'asset:equipment:view', module: () => import('./pages/equipment-print.js') },
    { path: '/equipment/:id', title: '设备详情', perm: 'asset:equipment:view', module: () => import('./pages/equipment-detail.js') },
    { path: '/categories', title: '设备分类', perm: 'asset:category:manage', module: () => import('./pages/categories.js') },
    // 巡检中心：模板 → 计划 → 任务 → 执行 → 异常 → 日历 → 统计
    { path: '/inspect/template', title: '巡检模板', perm: 'inspect:template:view', module: () => import('./pages/inspect-template.js') },
    { path: '/inspect/plan', title: '巡检计划', perm: 'inspect:plan:view', module: () => import('./pages/inspect-plan.js') },
    { path: '/inspect/task', title: '巡检任务', perm: 'inspect:task:view', module: () => import('./pages/inspect-task.js') },
    { path: '/inspect/task/:id/execute', title: '执行巡检', perm: 'inspect:task:execute', module: () => import('./pages/inspect-execute.js') },
    { path: '/inspect/task/:id', title: '巡检任务详情', perm: 'inspect:task:view', module: () => import('./pages/inspect-task-detail.js') },
    { path: '/inspect/anomaly', title: '巡检异常', perm: 'inspect:anomaly:view', module: () => import('./pages/inspect-anomaly.js') },
    { path: '/inspect/calendar', title: '巡检日历', perm: 'inspect:calendar:view', module: () => import('./pages/inspect-calendar.js') },
    { path: '/inspect/stats', title: '巡检统计', perm: 'inspect:stats:view', module: () => import('./pages/inspect-stats.js') },
    // 维修工单中心：列表 → 报修 → 派单/工作台 → 详情 → 维修/验收/时间线/评价
    { path: '/workorder', title: '维修工单', perm: 'wo:workorder:view', module: () => import('./pages/workorder-list.js') },
    { path: '/workorder/new', title: '报修', perm: 'wo:workorder:create', module: () => import('./pages/workorder-form.js') },
    { path: '/workorder/workbench', title: '维修工作台', perm: 'wo:workorder:workbench', module: () => import('./pages/workorder-workbench.js') },
    { path: '/workorder/dispatch', title: '派单工作台', perm: 'wo:workorder:board', module: () => import('./pages/workorder-dispatch.js') },
    { path: '/workorder/:id/repair', title: '维修过程记录', perm: 'wo:workorder:repair', module: () => import('./pages/workorder-repair.js') },
    { path: '/workorder/:id/verify', title: '维修验收', perm: 'wo:workorder:verify', module: () => import('./pages/workorder-verify.js') },
    { path: '/workorder/:id/timeline', title: '工单操作时间线', perm: 'wo:workorder:view', module: () => import('./pages/workorder-timeline.js') },
    { path: '/workorder/:id/evaluate', title: '工单评价', perm: 'wo:workorder:evaluate', module: () => import('./pages/workorder-evaluate.js') },
    { path: '/workorder/:id', title: '工单详情', perm: 'wo:workorder:view', module: () => import('./pages/workorder-detail.js') },
    // 备件库存：档案 → 库存 → 入库/出库 → 流水 → 预警
    { path: '/spare/part', title: '备件管理', perm: 'spare:part:view', module: () => import('./pages/spare-part.js') },
    { path: '/spare/stock', title: '库存管理', perm: 'spare:stock:view', module: () => import('./pages/spare-stock.js') },
    { path: '/spare/stock/in', title: '备件入库', perm: 'spare:stock:in', module: () => import('./pages/spare-stock-in.js') },
    { path: '/spare/stock/out', title: '备件出库', perm: 'spare:issue', module: () => import('./pages/spare-stock-out.js') },
    { path: '/spare/txn', title: '库存流水', perm: 'spare:txn:view', module: () => import('./pages/spare-txn.js') },
    { path: '/spare/alert', title: '库存预警', perm: 'spare:alert:view', module: () => import('./pages/spare-alert.js') },
    // SLA 预警中心
    { path: '/sla/monitor', title: 'SLA 预警中心', perm: 'sla:monitor:view', module: () => import('./pages/sla-monitor.js') },

    { path: '/dashboard', title: '运维驾驶舱', perm: 'dashboard:view', module: () => import('./pages/dashboard.js') },
    { path: '/messages', title: '通知中心', perm: null, module: () => import('./pages/messages.js') },
    // 设备健康
    { path: '/health', title: '设备健康列表', perm: 'health:view', module: () => import('./pages/health-list.js') },
    { path: '/health/:id', title: '设备健康详情', perm: 'health:view', module: () => import('./pages/health-detail.js') },
    { path: '/users', title: '用户管理', perm: 'system:user:view', module: () => import('./pages/users.js') },
    { path: '/roles', title: '角色管理', perm: 'system:role:manage', module: () => import('./pages/roles.js') },
    { path: '/permissions', title: '权限管理', perm: 'system:role:manage', module: () => import('./pages/permissions.js') },
    { path: '/audit', title: '用户操作记录', perm: 'system:audit:view', module: () => import('./pages/audit.js') },
    { path: '/profile', title: '个人资料', perm: null, module: () => import('./pages/profile.js') },
    // 阶段十：站点地图占位页（规划中模块，统一占位展示，无假数据）
    { path: '/equipment/location', title: '设备位置', perm: 'asset:equipment:view', module: () => import('./pages/placeholder.js') },
    { path: '/equipment/lifecycle', title: '设备生命周期', perm: 'asset:equipment:view', module: () => import('./pages/placeholder.js') },
    { path: '/workorder/records', title: '维修记录', perm: 'wo:workorder:view', module: () => import('./pages/placeholder.js') },
    { path: '/analytics/fault', title: '故障分析', perm: 'dashboard:view', module: () => import('./pages/placeholder.js') },
    { path: '/analytics/cost', title: '维修成本', perm: 'dashboard:view', module: () => import('./pages/placeholder.js') },
    { path: '/analytics/report', title: '运维报表', perm: 'dashboard:view', module: () => import('./pages/placeholder.js') },
    { path: '/system/config', title: '系统配置', perm: 'system:user:view', module: () => import('./pages/placeholder.js') },
];

/** 阶段十站点地图：按产品规范八组组织；每项经前端权限表过滤（与后端 @PreAuthorize 同口径）。
 *  query：跳转时携带的筛选参数（如维修验收队列）；perm：可见所需权限码。 */
const NAV_TREE = [
    { group: '工作台', items: [
        { path: '/dashboard', title: '数据驾驶舱', perm: 'dashboard:view' },
    ] },
    { group: '设备资产', items: [
        { path: '/equipment', title: '设备档案', perm: 'asset:equipment:view' },
        { path: '/categories', title: '设备分类', perm: 'asset:category:manage' },
        { path: '/equipment/location', title: '设备位置', perm: 'asset:equipment:view' },
        { path: '/equipment/lifecycle', title: '设备生命周期', perm: 'asset:equipment:view' },
    ] },
    { group: '巡检中心', items: [
        { path: '/inspect/template', title: '巡检模板', perm: 'inspect:template:view' },
        { path: '/inspect/plan', title: '巡检计划', perm: 'inspect:plan:view' },
        { path: '/inspect/task', title: '巡检任务', perm: 'inspect:task:view' },
        { path: '/inspect/anomaly', title: '巡检异常', perm: 'inspect:anomaly:view' },
        { path: '/inspect/calendar', title: '巡检日历', perm: 'inspect:calendar:view' },
    ] },
    { group: '维修工单', items: [
        { path: '/workorder', title: '工单列表', perm: 'wo:workorder:view' },
        { path: '/workorder/dispatch', title: '待派单工单', perm: 'wo:workorder:board' },
        { path: '/workorder/workbench', title: '我的维修任务', perm: 'wo:workorder:workbench' },
        { path: '/workorder', title: '维修验收', perm: 'wo:workorder:verify',
          query: { status: 'PENDING_REVIEW' }, matchQuery: 'status=PENDING_REVIEW' },
        { path: '/workorder/records', title: '维修记录', perm: 'wo:workorder:view' },
    ] },
    { group: '备品备件', items: [
        { path: '/spare/part', title: '备件档案', perm: 'spare:part:view' },
        { path: '/spare/stock', title: '库存管理', perm: 'spare:stock:view' },
        { path: '/spare/txn', title: '入库记录', perm: 'spare:txn:view',
          query: { type: 'IN' }, matchQuery: 'type=IN' },
        { path: '/spare/txn', title: '出库记录', perm: 'spare:txn:view',
          query: { type: 'OUT' }, matchQuery: 'type=OUT' },
        { path: '/spare/alert', title: '库存预警', perm: 'spare:alert:view' },
    ] },
    { group: '运维分析', items: [
        { path: '/health', title: '设备健康', perm: 'health:view' },
        { path: '/inspect/stats', title: '巡检统计', perm: 'inspect:stats:view' },
        { path: '/sla/monitor', title: 'SLA 预警中心', perm: 'sla:monitor:view' },
        { path: '/analytics/fault', title: '故障分析', perm: 'dashboard:view' },
        { path: '/analytics/cost', title: '维修成本', perm: 'dashboard:view' },
        { path: '/analytics/report', title: '运维报表', perm: 'dashboard:view' },
    ] },
    { group: '消息通知', items: [
        { path: '/messages', title: '通知中心', perm: null },
    ] },
    { group: '系统管理', items: [
        { path: '/users', title: '用户管理', perm: 'system:user:view' },
        { path: '/roles', title: '角色管理', perm: 'system:role:manage' },
        { path: '/permissions', title: '权限管理', perm: 'system:role:manage' },
        { path: '/audit', title: '操作日志', perm: 'system:audit:view' },
        { path: '/system/config', title: '系统配置', perm: 'system:user:view' },
    ] },
];

/** 侧边栏分组（阶段十规范：工作台/设备资产/巡检中心/维修工单/备品备件/运维分析/消息通知/系统管理） */
function groupOf(path) {
    for (const { group, items } of NAV_TREE) {
        if (items.some((item) => item.path === path)) {
            return group;
        }
    }
    if (path.startsWith('/inspect')) return '巡检中心';
    if (path.startsWith('/workorder')) return '维修工单';
    if (path.startsWith('/spare')) return '备品备件';
    if (path.startsWith('/health') || path.startsWith('/sla') || path.startsWith('/analytics')) return '运维分析';
    if (path === '/users' || path === '/roles' || path === '/permissions' || path === '/audit') return '系统管理';
    if (path === '/messages') return '消息通知';
    if (path === '/dashboard') return '工作台';
    return '设备资产';
}

const state = {
    user: null,
    route: null,
};

/* ------------------------------------------------------------------ */
/* 路由匹配                                                            */
/* ------------------------------------------------------------------ */

function matchRoute(path) {
    const pathParts = path.split('/').filter(Boolean);
    const tryRoute = (route) => {
        const routeParts = route.path.split('/').filter(Boolean);
        if (routeParts.length !== pathParts.length) {
            return null;
        }
        const params = {};
        for (let i = 0; i < routeParts.length; i += 1) {
            if (routeParts[i].startsWith(':')) {
                params[routeParts[i].slice(1)] = decodeURIComponent(pathParts[i]);
            } else if (routeParts[i] !== pathParts[i]) {
                return null;
            }
        }
        return params;
    };
    // 两段匹配：先匹配不含参数的静态路由（如 /equipment/location），再匹配参数路由（如 /equipment/:id），
    // 避免 /equipment/location 被 /equipment/:id 以 id="location" 截胡导致详情页报 400。
    for (const route of ROUTES) {
        if (!route.path.includes(':')) {
            const params = tryRoute(route);
            if (params) {
                return { route, params };
            }
        }
    }
    for (const route of ROUTES) {
        if (route.path.includes(':')) {
            const params = tryRoute(route);
            if (params) {
                return { route, params };
            }
        }
    }
    return null;
}

function currentPath() {
    const hash = location.hash.replace(/^#/, '');
    return hash.split('?')[0] || '';
}

/** hash 内查询参数：/workorder?status=WAITING_VERIFY → {status:...} */
function currentQuery() {
    const hash = location.hash.replace(/^#/, '');
    return new URLSearchParams(hash.split('?')[1] || '');
}

function navigate(path, { replace = false } = {}) {
    const target = `#${path}`;
    if (replace) {
        history.replaceState(null, '', target);
        render();
    } else if (location.hash === target) {
        render();
    } else {
        location.hash = target;
    }
}

/* ------------------------------------------------------------------ */
/* 菜单                                                                */
/* ------------------------------------------------------------------ */

function defaultPath() {
    // 按路由表顺序找第一个「不含参数且有权限」的页面，避免只有巡检权限的账号落到个人资料
    const found = ROUTES.find((route) => !route.path.includes(':')
        && (!route.perm || auth.can(route.perm)));
    return found ? found.path : '/profile';
}

function buildMenu() {
    // 阶段十：按产品站点地图组织导航；权限过滤与后端 @PreAuthorize 同口径。
    const items = [];
    NAV_TREE.forEach(({ group, items: groupItems }) => {
        groupItems.forEach((item) => {
            if (item.perm && !auth.can(item.perm)) {
                return;
            }
            items.push({ ...item, group });
        });
    });
    items.push({ path: '/profile', title: '个人资料', group: '账号' });
    return items;
}

/** 分组展示顺序（与产品布局规范一致） */
const GROUP_ORDER = ['工作台', '设备资产', '巡检中心', '维修工单', '备品备件', '运维分析', '消息通知', '系统管理', '账号', '功能'];

/** 判断导航项是否命中当前地址（含 hash 查询参数精确匹配） */
function isNavActive(item, activePath, activeSearch) {
    if (item.matchQuery) {
        return activePath === item.path && activeSearch === item.matchQuery;
    }
    // 当前地址带 ? 且被某个带参菜单项认领时，普通同名项不高亮（如工单列表 vs 验收队列）
    const claimed = NAV_TREE
        .flatMap(({ items }) => items)
        .some((i) => i.matchQuery && i.path === activePath && i.matchQuery === activeSearch);
    if (claimed) {
        return false;
    }
    // 当前地址正好是某个菜单项的完整路径时，只让精确项高亮（避免 /equipment/location 同时点亮「设备档案」与「设备位置」）；
    // 前缀匹配仅在没有专属菜单项时生效（如 /equipment/5 高亮「设备档案」）。
    const exactExists = NAV_TREE
        .flatMap(({ items }) => items)
        .some((i) => !i.matchQuery && i.path === activePath);
    if (exactExists) {
        return activePath === item.path;
    }
    return activePath === item.path || activePath.startsWith(`${item.path}/`);
}

function renderNav(activePath) {
    const nav = qs('#nav');
    nav.innerHTML = '';
    const activeSearch = [...currentQuery().entries()]
        .map(([k, v]) => `${k}=${v}`).join('&');

    NAV_TREE.forEach(({ group }) => {
        const items = buildMenu().filter((item) => item.group === group);
        if (items.length === 0) {
            return;
        }
        nav.append(h('div', { class: 'nav-group-title', text: group }));
        items.forEach((item) => {
            const active = isNavActive(item, activePath, activeSearch);
            const search = item.query ? `?${new URLSearchParams(item.query)}` : '';
            nav.append(h('button', {
                class: `nav-item${active ? ' active' : ''}`,
                type: 'button',
                title: item.title,
                onclick: () => navigate(item.path + search),
            },
            h('span', { class: 'nav-dot' }),
            h('span', { class: 'nav-abbr', 'aria-hidden': 'true', text: item.title.slice(0, 1) }),
            h('span', { class: 'nav-label', text: item.title })));
        });
    });
}

/* ------------------------------------------------------------------ */
/* 顶栏                                                                */
/* ------------------------------------------------------------------ */

const ROLE_LABEL = {
    ADMIN: '系统管理员',
    EQUIP_ADMIN: '设备管理员',
    INSPECTOR: '巡检人员',
    REPAIRMAN: '维修人员',
    VERIFIER: '验收人员',
    MANAGER: '企业负责人',
};

function renderTopbar() {
    const user = state.user || {};
    const name = user.realName || user.username || '未登录';
    qs('#user-name').textContent = name;
    qs('#user-avatar').textContent = name.slice(0, 1);
    qs('#menu-name').textContent = name;
    const roles = (user.roles || []).map((role) => ROLE_LABEL[role] || role);
    const roleText = roles.join(' / ') || '—';
    qs('#user-role').textContent = roleText;
    qs('#menu-role').textContent = roleText;
}

/* ------------------------------------------------------------------ */
/* 顶栏：全局搜索 / 消息铃铛 / 头像菜单                                  */
/* ------------------------------------------------------------------ */

/** 全局搜索：设备 + 工单 + 备件三个真实接口，权限不足的域自动跳过 */
async function globalSearch(keyword) {
    const panel = qs('#search-panel');
    const text = keyword.trim();
    if (!text) {
        panel.hidden = true;
        panel.innerHTML = '';
        return;
    }

    const tasks = [];
    if (auth.can('asset:equipment:view')) {
        tasks.push(api.get('/api/asset/equipment', { pageNum: 1, pageSize: 5, keyword: text })
            .then((page) => ({ group: '设备', items: (page.list || page.records || []).map((row) => ({
                label: row.equipmentName || '未命名设备',
                sub: row.assetNo || '',
                path: `/equipment/${row.id}`,
            })) })));
    }
    if (auth.can('wo:workorder:view')) {
        tasks.push(api.get('/api/workorder', { pageNum: 1, pageSize: 5, keyword: text })
            .then((page) => ({ group: '维修工单', items: (page.records || []).map((row) => ({
                label: row.title || row.faultDesc || `工单 ${row.orderNo || row.id}`,
                sub: row.orderNo || '',
                path: `/workorder/${row.id}`,
            })) })));
    }
    if (auth.can('spare:part:view')) {
        tasks.push(api.get('/api/inventory/parts', { pageNum: 1, pageSize: 5, keyword: text })
            .then((page) => ({ group: '备件', items: (page.records || []).map((row) => ({
                label: row.partName || '未命名备件',
                sub: row.partNo || '',
                path: '/spare/part',
            })) })));
    }

    const results = await Promise.allSettled(tasks);
    panel.innerHTML = '';
    let count = 0;
    results.forEach((result) => {
        if (result.status !== 'fulfilled' || !result.value.items.length) {
            return;
        }
        panel.append(h('div', { class: 'search-group-title', text: result.value.group }));
        result.value.items.forEach((item) => {
            count += 1;
            panel.append(h('button', {
                class: 'search-item', type: 'button',
                onclick: () => {
                    panel.hidden = true;
                    qs('#global-search').value = '';
                    navigate(item.path);
                },
            },
            h('span', { text: item.label }),
            item.sub ? h('span', { class: 'mono', text: item.sub }) : null));
        });
    });
    if (!count) {
        panel.append(h('div', { class: 'search-empty', text: `未找到与「${esc(text)}」相关的内容` }));
    }
    panel.hidden = false;
}

let bellTimer = null;

/** 未读消息数轮询：30 秒一次，失败静默（接口对全部登录用户开放） */
async function refreshBell() {
    try {
        const count = await api.get('/api/system/messages/unread-count');
        const badge = qs('#bell-badge');
        const num = Number(count) || 0;
        badge.textContent = num > 99 ? '99+' : String(num);
        badge.hidden = num === 0;
    } catch (ignored) {
        // 消息接口不可达时不打扰用户
    }
}

function startBellPolling() {
    refreshBell();
    if (bellTimer) {
        clearInterval(bellTimer);
    }
    // SSE 实时通道在线时降频兜底（5 分钟对账一次）；断线仍 30 秒轮询
    const period = isRealtimeConnected() ? 300000 : 30000;
    bellTimer = setInterval(refreshBell, period);
}

/** 深浅主题切换：localStorage 记忆 + 跟随系统为默认值（第八期创新升级） */
function initThemeToggle() {
    const apply = (theme) => {
        document.documentElement.setAttribute('data-theme', theme);
        const btn = qs('#theme-btn');
        if (btn) {
            btn.textContent = theme === 'dark' ? '☀️' : '🌙';
        }
        localStorage.setItem('ec_theme', theme);
    };
    const saved = localStorage.getItem('ec_theme');
    if (saved === 'dark' || saved === 'light') {
        apply(saved);
    } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        apply('dark');
    }
    const btn = qs('#theme-btn');
    if (btn) {
        btn.addEventListener('click', () => {
            apply(document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark');
        });
    }
}

/**
 * SSE 事件统一入口：铃铛即时刷新 + 关键事件轻提示 + 通知业务页面自刷新。
 * 页面侧监听 window 'ec:sse-refresh'（携带事件类型与数据）自行决定是否重拉数据。
 */
function onRealtimeEvent(e) {
    const { type, data } = e.detail || {};
    if (type === 'realtime:up' || type === 'realtime:down') {
        startBellPolling(); // 连接态变化 → 调整轮询频率
        return;
    }
    if (type === 'message:new') {
        refreshBell();
        toast(data.title || '收到新消息', 'info');
    } else if (type === 'workorder:changed') {
        toast(`工单 ${data.orderNo || ''} 有状态更新`, 'info');
    } else if (type === 'sla:alert') {
        toast(`SLA 超时预警：${data.title || data.orderNo || ''}`, 'warn');
    } else if (type === 'stock:low') {
        toast(`库存不足：${data.partName || ''}`, 'warn');
    } else if (type === 'anomaly:found') {
        toast(`巡检发现异常：${data.itemName || ''}`, 'warn');
    }
    if (type !== 'message:new') {
        window.dispatchEvent(new CustomEvent('ec:sse-refresh', { detail: { type, data } }));
    }
}

function closeAvatarMenu() {
    qs('#avatar-menu').hidden = true;
}

function renderCrumbs(title) {
    qs('#crumbs').innerHTML = `<span>巡维云</span><span>/</span><strong>${esc(title)}</strong>`;
}

/* ------------------------------------------------------------------ */
/* 渲染                                                                */
/* ------------------------------------------------------------------ */

function renderForbidden(container, route) {
    container.innerHTML = '';
    const card = h('div', { class: 'card' },
        h('div', { class: 'card-body' },
            h('h2', { text: '没有访问权限', style: 'font-size:16px;margin-bottom:8px' }),
            h('p', {
                style: 'color:var(--text-secondary);font-size:13px;margin:0 0 14px',
                text: `当前账号缺少权限码 ${route.perm}，该页面已在前端被拦截；直接调用接口同样会被后端拒绝（HTTP 403）。`,
            }),
            h('button', {
                class: 'btn btn-primary', type: 'button', text: '返回可访问页面',
                onclick: () => navigate(defaultPath()),
            })),
    );
    container.append(card);
}

let renderToken = 0;
/** 当前页面的清理钩子队列（onCleanup 注册，路由切换时统一执行） */
let pageCleanups = [];

async function render() {
    if (!auth.requireLogin()) {
        return;
    }
    const path = currentPath();
    if (!path) {
        navigate(defaultPath(), { replace: true });
        return;
    }

    const matched = matchRoute(path);
    const view = qs('#view');
    const token = ++renderToken;

    if (!matched) {
        qs('#crumbs').innerHTML = '<span>巡维云</span><span>/</span><strong>页面不存在</strong>';
        view.innerHTML = '';
        view.append(h('div', { class: 'card' },
            h('div', { class: 'card-body' },
                h('h2', { text: '页面不存在', style: 'font-size:16px;margin-bottom:8px' }),
                h('p', { text: `路径 ${path} 没有对应的页面。`, style: 'color:var(--text-muted);font-size:13px;margin:0 0 14px' }),
                h('button', { class: 'btn btn-primary', type: 'button', text: '回到首页', onclick: () => navigate(defaultPath()) }))));
        return;
    }

    state.route = matched.route;
    renderNav(path);
    renderCrumbs(matched.route.title);
    document.title = `${matched.route.title} · 巡维云 EquipCare`;

    if (matched.route.perm && !auth.can(matched.route.perm)) {
        renderForbidden(view, matched.route);
        return;
    }

    view.innerHTML = '<div class="card"><div class="card-body"><div class="skeleton" style="width:40%;margin-bottom:10px"></div>'
        + '<div class="skeleton" style="width:70%;margin-bottom:10px"></div><div class="skeleton" style="width:55%"></div></div></div>';

    try {
        const module = await matched.route.module();
        if (token !== renderToken) {
            return;
        }
        // 第七阶段：页面级清理钩子（图表实例 / 定时器随路由切换释放）
        while (pageCleanups.length) {
            try {
                pageCleanups.pop()();
            } catch (cleanupError) {
                console.error('页面清理钩子执行失败', cleanupError);
            }
        }
        view.innerHTML = '';
        await module.render(view, {
            params: matched.params,
            query: currentQuery(),
            user: state.user,
            navigate,
            refreshUser,
            onCleanup: (fn) => pageCleanups.push(fn),
            setTitle: (title) => {
                renderCrumbs(title);
                document.title = `${title} · 巡维云 EquipCare`;
            },
        });
    } catch (error) {
        if (token !== renderToken) {
            return;
        }
        console.error(error);
        view.innerHTML = '';
        view.append(h('div', { class: 'card' },
            h('div', { class: 'card-body' },
                h('h2', { text: '页面加载失败', style: 'font-size:16px;margin-bottom:8px' }),
                h('p', { text: error.message || '未知错误', style: 'color:var(--danger);font-size:13px' }),
                h('button', { class: 'btn', type: 'button', text: '重试', onclick: () => render() }))));
    }
}

async function refreshUser() {
    try {
        const me = await api.get('/api/auth/me');
        state.user = me;
        auth.saveUser(me);
        renderTopbar();
    } catch (error) {
        // 忽略：令牌失效时 api 层已处理跳转
    }
}

/* ------------------------------------------------------------------ */
/* 启动                                                                */
/* ------------------------------------------------------------------ */

async function bootstrap() {
    if (!auth.requireLogin()) {
        return;
    }
    state.user = auth.user;
    renderTopbar();

    try {
        await refreshUser();
    } catch (error) {
        toastError(error);
    }

    // 侧栏折叠：记忆用户偏好（阶段十）
    const sidebar = qs('#sidebar');
    const applySidebar = (collapsed) => {
        sidebar.classList.toggle('collapsed', collapsed);
        qs('#sidebar-toggle').setAttribute('aria-expanded', String(!collapsed));
        qs('#sidebar-toggle').textContent = collapsed ? '»' : '«';
    };
    applySidebar(localStorage.getItem('ec_sidebar_collapsed') === '1');
    qs('#sidebar-toggle').addEventListener('click', () => {
        const collapsed = !sidebar.classList.contains('collapsed');
        localStorage.setItem('ec_sidebar_collapsed', collapsed ? '1' : '0');
        applySidebar(collapsed);
    });

    qs('#user-chip').addEventListener('click', () => {
        const menu = qs('#avatar-menu');
        menu.hidden = !menu.hidden;
    });
    // 点击其他区域收起头像菜单与搜索面板
    document.addEventListener('click', (event) => {
        if (!event.target.closest('.avatar-wrap')) {
            closeAvatarMenu();
        }
        if (!event.target.closest('.topbar-search')) {
            qs('#search-panel').hidden = true;
        }
    });
    qs('#menu-profile').addEventListener('click', () => {
        closeAvatarMenu();
        navigate('/profile');
    });
    qs('#menu-messages').addEventListener('click', () => {
        closeAvatarMenu();
        navigate('/messages');
    });
    const doLogout = async () => {
        try {
            // 回传 refreshToken：服务端吊销该用户全部刷新令牌（登出后旧令牌不可再续签）
            await api.post('/api/auth/logout', { refreshToken: auth.refreshToken || '' });
        } catch (ignored) {
            // 服务端不可用或令牌已过期，仍清理本地状态
        }
        auth.clear();
        redirectToLogin();
    };
    qs('#menu-logout').addEventListener('click', doLogout);

    // 全局搜索：输入防抖 + Enter 立即搜索 + Esc 收起
    const searchInput = qs('#global-search');
    let searchTimer = null;
    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => globalSearch(searchInput.value).catch(() => {}), 350);
    });
    searchInput.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            clearTimeout(searchTimer);
            globalSearch(searchInput.value).catch(() => {});
        } else if (event.key === 'Escape') {
            qs('#search-panel').hidden = true;
            searchInput.blur();
        }
    });
    qs('#bell-btn').addEventListener('click', () => navigate('/messages'));
    initThemeToggle();
    startBellPolling();
    startRealtime();
    window.addEventListener('ec:sse', onRealtimeEvent);

    window.addEventListener('hashchange', render);
    if (!currentPath()) {
        navigate(defaultPath(), { replace: true });
    } else {
        render();
    }
}

bootstrap();
