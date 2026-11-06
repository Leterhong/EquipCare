import { defineStore } from 'pinia';

/**
 * 登录态存储：与后端 JWT 双令牌协议对齐（access 2h / refresh 7d）。
 * localStorage 键名与旧原生前端保持一致（ec_*），同一浏览器可共享登录态。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('ec_access_token') || '',
    refreshToken: localStorage.getItem('ec_refresh_token') || '',
    user: JSON.parse(localStorage.getItem('ec_user') || 'null') as UserInfo | null,
  }),
  getters: {
    loggedIn: (s) => !!s.accessToken,
    realName: (s) => s.user?.realName || s.user?.username || '—',
    permissions: (s) => s.user?.permissions || [],
  },
  actions: {
    save(login: { accessToken: string; refreshToken: string }) {
      this.accessToken = login.accessToken;
      this.refreshToken = login.refreshToken;
      localStorage.setItem('ec_access_token', login.accessToken);
      localStorage.setItem('ec_refresh_token', login.refreshToken);
    },
    saveUser(user: UserInfo) {
      this.user = user;
      localStorage.setItem('ec_user', JSON.stringify(user));
    },
    clear() {
      this.accessToken = '';
      this.refreshToken = '';
      this.user = null;
      localStorage.removeItem('ec_access_token');
      localStorage.removeItem('ec_refresh_token');
      localStorage.removeItem('ec_user');
    },
    can(perm: string): boolean {
      return this.permissions.includes(perm);
    },
  },
});

export interface UserInfo {
  userId: number;
  username: string;
  realName: string;
  deptName?: string;
  roles: string[];
  permissions: string[];
}

export interface MenuNode {
  id: number;
  parentId: number;
  code: string;
  name: string;
  path?: string;
  icon?: string;
  sort?: number;
  children?: MenuNode[];
}
