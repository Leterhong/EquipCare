/**
 * 公共测试工具：fetch mock 与登录态注入。
 */

export function jsonResponse(body, { status = 200, contentType = 'application/json' } = {}) {
    return {
        ok: status >= 200 && status < 300,
        status,
        headers: { get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null) },
        json: async () => body,
        blob: async () => new Blob([JSON.stringify(body)]),
    };
}

export function ok(data) {
    return jsonResponse({ code: 200, message: 'OK', data });
}

export function fail(code, message, httpStatus = 200) {
    return jsonResponse({ code, message, data: null }, { status: httpStatus });
}

/** 按路由分发的 fetch mock：routes 为 [method, url前缀, 处理函数或响应] 数组 */
export function routeFetch(routes) {
    const calls = [];
    const impl = async (url, init = {}) => {
        const method = (init.method || 'GET').toUpperCase();
        calls.push({ url: String(url), method, init });
        for (const [m, prefix, handler] of routes) {
            if (m.toUpperCase() === method && String(url).startsWith(prefix)) {
                return typeof handler === 'function'
                    ? handler(url, init, calls)
                    : handler;
            }
        }
        return fail(404, `mock 未匹配：${method} ${url}`, 404);
    };
    impl.calls = calls;
    return impl;
}

/** 注入登录态 */
export function loginAs(permissions = [], roles = ['OPERATOR']) {
    localStorage.setItem('ec_access_token', 'test-access-token');
    localStorage.setItem('ec_refresh_token', 'test-refresh-token');
    localStorage.setItem('ec_user', JSON.stringify({
        userId: 1,
        username: 'tester',
        realName: '测试用户',
        permissions,
        roles,
    }));
}

export function logoutAll() {
    localStorage.clear();
}

/** 捕获页面 toast 文本 */
export function toastText() {
    const nodes = document.querySelectorAll('.toast');
    return nodes.length ? nodes[nodes.length - 1].textContent : '';
}

export async function flush(times = 4) {
    for (let i = 0; i < times; i += 1) {
        // eslint-disable-next-line no-await-in-loop
        await Promise.resolve();
    }
}
