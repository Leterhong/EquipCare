/**
 * 维修工作台 / 待办总览。
 *
 * 一次请求拿到「我该处理什么」：待我接单、我维修中、待我验收、待派单、已驳回。
 * 每个区块给出可直接执行的动作，不用点进详情再找按钮。
 */

import { api } from '../api.js';
import {
    WORK_ORDER_STATUS, dueText, formatDate, h, toast, toastError,
} from '../ui.js';
import { priorityTag, reasonModal, woStatus } from './workorder-shared.js';

const STATUS_ORDER = ['PENDING_ASSIGN', 'PENDING_ACCEPT', 'IN_PROGRESS', 'PENDING_REVIEW', 'REJECTED', 'COMPLETED'];

export async function render(container, ctx) {
    let data;
    try {
        data = await api.get('/api/workorder/workbench', { limit: 10 });
    } catch (error) {
        container.append(h('div', { class: 'card' }, h('div', { class: 'card-body' }, h('p', { text: error.message }))));
        return;
    }

    async function act(promise, message) {
        try {
            await promise;
            toast(message, 'success');
            ctx.navigate('/workorder/workbench');
        } catch (error) {
            toastError(error);
        }
    }

    /* ---------------------------- 状态统计卡 ---------------------------- */

    const counts = new Map((data.counts || []).map((item) => [item.status, item.total]));
    const metrics = h('div', { class: 'metric-grid' });
    STATUS_ORDER.forEach((code) => {
        if (!WORK_ORDER_STATUS[code]) {
            return;
        }
        metrics.append(h('div', { class: 'metric-card', 'data-status': code, style: 'cursor:pointer' },
            h('div', { class: 'metric-label', text: WORK_ORDER_STATUS[code].label }),
            h('div', { class: 'metric-value', text: String(counts.get(code) || 0) })));
    });
    metrics.querySelectorAll('.metric-card').forEach((card) => {
        card.addEventListener('click', () => ctx.navigate('/workorder'));
    });

    /* ------------------------------ 卡片列表 ------------------------------ */

    function orderCard(order, actions) {
        return h('div', { class: 'stack-row' },
            h('div', { class: 'stack-head' },
                h('span', {}, h('a', {
                    class: 'link mono', href: `#/workorder/${order.id}`, text: order.orderNo,
                })),
                h('span', { html: woStatus(order.status) }),
                h('span', { html: priorityTag(order.priority) })),
            h('div', { text: order.title }),
            h('div', { class: 'cell-sub', text: `${order.equipmentName || ''} · ${order.assetNo || ''}`
                + ` · 时限 ${dueText(order.resolveDueTime)}` }),
            h('div', { class: 'cell-sub', text: `报修 ${formatDate(order.reportTime, true)} · 报修人 ${order.reporterName || '—'}` }),
            actions && actions.length ? h('div', { class: 'chip-row' }, ...actions) : null);
    }

    function section(title, list, emptyText, buildActions) {
        const body = h('div', { class: 'card-body' });
        if (!list || list.length === 0) {
            body.append(h('p', { class: 'hint', text: emptyText }));
        } else {
            const stack = h('div', { class: 'stack' });
            list.forEach((order) => stack.append(orderCard(order, buildActions ? buildActions(order) : [])));
            body.append(stack);
        }
        return h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', { text: title })), body);
    }

    const acceptActions = (order) => [
        h('button', {
            class: 'btn btn-sm btn-primary', type: 'button', text: '接单',
            onclick: () => act(api.post(`/api/workorder/${order.id}/accept`, {}), '已接单'),
        }),
        h('button', {
            class: 'btn btn-sm', type: 'button', text: '拒接',
            onclick: () => reasonModal({
                title: '拒接工单',
                label: '拒接原因',
                minLength: 5,
                onConfirm: (reason) => act(api.post(`/api/workorder/${order.id}/reject`, { reason }), '已拒接'),
            }),
        }),
        h('button', {
            class: 'btn btn-sm', type: 'button', text: '详情',
            onclick: () => ctx.navigate(`/workorder/${order.id}`),
        }),
    ];

    const repairActions = (order) => [
        h('button', {
            class: 'btn btn-sm btn-primary', type: 'button',
            text: '继续维修',
            onclick: () => ctx.navigate(`/workorder/${order.id}/repair`),
        }),
        h('button', {
            class: 'btn btn-sm', type: 'button', text: '详情',
            onclick: () => ctx.navigate(`/workorder/${order.id}`),
        }),
    ];

    const reviewActions = (order) => [
        h('button', {
            class: 'btn btn-sm btn-primary', type: 'button', text: '去验收',
            onclick: () => ctx.navigate(`/workorder/${order.id}/verify`),
        }),
        h('button', {
            class: 'btn btn-sm', type: 'button', text: '详情',
            onclick: () => ctx.navigate(`/workorder/${order.id}`),
        }),
    ];

    const dispatchActions = (order) => [
        h('button', {
            class: 'btn btn-sm', type: 'button', text: '去派单台',
            onclick: () => ctx.navigate('/workorder/dispatch'),
        }),
        h('button', {
            class: 'btn btn-sm', type: 'button', text: '详情',
            onclick: () => ctx.navigate(`/workorder/${order.id}`),
        }),
    ];

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '维修工作台' }),
            h('p', { text: '待我接单 / 我维修中 / 待我验收，一页看完；每个区块都能直接操作。' })),
        h('div', { class: 'page-actions' },
            h('button', { class: 'btn', type: 'button', text: '工单列表', onclick: () => ctx.navigate('/workorder') })));

    container.append(head, metrics);
    container.append(section('待我接单', data.pendingAccept, '没有待接单的工单', acceptActions));
    container.append(section('我维修中', data.repairing, '当前没有在修工单', repairActions));
    container.append(section('待我验收', data.pendingReview, '没有待验收工单', reviewActions));
    container.append(section('待派单', data.pendingAssign, '派单池已清空', dispatchActions));
    container.append(section('已驳回（待重新派单）', data.rejected, '没有被拒接的工单', dispatchActions));
}
