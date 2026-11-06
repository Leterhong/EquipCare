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
        { path: 'workorder', component: () => import('../views/WorkorderListView.vue'), meta: { title: '维修工单', perm: 'wo:workorder:view' } },
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
  if (to.meta.title) {
    document.title = `${String(to.meta.title)} · 巡维云 EquipCare`;
  }
});
