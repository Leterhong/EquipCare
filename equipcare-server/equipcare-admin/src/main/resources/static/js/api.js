/**
 * 统一接口客户端。
 *
 * 设计要点：
 *  1. 所有请求自动携带 Bearer 令牌；
 *  2. 统一解包后端 { code, message, data }，非 200 抛 ApiError；
 *  3. 访问令牌过期时用 refreshToken 静默续期一次，失败才回登录页；
 *  4. 图片等二进制资源也走带令牌的 fetch，避免把令牌拼到 URL 上泄漏。
 */

const STORAGE = {
    access: 'ec_access_token',
    refresh: 'ec_refresh_token',
    user: 'ec_user',
};

export class ApiError extends Error {
    constructor(code, message, httpStatus) {
        super(message || '请求失败');
        this.code = code;
        this.httpStatus = httpStatus;
    }
}

export const auth = {
    get accessToken() {
        return localStorage.getItem(STORAGE.access);
    },
    get refreshToken() {
        return localStorage.getItem(STORAGE.refresh);
    },
    get user() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE.user) || 'null');
        } catch (e) {
            return null;
        }
    },
    save(loginVO) {
        localStorage.setItem(STORAGE.access, loginVO.accessToken);
        localStorage.setItem(STORAGE.refresh, loginVO.refreshToken);
    },
    saveUser(user) {
        localStorage.setItem(STORAGE.user, JSON.stringify(user));
    },
    clear() {
        localStorage.removeItem(STORAGE.access);
        localStorage.removeItem(STORAGE.refresh);
        localStorage.removeItem(STORAGE.user);
    },
    /** 是否有某个权限码；后端仍会独立校验，这里只控制界面元素 */
    can(code) {
        const user = auth.user;
        return !!(user && user.permissions && user.permissions.includes(code));
    },
    hasRole(role) {
        const user = auth.user;
        return !!(user && user.roles && user.roles.includes(role));
    },
    requireLogin() {
        if (!auth.accessToken) {
            redirectToLogin();
            return false;
        }
        return true;
    },
};

export function redirectToLogin() {
    const back = encodeURIComponent(location.pathname + location.search + location.hash);
    location.replace(`/index.html?redirect=${back}`);
}

function buildQuery(params) {
    if (!params) {
        return '';
    }
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null || value === '') {
            return;
        }
        search.append(key, value);
    });
    const qs = search.toString();
    return qs ? `?${qs}` : '';
}

let refreshing = null;

async function refreshToken() {
    const token = auth.refreshToken;
    if (!token) {
        return false;
    }
    if (!refreshing) {
        refreshing = fetch('/api/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: token }),
        })
            .then((res) => res.json())
            .then((body) => {
                if (body.code === 200 && body.data) {
                    auth.save(body.data);
                    return true;
                }
                return false;
            })
            .catch(() => false)
            .finally(() => {
                refreshing = null;
            });
    }
    return refreshing;
}

async function rawRequest(path, { method = 'GET', body, params, headers = {}, retry = true } = {}) {
    const url = path.startsWith('http') ? path : path + buildQuery(params);
    const init = { method, headers: { ...headers } };

    if (body instanceof FormData) {
        init.body = body;
    } else if (body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(body);
    }
    if (auth.accessToken) {
        init.headers.Authorization = `Bearer ${auth.accessToken}`;
    }

    let response;
    try {
        response = await fetch(url, init);
    } catch (networkError) {
        throw new ApiError(-1, '网络异常，请检查服务是否已启动');
    }

    if (response.status === 401 && retry) {
        if (auth.refreshToken) {
            const ok = await refreshToken();
            if (ok) {
                return rawRequest(path, { method, body, params, headers, retry: false });
            }
        }
        // 没有刷新令牌或续期失败：一律清凭据回登录页，避免用户拿着过期令牌反复失败
        auth.clear();
        redirectToLogin();
        throw new ApiError(401, '登录已过期，请重新登录', 401);
    }

    const contentType = response.headers.get('Content-Type') || '';
    if (!contentType.includes('application/json')) {
        if (!response.ok) {
            throw new ApiError(response.status, `请求失败（HTTP ${response.status}）`, response.status);
        }
        return response;
    }

    const payload = await response.json().catch(() => null);
    if (!payload) {
        throw new ApiError(response.status, '响应解析失败', response.status);
    }
    if (payload.code !== 200) {
        if (payload.code === 1004 || payload.code === 1005) {
            auth.clear();
            redirectToLogin();
        }
        throw new ApiError(payload.code, payload.message, response.status);
    }
    return payload.data;
}

export const api = {
    get: (path, params) => rawRequest(path, { params }),
    post: (path, body) => rawRequest(path, { method: 'POST', body }),
    put: (path, body) => rawRequest(path, { method: 'PUT', body }),
    patch: (path, body) => rawRequest(path, { method: 'PATCH', body }),
    del: (path) => rawRequest(path, { method: 'DELETE' }),
    upload(file, extra = {}) {
        const form = new FormData();
        form.append('file', file);
        Object.entries(extra).forEach(([k, v]) => {
            if (v !== undefined && v !== null && v !== '') {
                form.append(k, v);
            }
        });
        return rawRequest('/api/files/upload', { method: 'POST', body: form });
    },
    /** 取二进制资源并转成可直接用于 img.src 的 objectURL */
    async objectUrl(path, params) {
        const res = await rawRequest(path, { params });
        const blob = await res.blob();
        return URL.createObjectURL(blob);
    },
};
