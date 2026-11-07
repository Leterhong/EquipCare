/**
 * HTTP 客户端拦截器测试（核心安全链路）：
 *  - 业务码 200 解包 data；
 *  - 401 → 用 refreshToken 静默续期一次 → 自动重试原请求；
 *  - 续期失败 → 清登录态并回登录页；
 *  - 业务错误码 → 抛 ApiError。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { AxiosRequestConfig } from 'axios';
import { http, ApiError } from '../src/api/http';
import { useAuthStore } from '../src/stores/auth';

type Responder = (config: AxiosRequestConfig) => { status: number; body: unknown };

let responder: Responder;

// 用自定义 adapter 替换网络层，按脚本逐次应答
http.defaults.adapter = async (config) => {
  const { status, body } = responder(config);
  const response: Record<string, unknown> = {
    data: body,
    status,
    statusText: String(status),
    headers: {},
    config,
    request: {},
  };
  if (status >= 400) {
    // axios 手工 adapter 需要自己抛 4xx/5xx 以触发错误拦截器
    const error = new Error('Request failed with status code ' + status) as Error & {
      response: Record<string, unknown>;
      isAxiosError: boolean;
      config: AxiosRequestConfig;
    };
    error.response = response;
    error.isAxiosError = true;
    error.config = config;
    throw error;
  }
  return response as never;
};

function ok<T>(data: T) {
  return { status: 200, body: { code: 200, message: 'success', data, success: true } };
}

describe('http interceptor', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    const auth = useAuthStore();
    auth.save({ accessToken: 'old-token', refreshToken: 'refresh-token' });
    auth.saveUser({
      userId: 1, username: 'admin', realName: '管理员', roles: ['ADMIN'], permissions: [],
    });
  });

  it('业务码 200 → 直接解包 data', async () => {
    responder = () => ok({ hello: 'world' });
    const data = await http.get<never, { hello: string }>('/api/demo');
    expect(data.hello).toBe('world');
  });

  it('401 → 静默续期一次并重试原请求', async () => {
    let calls = 0;
    responder = (config) => {
      calls += 1;
      const url = String(config.url);
      if (url.includes('/api/auth/refresh')) {
        // refresh 也经同一实例（skipAuthRetry 防递归），adapter 统一应答
        return ok({ accessToken: 'new-token', refreshToken: 'new-refresh' });
      }
      if (calls === 1) {
        return { status: 401, body: { code: 1002, message: '令牌过期' } };
      }
      return ok({ after: 'refresh' });
    };
    const data = await http.get<never, { after: string }>('/api/workorder');
    expect(data.after).toBe('refresh');
    const auth = useAuthStore();
    expect(auth.accessToken).toBe('new-token');
    expect(calls).toBe(3); // 原请求 401 → refresh → 重试成功
  });

  it('续期失败 → 清空登录态并抛错（回登录页由守卫完成）', async () => {
    responder = (config) => {
      const url = String(config.url);
      if (url.includes('/api/auth/refresh')) {
        return { status: 401, body: { code: 1002, message: '刷新失败' } };
      }
      return { status: 401, body: { code: 1002, message: '令牌过期' } };
    };
    await expect(http.get('/api/workorder')).rejects.toThrow();
    const auth = useAuthStore();
    expect(auth.loggedIn).toBe(false);
    expect(localStorage.getItem('ec_refresh_token')).toBeNull();
  });

  it('业务错误码 → 抛 ApiError 且保留 code', async () => {
    responder = () => ({ status: 200, body: { code: 1001, message: '用户名或密码错误', data: null, success: false } });
    const err = await http.get('/api/demo').catch((e: ApiError) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).code).toBe(1001);
    expect((err as ApiError).message).toBe('用户名或密码错误');
  });
});
