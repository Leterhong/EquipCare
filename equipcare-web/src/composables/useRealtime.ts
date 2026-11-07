/**
 * SSE 实时通道（Vue 组合式函数，单例）。
 *
 * 与旧前端 realtime.js 同一套服务端协议：
 *   POST /api/sse/ticket（JWT 换 30s 一次性票据）→ GET /api/sse/stream?ticket=
 * 断线指数退避重连（1s·2ⁿ + 抖动，封顶 30s，6 次后放弃回退轮询语义），
 * 服务端事件带 id（outbox 主键）→ lastEventId 去重。
 *
 * Vue 侧用法：
 *   const { unread, connected } = storeToRefs(useRealtimeStore()) …
 * 本模块保持无框架依赖的连接管理 + 一个极薄的响应式状态层。
 */
import { reactive } from 'vue';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';

export interface RealtimeEvent {
  type: string;
  data: Record<string, unknown>;
}

const state = reactive({
  connected: false,
  unread: 0,
});

let source: EventSource | null = null;
let retryCount = 0;
let lastEventId = '';
let started = false;

const MAX_RETRY = 6;
const RETRY_BASE_MS = 2000;

type Handler = (data: Record<string, unknown>) => void;
const handlers = new Map<string, Set<Handler>>();

export function useRealtime() {
  return {
    state,
    start: startRealtime,
    stop: stopRealtime,
    on,
    refreshUnread,
  };
}

/** 认证状态（连接前校验） */
function authStore() {
  return useAuthStore();
}

/** 订阅事件类型（message:new / workorder:changed / sla:alert / stock:low / anomaly:found） */
function on(type: string, handler: Handler) {
  if (!handlers.has(type)) {
    handlers.set(type, new Set());
  }
  handlers.get(type)!.add(handler);
  return () => handlers.get(type)?.delete(handler);
}

function emit(type: string, data: Record<string, unknown>) {
  handlers.get(type)?.forEach((h) => {
    try {
      h(data);
    } catch (ignored) {
      // 单个订阅者异常不阻断其他订阅者
    }
  });
}

async function refreshUnread() {
  try {
    state.unread = await http.get<never, number>('/api/system/messages/unread-count');
  } catch {
    /* 未登录等场景静默 */
  }
}

function closeQuietly() {
  if (source) {
    try {
      source.close();
    } catch {
      /* 已关闭 */
    }
    source = null;
  }
}

async function connect() {
  if (!authStore().accessToken) return;
  try {
    const { ticket } = await http.post<never, { ticket: string }>('/api/sse/ticket');
    closeQuietly();
    source = new EventSource(`/api/sse/stream?ticket=${encodeURIComponent(ticket)}`);

    source.onopen = () => {
      retryCount = 0;
      state.connected = true;
    };
    source.addEventListener('connected', () => {
      retryCount = 0;
      state.connected = true;
    });

    for (const type of [
      'message:new',
      'workorder:changed',
      'sla:alert',
      'stock:low',
      'anomaly:found',
    ]) {
      source.addEventListener(type, (e: MessageEvent) => {
        if (e.lastEventId && e.lastEventId === lastEventId) return; // 去重
        lastEventId = e.lastEventId || lastEventId;
        let data: Record<string, unknown> = {};
        try {
          data = JSON.parse(e.data);
        } catch {
          /* 纯文本兼容 */
        }
        emit(type, data);
        if (type === 'message:new') {
          state.unread += 1;
          const evt = new CustomEvent('ec:vue-sse', { detail: { type } });
          window.dispatchEvent(evt);
        }
      });
    }

    source.onerror = () => {
      closeQuietly();
      state.connected = false;
      if (!started || retryCount >= MAX_RETRY) return; // 回退轮询语义由页面自行处理
      retryCount += 1;
      const delay = Math.min(30000, RETRY_BASE_MS * 2 ** retryCount) + Math.random() * 1000;
      setTimeout(() => {
        if (started) void connect();
      }, delay);
    };
  } catch {
    state.connected = false;
  }
}

/** 启动实时通道（登录后调用；幂等） */
export function startRealtime() {
  if (started) return;
  started = true;
  void refreshUnread();
  void connect();
}

/** 停止（登出/页面卸载） */
export function stopRealtime() {
  started = false;
  state.connected = false;
  closeQuietly();
}

/** 登出时清空去重位点 */
export function resetRealtime() {
  lastEventId = '';
  retryCount = 0;
}
