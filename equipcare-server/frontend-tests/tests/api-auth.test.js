/**
 * 登录状态与权限控制单元测试（被测对象：static/js/api.js 的 auth）。
 * 对应用户要求：登录状态、权限控制。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { auth, redirectToLogin } from '@static/api.js';
import { loginAs, logoutAll } from './helpers.js';

beforeEach(() => {
    logoutAll();
});

describe('登录状态（auth）', () => {
    it('未登录时 accessToken 为 null，requireLogin 判定为未登录', () => {
        expect(auth.accessToken).toBeNull();
        expect(auth.refreshToken).toBeNull();
    });

    it('save() 保存访问/刷新令牌，getter 可读取', () => {
        auth.save({ accessToken: 'a-1', refreshToken: 'r-1' });
        expect(auth.accessToken).toBe('a-1');
        expect(auth.refreshToken).toBe('r-1');
    });

    it('saveUser() 保存用户信息，user getter 解析 JSON', () => {
        auth.saveUser({ userId: 9, username: 'u9', permissions: ['wo:workorder:view'] });
        expect(auth.user.username).toBe('u9');
        expect(auth.user.permissions).toContain('wo:workorder:view');
    });

    it('user 信息损坏（非法 JSON）时返回 null 而不是抛错', () => {
        localStorage.setItem('ec_user', '{broken json');
        expect(auth.user).toBeNull();
        expect(auth.can('anything')).toBe(false);
    });

    it('clear() 清空全部登录凭据', () => {
        loginAs(['wo:workorder:view']);
        auth.clear();
        expect(auth.accessToken).toBeNull();
        expect(auth.refreshToken).toBeNull();
        expect(auth.user).toBeNull();
    });
});

describe('权限控制（auth.can / hasRole）', () => {
    it('拥有权限码时 can() 返回 true', () => {
        loginAs(['wo:workorder:view', 'spare:stock:in']);
        expect(auth.can('wo:workorder:view')).toBe(true);
        expect(auth.can('spare:stock:in')).toBe(true);
    });

    it('没有权限码时 can() 返回 false（界面收敛，后端仍独立校验）', () => {
        loginAs(['wo:workorder:view']);
        expect(auth.can('system:user:manage')).toBe(false);
    });

    it('未登录时 can() 一律 false', () => {
        expect(auth.can('wo:workorder:view')).toBe(false);
    });

    it('hasRole() 按角色判定', () => {
        loginAs([], ['ADMIN']);
        expect(auth.hasRole('ADMIN')).toBe(true);
        expect(auth.hasRole('INSPECTOR')).toBe(false);
    });

    it('redirectToLogin() 携带当前地址作为回跳参数', () => {
        const fakeLocation = { pathname: '/app.html', search: '?a=1', hash: '#/workorder/9', replace: vi.fn() };
        vi.stubGlobal('location', fakeLocation);
        redirectToLogin();
        expect(fakeLocation.replace).toHaveBeenCalledTimes(1);
        const target = fakeLocation.replace.mock.calls[0][0];
        expect(target.startsWith('/index.html?redirect=')).toBe(true);
        const back = decodeURIComponent(target.split('redirect=')[1]);
        expect(back).toBe('/app.html?a=1#/workorder/9');
        vi.unstubAllGlobals();
    });
});
