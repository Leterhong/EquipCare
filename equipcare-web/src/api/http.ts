import axios, { type AxiosRequestConfig } from 'axios';
import { useAuthStore } from '../stores/auth';
import { router } from '../router';

/**
 * 统一请求客户端：自动带 Bearer；非 200 业务码抛错；
 * 401 时用 refreshToken 静默续期一次，失败才回登录页（与旧前端行为对齐）。
 */
export const http = axios.create({ baseURL: '/', timeout: 15000 });

/** DELETE 便捷方法（模块级导出，类型安全） */
export const httpDel = (path: string) => http.delete(path);

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  success: boolean;
}

export class ApiError extends Error {
  code: number;
  constructor(code: number, message: string) {
    super(message || '请求失败');
    this.code = code;
  }
}

let refreshing: Promise<void> | null = null;

http.interceptors.request.use((config) => {
  const auth = useAuthStore();
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`;
  }
  return config;
});

http.interceptors.response.use(
  async (resp) => {
    const body = resp.data as ApiResult<unknown>;
    // 登录接口本身也是 200 包装，这里统一解包
    if (body && typeof body.code === 'number' && body.code !== 200) {
      if (body.code === 1002) {
        await refreshOnce();
        return http.request(resp.config);
      }
      return Promise.reject(new ApiError(body.code, body.message));
    }
    return body.data === undefined ? (body as never) : (body.data as never);
  },
  async (error) => {
    const status = error.response?.status;
    const cfg = error.config as AxiosRequestConfig & { skipAuthRetry?: boolean };
    if (status === 401 && !cfg?.skipAuthRetry) {
      try {
        await refreshOnce();
        return http.request({ ...error.config, skipAuthRetry: true });
      } catch {
        forceLogin();
      }
    }
    const message = error.response?.data?.message || error.message || '网络异常';
    return Promise.reject(new ApiError(status || 0, message));
  },
);

async function refreshOnce(): Promise<void> {
  const auth = useAuthStore();
  if (!auth.refreshToken) {
    forceLogin();
    throw new ApiError(1002, '登录已过期');
  }
  refreshing ||= http
    .post<never, { accessToken: string; refreshToken: string }>('/api/auth/refresh', {
      refreshToken: auth.refreshToken,
    }, { skipAuthRetry: true } as AxiosRequestConfig & { skipAuthRetry?: boolean })
    .then((tokens) => {
      // 拦截器已解包业务 data，tokens 即 { accessToken, refreshToken }
      auth.save(tokens);
    })
    .finally(() => {
      refreshing = null;
    });
  await refreshing;
}

function forceLogin() {
  const auth = useAuthStore();
  auth.clear();
  router.push('/login');
}
