/**
 * AI 副驾接入（Vue 组合式函数）。
 *
 * 能力开关：/api/ai/status（会话级缓存），未启用时全部入口隐藏；
 * 故障诊断：票据 → EventSource 流式（delta/ai.error/done 三事件）；
 * 派单推荐 / 维修报告：一次性 JSON。
 * 后端 equipcare.ai.enabled=false 时所有接口 404 —— 统一按"未启用"降级。
 */
import { ref } from 'vue';
import { http } from '../api/http';

export interface AiStatus {
  enabled: boolean;
  model: string;
}

export interface DispatchSuggestion {
  userId: number;
  realName?: string;
  reason: string;
  categoryRepaired?: number;
  totalRepaired?: number;
  activeLoad?: number;
}

const enabled = ref<boolean | null>(null); // null = 探测中

export function useAi() {
  return { enabled, status, checkEnabled, streamDiagnosis, suggestDispatch, generateReport };
}

/** AI 是否启用（会话级缓存；404/异常一律视为未启用） */
async function checkEnabled(): Promise<boolean> {
  if (enabled.value !== null) return enabled.value;
  try {
    const s = await http.get<never, AiStatus>('/api/ai/status');
    enabled.value = !!s.enabled;
  } catch {
    enabled.value = false;
  }
  return enabled.value;
}

async function newTicket(): Promise<string> {
  const r = await http.post<never, { ticket: string }>('/api/sse/ticket');
  return r.ticket;
}

export interface DiagnosisStreamHandlers {
  onDelta: (text: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
  onFail: (e: Error) => void;
}

/**
 * 流式故障诊断。返回 close()——组件卸载/用户取消时调用，
 * 后端收到连接断开会取消上游请求（停烧 token）。
 */
export async function streamDiagnosis(
  workOrderId: number,
  handlers: DiagnosisStreamHandlers,
): Promise<() => void> {
  let ticket: string;
  try {
    ticket = await newTicket();
  } catch (e) {
    handlers.onFail(new Error('无法建立 AI 连接（' + (e as Error).message + '）'));
    return () => {};
  }
  const source = new EventSource(`/api/sse/stream?ticket=${encodeURIComponent(ticket)}`);
  const deliver = (fn: (v: string) => void, arg: string) => {
    try {
      fn(arg);
    } catch {
      /* 渲染回调异常不中断流 */
    }
  };
  source.addEventListener('delta', (e) => deliver(handlers.onDelta, (e as MessageEvent).data));
  source.addEventListener('done', () => {
    source.close();
    deliver(handlers.onDone, '');
  });
  source.addEventListener('ai.error', (e) => {
    let message = (e as MessageEvent).data;
    try {
      message = JSON.parse((e as MessageEvent).data).message || message;
    } catch {
      /* 纯文本 */
    }
    source.close();
    deliver(handlers.onError, message);
  });
  source.onerror = () => {
    source.close();
    deliver(handlers.onError, '连接中断，请重试');
  };
  return () => source.close();
}

/** AI 智能派单推荐 TOP3（模型失败后端自动降级纯算法） */
export async function suggestDispatch(workOrderId: number): Promise<DispatchSuggestion[]> {
  return http.post<never, DispatchSuggestion[]>(`/api/ai/dispatch-suggest/${workOrderId}`);
}

/** 生成维修报告（Markdown） */
export async function generateReport(workOrderId: number): Promise<string> {
  const r = await http.post<never, { markdown: string }>(`/api/ai/report/${workOrderId}`);
  return r.markdown;
}
