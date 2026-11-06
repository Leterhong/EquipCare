/**
 * 工单操作时间线。
 *
 * 数据来自后端把六类原始记录（状态流转 / 派单 / 维修 / 验收 / 备件 / 评价）归并后的结果，
 * 前端只负责呈现：每条都带操作人、时间与源记录，便于追责与复核。
 */

import { api } from '../api.js';
import { formatDate, esc, h } from '../ui.js';
import { attachmentThumbs, woStatus } from './workorder-shared.js';

const EVENT_META = {
    CREATE: { label: '建单', cls: 'ev-create' },
    STATUS: { label: '状态流转', cls: 'ev-status' },
    REPAIR: { label: '维修记录', cls: 'ev-repair' },
    DISPATCH: { label: '派单', cls: 'ev-dispatch' },
    VERIFY: { label: '验收', cls: 'ev-verify' },
    PART: { label: '备件', cls: 'ev-part' },
    EVALUATION: { label: '评价', cls: 'ev-eval' },
};

export async function render(container, ctx) {
    const id = Number(ctx.params.id);
    let order;
    let items;
    try {
        [order, items] = await Promise.all([
            api.get(`/api/workorder/${id}`),
            api.get(`/api/workorder/${id}/timeline`),
        ]);
    } catch (error) {
        container.append(h('div', { class: 'card' },
            h('div', { class: 'card-body' }, h('p', { text: error.message }))));
        return;
    }

    ctx.setTitle(`时间线 · ${order.orderNo}`);

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '工单操作时间线' }),
            h('p', {}, h('span', { class: 'mono', text: order.orderNo }), ' · ',
                h('span', { text: order.title }), ' ',
                h('span', { html: woStatus(order.status) }))),
        h('div', { class: 'page-actions' },
            h('button', { class: 'btn', type: 'button', text: '工单详情', onclick: () => ctx.navigate(`/workorder/${id}`) })));

    const timeline = h('div', { class: 'timeline' });
    if (!items || items.length === 0) {
        timeline.append(h('p', { class: 'hint', text: '暂无记录' }));
    } else {
        items.forEach((item) => {
            const meta = EVENT_META[item.eventType] || { label: item.eventType, cls: '' };
            const statusLine = item.fromStatus || item.toStatus
                ? h('div', { class: 'cell-sub' }, `${item.fromStatusLabel || '—'} → ${item.toStatusLabel || '—'}`
                    + (item.costMinutes != null ? `（上一状态停留 ${item.costMinutes} 分钟）` : ''))
                : null;
            timeline.append(h('div', { class: `timeline-item ${meta.cls}` },
                h('div', { class: 'timeline-dot' }),
                h('div', { class: 'timeline-body' },
                    h('div', { class: 'stack-head' },
                        h('span', { class: 'tag tag-outline', text: meta.label }),
                        h('strong', { text: item.title || '' }),
                        h('span', { class: 'cell-sub', text: formatDate(item.eventTime, true) }),
                        h('span', { class: 'cell-sub', text: item.operatorName || '—' }),
                        item.operatorRole ? h('span', { class: 'cell-sub', text: item.operatorRole }) : null),
                    statusLine,
                    item.detail ? h('div', { class: 'fault-text', text: item.detail }) : null,
                    (item.attachments || []).length ? attachmentThumbs(item.attachments) : null,
                    h('div', { class: 'cell-sub mono', text: `${item.sourceType || ''}#${item.sourceId || ''}` }))));
        });
    }

    container.append(head, h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: `共 ${items.length} 条记录` }),
            h('span', { class: 'hint', text: '按发生时间正序；每条都可回查源记录' })),
        h('div', { class: 'card-body' }, timeline)));

    void esc;
}
