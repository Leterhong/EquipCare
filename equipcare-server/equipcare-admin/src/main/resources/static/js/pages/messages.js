/**
 * 通知中心（第七阶段）。
 *
 * 覆盖七类通知：巡检任务提醒 / 巡检异常通知（msgType=TASK）、
 * 新工单 / 工单派发 / 工单超时 / 维修验收（msgType=WORK_ORDER）、
 * SLA 超时（msgType=SLA）、库存不足（msgType=SYSTEM）。
 * 支持已读/未读筛选、单条已读、勾选批量已读、全部已读；
 * 归属校验在后端（仅能标记本人消息）。
 */

import { api } from '../api.js';
import { esc, h, renderPagination, toast, toastError } from '../ui.js';

const MSG_TYPE_LABEL = {
    TASK: '巡检',
    WORK_ORDER: '工单',
    SLA: 'SLA',
    SYSTEM: '系统',
};

export async function render(container, ctx) {
    const state = {
        pageNum: 1,
        pageSize: 20,
        total: 0,
        records: [],
        selected: new Set(),
    };

    const summary = h('span', { class: 'hint' });
    const typeFilter = h('select', { class: 'input', 'aria-label': '通知类型' },
        h('option', { value: '', text: '全部类型' }),
        h('option', { value: 'TASK', text: '巡检（提醒/异常）' }),
        h('option', { value: 'WORK_ORDER', text: '工单（新单/派发/超时/验收）' }),
        h('option', { value: 'SLA', text: 'SLA 超时' }),
        h('option', { value: 'SYSTEM', text: '系统（库存不足等）' }));
    const readFilter = h('select', { class: 'input', 'aria-label': '阅读状态' },
        h('option', { value: '', text: '全部状态' }),
        h('option', { value: '0', text: '未读' }),
        h('option', { value: '1', text: '已读' }));
    const markBatchBtn = h('button', {
        class: 'btn', type: 'button', text: '批量标记已读', disabled: true,
    });
    const markAllBtn = h('button', { class: 'btn', type: 'button', text: '全部标记已读' });
    const listHost = h('div', { class: 'msg-list' });
    const pagerHost = h('div', {});

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '通知中心' }),
                h('p', { text: '巡检提醒 / 巡检异常 / 新工单 / 工单派发 / 工单超时 / 维修验收 / 库存不足 / SLA 超时，统一在此接收与处理。' })),
            h('div', { class: 'page-actions' }, summary)),
        h('div', { class: 'card' },
            h('div', { class: 'card-body toolbar' },
                typeFilter, readFilter, markBatchBtn, markAllBtn),
            listHost,
            h('div', { class: 'card-body' }, pagerHost)));

    typeFilter.addEventListener('change', () => {
        state.pageNum = 1;
        load();
    });
    readFilter.addEventListener('change', () => {
        state.pageNum = 1;
        load();
    });
    markAllBtn.addEventListener('click', async () => {
        try {
            await api.patch('/api/system/messages/read-all');
            toast('已全部标记为已读');
            state.selected.clear();
            await load();
        } catch (error) {
            toastError(error);
        }
    });
    markBatchBtn.addEventListener('click', async () => {
        if (!state.selected.size) return;
        try {
            await api.patch('/api/system/messages/read-batch', [...state.selected]);
            toast(`已标记 ${state.selected.size} 条为已读`);
            state.selected.clear();
            await load();
        } catch (error) {
            toastError(error);
        }
    });

    await load();

    async function load() {
        listHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:70%"></div></div>';
        const query = new URLSearchParams({
            pageNum: String(state.pageNum),
            pageSize: String(state.pageSize),
        });
        if (typeFilter.value) query.set('msgType', typeFilter.value);
        if (readFilter.value !== '') query.set('readFlag', readFilter.value);
        try {
            const page = await api.get(`/api/system/messages?${query.toString()}`);
            state.records = page.records || [];
            state.total = page.total || 0;
            renderList();
        } catch (error) {
            listHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function renderList() {
        const unread = state.records.filter((r) => !r.readFlag).length;
        summary.textContent = `共 ${state.total} 条，本页未读 ${unread}`;
        markBatchBtn.disabled = state.selected.size === 0;
        markBatchBtn.textContent = state.selected.size
            ? `批量标记已读（${state.selected.size}）` : '批量标记已读';

        listHost.innerHTML = '';
        if (!state.records.length) {
            listHost.append(h('div', { class: 'table-empty', text: '暂无通知' }));
        } else {
            state.records.forEach((msg) => listHost.append(renderItem(msg)));
        }

        pagerHost.innerHTML = '';
        pagerHost.append(renderPagination(
            { pageNum: state.pageNum, pageSize: state.pageSize, total: state.total },
            { onChange: (page) => { state.pageNum = page; load(); } }));
    }

    function renderItem(msg) {
        const unread = !msg.readFlag;
        const checked = state.selected.has(msg.id);
        const checkbox = h('input', { type: 'checkbox', 'aria-label': '选择该通知' });
        checkbox.checked = checked;
        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                state.selected.add(msg.id);
            } else {
                state.selected.delete(msg.id);
            }
            renderList();
        });

        const row = h('div', { class: `msg-item${unread ? ' msg-unread' : ''}` },
            h('label', { class: 'msg-check' }, checkbox),
            h('div', { class: 'msg-main' },
                h('div', { class: 'msg-title' },
                    unread ? h('span', { class: 'tag tag-red', text: '未读' }) : null,
                    h('strong', { text: msg.title || '（无标题）' }),
                    h('span', { class: 'tag tag-gray', text: MSG_TYPE_LABEL[msg.msgType] || msg.msgType || '通知' })),
                msg.content ? h('div', { class: 'msg-content', text: msg.content }) : null,
                h('div', { class: 'msg-time hint', text: msg.createTime || '' })),
            unread
                ? h('button', {
                    class: 'btn btn-sm', type: 'button', text: '标记已读',
                    onclick: async () => {
                        try {
                            await api.patch(`/api/system/messages/${msg.id}/read`);
                            await load();
                        } catch (error) {
                            toastError(error);
                        }
                    },
                })
                : null);
        return row;
    }
}
