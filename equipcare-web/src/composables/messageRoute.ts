/**
 * 消息业务跳转映射（bizType + bizId → 站内路由）。
 * 与后端 MessageService.send 的 bizType 约定对齐：
 *   WORK_ORDER / TASK / SLA / SYSTEM / STOCK_ALERT
 * SLA 与工单类消息落工单详情；库存类落备件台账；其余回消息中心。
 */
export function messageRoute(bizType?: string, bizId?: number): string | null {
  if (!bizId || bizId <= 0) return null;
  switch (bizType) {
    case 'WORK_ORDER':
    case 'SLA_ALERT':
      return `/workorder/${bizId}`;
    case 'TASK':
      return `/inspect-task/${bizId}`;
    case 'STOCK_ALERT':
      return '/spare-part';
    default:
      return null;
  }
}

/** 消息类型 → 展示标签（与后端 msgType 约定对齐） */
export function msgTypeLabel(msgType?: string): string {
  switch (msgType) {
    case 'WORK_ORDER': return '工单';
    case 'TASK': return '巡检';
    case 'SLA': return 'SLA';
    case 'STOCK': return '库存';
    case 'SYSTEM': return '系统';
    default: return msgType || '—';
  }
}
