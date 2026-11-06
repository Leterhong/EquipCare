/**
 * 派单工作台。
 *
 * 两列待办：待派单（新单）与已驳回（被拒接、需要重新派）。默认按优先级 + 故障等级排序，
 * 让派单人先看到最该先派的单，而不是按创建时间堆在最后。
 */

import { aiEnabled } from './ai-copilot.js';
import { api } from '../api.js';
import {
    WORK_ORDER_LEVEL, dueText, esc, formatDate, h, renderTable, toast, toastError,
} from '../ui.js';
import { dispatchModal, loadAssignees, priorityTag, woStatus } from './workorder-shared.js';

export async function render(container, ctx) {
    const pendingHost = h('div', {});
    const rejectedHost = h('div', {});
    const pendingCount = h('span', { class: 'hint', text: '' });
    const rejectedCount = h('span', { class: 'hint', text: '' });

    let assignees = [];

    async function load() {
        pendingHost.innerHTML = '<div class="table-empty">加载中…</div>';
        rejectedHost.innerHTML = '<div class="table-empty">加载中…</div>';
        try {
            assignees = await loadAssignees();
            const [pending, rejected] = await Promise.all([
                api.get('/api/workorder', { pageNum: 1, pageSize: 20, status: 'PENDING_ASSIGN', priorityFirst: true }),
                api.get('/api/workorder', { pageNum: 1, pageSize: 20, status: 'REJECTED', priorityFirst: true }),
            ]);
            pendingCount.textContent = `${pending.total} 张待派单`;
            rejectedCount.textContent = `${rejected.total} 张待重新派单`;
            paint(pendingHost, pending, '暂无待派单工单', '派单');
            paint(rejectedHost, rejected, '没有被拒接的工单', '重新派单');
        } catch (error) {
            toastError(error);
        }
    }

    function paint(host, data, emptyText, buttonText) {
        host.innerHTML = renderTable({
            columns: [
                { title: '工单号', render: (row) => `<span class="mono">${esc(row.orderNo)}</span>` },
                {
                    title: '故障',
                    render: (row) => `<div>${esc(row.title)}</div>`
                        + `<div class="cell-sub">${esc(row.equipmentName || '')} · ${esc(row.assetNo || '')}</div>`,
                },
                { title: '等级', render: (row) => `<span class="tag tag-outline">${esc(WORK_ORDER_LEVEL[row.level]?.label || row.level)}</span>` },
                { title: '优先级', render: (row) => priorityTag(row.priority) },
                { title: '状态', render: (row) => woStatus(row.status) },
                { title: '报修人', render: (row) => esc(row.reporterName || '—') },
                { title: '时限', render: (row) => `<span class="cell-sub">${esc(dueText(row.resolveDueTime))}</span>` },
                { title: '报修时间', render: (row) => `<span class="cell-sub">${esc(formatDate(row.reportTime, true))}</span>` },
                {
                    title: '操作',
                    render: (row) => `<button class="btn btn-sm btn-primary" data-dispatch="${row.id}" type="button">${buttonText}</button>`
                        + ` <button class="btn btn-sm" data-open="${row.id}" type="button">详情</button>`,
                },
            ],
            rows: data.records || [],
            empty: emptyText,
        });

        host.querySelectorAll('button[data-dispatch]').forEach((button) => {
            button.addEventListener('click', () => {
                const id = Number(button.dataset.dispatch);
                const order = (data.records || []).find((item) => item.id === id);
                if (!order) {
                    return;
                }
                dispatchModal({
                    aiSuggest: await aiEnabled(),
                    order,
                    assignees,
                    title: buttonText,
                    onConfirm: async (payload) => {
                        await api.post(`/api/workorder/${id}/dispatch`, payload);
                        toast('已派单', 'success');
                        await load();
                    },
                });
            });
        });
        host.querySelectorAll('button[data-open]').forEach((button) => {
            button.addEventListener('click', () => ctx.navigate(`/workorder/${button.dataset.open}`));
        });
    }

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '派单工作台' }),
            h('p', { text: '按优先级与故障等级排序；派单时可指定维修人、优先级与完成时限。' })),
        h('div', { class: 'page-actions' },
            h('button', { class: 'btn', type: 'button', text: '工单列表', onclick: () => ctx.navigate('/workorder') })));

    const pendingCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '待派单' }), pendingCount),
        h('div', { class: 'card-body' }, pendingHost));

    const rejectedCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '已驳回（待重新派单）' }), rejectedCount),
        h('div', { class: 'card-body' }, rejectedHost));

    container.append(head, pendingCard, rejectedCard);
    await load();
}
