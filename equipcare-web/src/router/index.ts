import { createRouter, createWebHashHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

/**
 * 新版前端路由（hash 模式：与部署方式解耦，Nginx 无需回退配置）。
 * 首批迁移：登录 / 布局壳 / 数据驾驶舱 / 工单列表；
 * 其余页面继续使用旧原生前端（顶部提供跳转入口），分批迁移。
 */
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '数据驾驶舱', perm: 'dashboard:view' } },
        { path: 'equipment', component: () => import('../views/EquipmentListView.vue'), meta: { title: '设备台账', perm: 'asset:equipment:view' } },
        { path: 'equipment/:id', component: () => import('../views/EquipmentDetailView.vue'), meta: { title: '设备详情', perm: 'asset:equipment:view' } },
        { path: 'workorder', component: () => import('../views/WorkorderListView.vue'), meta: { title: '维修工单', perm: 'wo:workorder:view' } },
        { path: 'workbench', component: () => import('../views/WorkbenchView.vue'), meta: { title: '维修工作台', perm: 'wo:workorder:workbench' } },
        { path: 'inspect-task', component: () => import('../views/InspectTaskListView.vue'), meta: { title: '巡检任务', perm: 'inspect:task:view' } },
        { path: 'spare-part', component: () => import('../views/SparePartListView.vue'), meta: { title: '备件台账', perm: 'spare:part:view' } },
        { path: 'workorder/:id/repair', component: () => import('../views/WorkorderRepairView.vue'), meta: { title: '维修过程记录', perm: 'wo:workorder:repair' } },
        { path: 'inspect-task/:id/execute', component: () => import('../views/InspectExecuteView.vue'), meta: { title: '执行巡检', perm: 'inspect:task:execute' } },
        { path: 'inspect-template', component: () => import('../views/InspectTemplateListView.vue'), meta: { title: '巡检模板', perm: 'inspect:template:view' } },
        { path: 'inspect-template-new', component: () => import('../views/InspectTemplateEditView.vue'), meta: { title: '新建巡检模板', perm: 'inspect:template:manage' } },
        { path: 'inspect-template-edit/:id', component: () => import('../views/InspectTemplateEditView.vue'), meta: { title: '编辑巡检模板', perm: 'inspect:template:manage' } },
        { path: 'workorder/:id/verify', component: () => import('../views/WorkorderVerifyView.vue'), meta: { title: '维修验收', perm: 'wo:workorder:verify' } },
        { path: 'workorder/:id/evaluate', component: () => import('../views/WorkorderEvaluateView.vue'), meta: { title: '工单评价', perm: 'wo:workorder:evaluate' } },
        { path: 'inspect-anomaly', component: () => import('../views/InspectAnomalyListView.vue'), meta: { title: '巡检异常', perm: 'inspect:anomaly:view' } },
        { path: 'inspect-plan', component: () => import('../views/InspectPlanListView.vue'), meta: { title: '巡检计划', perm: 'inspect:plan:view' } },
        { path: 'health', component: () => import('../views/HealthListView.vue'), meta: { title: '设备健康', perm: 'health:view' } },
        { path: 'users', component: () => import('../views/UserListView.vue'), meta: { title: '用户管理', perm: 'system:user:view' } },
        { path: 'roles', component: () => import('../views/RoleListView.vue'), meta: { title: '角色管理', perm: 'system:role:view' } },
        { path: 'audit-logs', component: () => import('../views/AuditLogView.vue'), meta: { title: '审计日志', perm: 'system:audit:view' } },
        { path: 'workorder-new', component: () => import('../views/WorkorderFormView.vue'), meta: { title: '报修', perm: 'wo:workorder:create' } },
        { path: 'tenants', component: () => import('../views/TenantListView.vue'), meta: { title: '租户管理', roles: ['ADMIN'] } },
        { path: 'messages', component: () => import('../views/MessageCenterView.vue'), meta: { title: '消息中心' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (!to.meta.public && !auth.loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.perm && auth.loggedIn && !auth.can(to.meta.perm as string)) {
    return { path: '/dashboard' };
  }
  if (to.meta.roles && auth.loggedIn) {
    const ok = (to.meta.roles as string[]).some((r) => auth.user?.roles?.includes(r));
    if (!ok) return { path: '/dashboard' };
  }
  if (to.meta.title) {
    document.title = `${String(to.meta.title)} · 巡维云 EquipCare`;
  }
});
