/**
 * 认证状态 store 单元测试：令牌存取、权限判定、清理。
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useAuthStore } from '../src/stores/auth';

const LOGIN = {
  accessToken: 'at-1',
  refreshToken: 'rt-1',
};
const USER = {
  userId: 1,
  username: 'admin',
  realName: '管理员',
  roles: ['ADMIN'],
  permissions: ['dashboard:view', 'asset:equipment:view'],
};

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('save 持久化双令牌到 localStorage', () => {
    const auth = useAuthStore();
    auth.save(LOGIN);
    expect(localStorage.getItem('ec_access_token')).toBe('at-1');
    expect(localStorage.getItem('ec_refresh_token')).toBe('rt-1');
    expect(auth.loggedIn).toBe(true);
  });

  it('saveUser 持久化用户并驱动权限判定', () => {
    const auth = useAuthStore();
    auth.saveUser(USER);
    expect(auth.realName).toBe('管理员');
    expect(auth.can('dashboard:view')).toBe(true);
    expect(auth.can('wo:workorder:create')).toBe(false);
  });

  it('clear 清空内存与 localStorage，登录态复位', () => {
    const auth = useAuthStore();
    auth.save(LOGIN);
    auth.saveUser(USER);
    auth.clear();
    expect(auth.accessToken).toBe('');
    expect(auth.loggedIn).toBe(false);
    expect(localStorage.getItem('ec_access_token')).toBeNull();
    expect(localStorage.getItem('ec_user')).toBeNull();
  });

  it('从未登录时从 localStorage 恢复（页面刷新场景）', () => {
    localStorage.setItem('ec_access_token', 'persisted');
    localStorage.setItem('ec_user', JSON.stringify(USER));
    const auth = useAuthStore();
    expect(auth.loggedIn).toBe(true);
    expect(auth.can('dashboard:view')).toBe(true);
  });
});
