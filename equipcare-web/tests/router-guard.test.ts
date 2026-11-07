/**
 * 路由守卫单元测试：未登录重定向 / 权限不足回驾驶舱 / 已登录正常放行。
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { router } from '../src/router';
import { useAuthStore } from '../src/stores/auth';
import { http } from '../src/api/http';

beforeEach(() => {
  // 离线模式：布局壳 onMounted 的菜单加载/SSE 未读请求立即失败（各处均 catch 静默），
  // 避免真实 jsdom XHR 挂起拖垮 5s 测试超时
  http.defaults.adapter = async () => {
    throw new Error('offline-in-test');
  };
});

describe('router guard', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('未登录访问受保护页 → 重定向登录页并携带 redirect', async () => {
    await router.push('/workorder');
    expect(router.currentRoute.value.path).toBe('/login');
    expect(router.currentRoute.value.query.redirect).toBe('/workorder');
  });

  it('未登录访问登录页 → 正常放行', async () => {
    await router.push('/login');
    expect(router.currentRoute.value.path).toBe('/login');
  });

  it('已登录但无权限 → 回到驾驶舱', async () => {
    const auth = useAuthStore();
    auth.save({ accessToken: 't', refreshToken: 'r' });
    auth.saveUser({
      userId: 2, username: 'op', realName: '操作工', roles: ['REPAIRMAN'],
      permissions: ['dashboard:view'],
    });
    await router.push('/workorder'); // 缺 wo:workorder:view
    expect(router.currentRoute.value.path).toBe('/dashboard');
  });

  it('已登录且有权限 → 正常进入', async () => {
    const auth = useAuthStore();
    auth.save({ accessToken: 't', refreshToken: 'r' });
    auth.saveUser({
      userId: 1, username: 'admin', realName: '管理员', roles: ['ADMIN'],
      permissions: ['wo:workorder:view'],
    });
    await router.push('/workorder');
    expect(router.currentRoute.value.path).toBe('/workorder');
  });
});
