/**
 * 统一接口客户端（api.js）行为测试：接口异常处理。
 * 覆盖：参数序列化 / 成功解包 / 业务错误 / 401 静默续期（单飞）/ 续期失败回登录 / 网络异常 / 非 JSON 响应。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { api, ApiError, auth } from '@static/api.js';
import { ok, fail, routeFetch, loginAs, logoutAll } from './helpers.js';

beforeEach(() => {
    logoutAll();
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('请求参数与成功解包', () => {
    it('GET 查询参数正确拼接，空值参数被过滤', async () => {
        const fetchMock = vi.fn(async () => ok({ id: 1 }));
        vi.stubGlobal('fetch', fetchMock);
        loginAs(['any']);
        await api.get('/api/asset/equipment', { pageNum: 2, pageSize: 10, keyword: '', status: null });
        const url = fetchMock.mock.calls[0][0];
        expect(url).toContain('pageNum=2');
        expect(url).toContain('pageSize=10');
        expect(url).not.toContain('keyword');
        expect(url).not.toContain('status');
    });

    it('成功响应解包 Result.data，直接返回业务数据', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => ok({ total: 3 })));
        const data = await api.get('/api/analytics/dashboard/summary');
        expect(data).toEqual({ total: 3 });
    });

    it('自动携带 Bearer 访问令牌', async () => {
        const fetchMock = vi.fn(async () => ok(null));
        vi.stubGlobal('fetch', fetchMock);
        loginAs([]);
        await api.get('/api/asset/equipment');
        const init = fetchMock.mock.calls[0][1];
        expect(init.headers.Authorization).toBe('Bearer test-access-token');
    });

    it('POST 请求体序列化为 JSON', async () => {
        const fetchMock = vi.fn(async () => ok({ id: 40 }));
        vi.stubGlobal('fetch', fetchMock);
        loginAs([]);
        await api.post('/api/workorder', { equipmentId: 1, level: 'P3' });
        const init = fetchMock.mock.calls[0][1];
        expect(init.method).toBe('POST');
        expect(init.headers['Content-Type']).toBe('application/json');
        expect(JSON.parse(init.body)).toEqual({ equipmentId: 1, level: 'P3' });
    });
});

describe('业务与协议错误', () => {
    it('业务 code != 200 抛出 ApiError 且携带 code 与 message', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => fail(422, '工单等级不能为空')));
        const err = await api.post('/api/workorder', {}).catch((e) => e);
        expect(err).toBeInstanceOf(ApiError);
        expect(err.code).toBe(422);
        expect(err.message).toBe('工单等级不能为空');
    });

    it('网络异常抛出「网络异常」ApiError（code=-1）', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
        const err = await api.get('/api/asset/equipment').catch((e) => e);
        expect(err).toBeInstanceOf(ApiError);
        expect(err.code).toBe(-1);
        expect(err.message).toContain('网络异常');
    });

    it('非 JSON 的 HTTP 错误响应抛出带状态码的 ApiError', async () => {
        const fetchMock = vi.fn(async () => ({
            ok: false,
            status: 502,
            headers: { get: () => 'text/html' },
            json: async () => { throw new Error('not json'); },
        }));
        vi.stubGlobal('fetch', fetchMock);
        const err = await api.get('/api/asset/equipment').catch((e) => e);
        expect(err).toBeInstanceOf(ApiError);
        expect(err.httpStatus).toBe(502);
        expect(err.message).toContain('502');
    });
});

describe('登录过期处理（401 静默续期）', () => {
    it('401 时用 refreshToken 续期并重放原请求，刷新请求全局单飞', async () => {
        const fetchMock = vi.fn(async (url, init = {}) => {
            const method = (init.method || 'GET').toUpperCase();
            if (method === 'POST' && String(url).startsWith('/api/auth/refresh')) {
                return ok({ accessToken: 'new-access', refreshToken: 'new-refresh' });
            }
            if (method === 'GET' && String(url).startsWith('/api/workorder/40')) {
                const bearer = init.headers?.Authorization || '';
                if (bearer === 'Bearer test-access-token') {
                    return fail(0, '登录已过期', 401); // 旧令牌一律 401（并发的两笔都命中）
                }
                if (bearer === 'Bearer new-access') {
                    return ok({ id: 40, status: 'COMPLETED' }); // 续期后重放成功
                }
                return fail(401, '未携带令牌', 401);
            }
            return fail(404, 'not found', 404);
        });
        vi.stubGlobal('fetch', fetchMock);
        loginAs(['wo:workorder:view']);

        // 并发两笔请求同时 401：续期只能发起一次
        const [r1, r2] = await Promise.all([
            api.get('/api/workorder/40'),
            api.get('/api/workorder/40'),
        ]);
        expect(r1).toEqual({ id: 40, status: 'COMPLETED' });
        expect(r2).toEqual(r1);
        const refreshCalls = fetchMock.mock.calls
            .filter(([u, i]) => String(u).startsWith('/api/auth/refresh'));
        expect(refreshCalls.length).toBe(1);
        expect(auth.accessToken).toBe('new-access');
    });

    it('无 refreshToken 时 401 直接清除登录态并跳转登录页', async () => {
        localStorage.setItem('ec_access_token', 'expired'); // 不写 refresh
        const fakeLocation = { pathname: '/app.html', search: '', hash: '', replace: vi.fn() };
        vi.stubGlobal('location', fakeLocation);
        vi.stubGlobal('fetch', vi.fn(async () => fail(0, '登录已过期', 401)));
        const err = await api.get('/api/workorder').catch((e) => e);
        expect(err).toBeInstanceOf(ApiError);
        expect(err.message).toContain('登录已过期');
        expect(auth.accessToken).toBeNull();
        expect(fakeLocation.replace).toHaveBeenCalled();
        vi.unstubAllGlobals();
    });

    it('续期失败（refresh 返回非 200）清除登录态并回登录页', async () => {
        const fakeLocation = { pathname: '/app.html', search: '', hash: '', replace: vi.fn() };
        vi.stubGlobal('location', fakeLocation);
        const fetchMock = vi.fn(async (url, init = {}) => {
            const method = (init.method || 'GET').toUpperCase();
            if (method === 'POST') return fail(1004, '刷新令牌无效');
            return fail(0, '登录已过期', 401);
        });
        vi.stubGlobal('fetch', fetchMock);
        loginAs(['wo:workorder:view']);
        const err = await api.get('/api/workorder').catch((e) => e);
        expect(err).toBeInstanceOf(ApiError);
        expect(err.message).toContain('登录已过期');
        expect(auth.accessToken).toBeNull();
        expect(fakeLocation.replace).toHaveBeenCalled();
        vi.unstubAllGlobals();
    });
});

describe('文件上传', () => {
    it('upload() 组装 FormData 并 POST 到 /api/files/upload', async () => {
        const fetchMock = vi.fn(async () => ok({ fileId: 77, url: '/api/files/77' }));
        vi.stubGlobal('fetch', fetchMock);
        loginAs(['wo:workorder:repair']);
        const file = new File(['png-bytes'], 'after.png', { type: 'image/png' });
        const vo = await api.upload(file, { bizType: 'WORK_ORDER' });
        expect(vo.fileId).toBe(77);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/files/upload');
        expect(init.body).toBeInstanceOf(FormData);
        expect(init.body.get('file')).toBeTruthy();
        expect(init.body.get('bizType')).toBe('WORK_ORDER');
    });
});
