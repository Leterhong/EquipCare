/**
 * SSE 实时通道客户端（单例，原生 JS 实现）。
 *
 * 设计要点（第八期创新升级）：
 *  1. 两段式鉴权：POST /api/sse/ticket（带 Bearer）换 30 秒一次性票据 →
 *     GET /api/sse/stream?ticket=…（EventSource 无法带请求头的标准解法）；
 *  2. 断线指数退避 + 随机抖动重连，onerror 主动 close 后由本模块重连，
 *     避免浏览器默认重连与自定义重连叠加成重连风暴；
 *  3. 服务端每条事件带 id（outbox 主键），客户端按 lastEventId 去重；
 *  4. 对外只发 CustomEvent（window），业务页面零侵入订阅：
 *     window.addEventListener('ec:sse', e => e.detail = {type, data})；
 *  5. 失败回退：连接不可用时不打扰用户，app.js 继续用 30 秒轮询兜底。
 */

import { api, auth } from './api.js';

const MAX_RETRY = 6;
const RETRY_BASE_MS = 2000;

let source = null;
let retryCount = 0;
let lastEventId = '';
let started = false;

/** 是否处于连接态（app.js 据此决定是否暂停轮询兜底） */
export const isRealtimeConnected = () => source !== null && source.readyState === 1;

function emit(type, data) {
    window.dispatchEvent(new CustomEvent('ec:sse', { detail: { type, data } }));
}

function closeQuietly() {
    if (source) {
        try {
            source.close();
        } catch (ignored) {
            // 已关闭
        }
        source = null;
    }
}

async function connect() {
    if (!auth.accessToken) {
        return; // 未登录不建连，登录后由 app.js 再次调用 start()
    }
    try {
        const { ticket } = await api.post('/api/sse/ticket');
        closeQuietly();
        source = new EventSource(`/api/sse/stream?ticket=${encodeURIComponent(ticket)}`);

        source.onopen = () => {
            retryCount = 0;
        };
        source.addEventListener('connected', () => {
            retryCount = 0;
            emit('connected', {});
        });

        // 统一处理业务事件：message:new / workorder:changed / sla:alert / stock:low / anomaly:found
        for (const type of ['message:new', 'workorder:changed', 'sla:alert', 'stock:low', 'anomaly:found']) {
            source.addEventListener(type, (e) => {
                // lastEventId 去重（服务端 id = outbox 主键）
                if (e.lastEventId && e.lastEventId === lastEventId) {
                    return;
                }
                lastEventId = e.lastEventId || lastEventId;
                let data = {};
                try {
                    data = JSON.parse(e.data);
                } catch (ignored) {
                    // data 兼容纯文本
                }
                emit(type, data);
            });
        }

        source.onerror = () => {
            // 主动 close，按退避策略重连（指数 + 抖动，封顶 30s）
            closeQuietly();
            if (!started || retryCount >= MAX_RETRY) {
                emit('realtime:down', {});
                return; // 放弃实时，轮询兜底接管
            }
            retryCount += 1;
            const delay = Math.min(30000, RETRY_BASE_MS * 2 ** retryCount) + Math.random() * 1000;
            setTimeout(() => {
                if (started) {
                    connect().catch(() => {});
                }
            }, delay);
        };
        emit('realtime:up', {});
    } catch (e) {
        // 票据获取失败（未登录 / 服务端关闭）：静默降级为轮询
        emit('realtime:down', {});
    }
}

/** 启动实时通道（登录后 / 路由就绪后调用；幂等） */
export function startRealtime() {
    if (started) {
        return;
    }
    started = true;
    connect().catch(() => {});
}

/** 停止通道（登出 / 页面卸载时调用） */
export function stopRealtime() {
    started = false;
    closeQuietly();
}

/** 登出时清空去重位点 */
export function resetRealtime() {
    lastEventId = '';
    retryCount = 0;
}
