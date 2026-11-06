/**
 * 工单列表页。
 *
 * 默认按 id 倒序（最新在前）；点任意一行进详情。筛选条件全部落到后端 SQL，
 * 不在前端做二次过滤，避免「列表显示 10 条但实际有 200 条」的错觉。
 */

import { api, auth } from '../api.js';
import {
    WORK_ORDER_LEVEL, WORK_ORDER_PRIORITY, WORK_ORDER_STATUS, WO_SOURCE, dueText, esc, formatDate, h,
    renderPagination, renderTable, statusTag, toastError,
} from '../ui.js';
import { priorityTag, woStatus } from './workorder-shared.js';

/** 看板列：仅展示用，不提供跨状态拖拽，状态只能由动作推进 */
const BOARD_COLUMNS = ['PENDING_ASSIGN', 'PENDING_ACCEPT', 'IN_PROGRESS', 'PENDING_REVIEW', 'COMPLETED'];
const BOARD_PAGE_SIZE = 20;

export async function render(container, ctx) {
    const state = {
        pageNum: 1,
        pageSize: 10,
        keyword: '',
        status: ctx.query?.get('status') || '',
        priority: '',
        level: '',
        onlyMine: false,
    };
    // 支持带参直达：/workorder?view=board
    let activeView = ctx.query?.get('view') === 'board' ? 'board' : 'list';

    const mayCreate = auth.can('wo:workorder:create');

    const keywordInput = h('input', { class: 'input', type: 'search', placeholder: '工单号 / 标题 / 资产编号 / 设备名' });
    const statusSelect = h('select', { class: 'input' },
        h('option', { value: '', text: '全部状态' }),
        ...Object.entries(WORK_ORDER_STATUS)
            .filter(([code]) => !['PENDING_DISPATCH', 'PENDING_VERIFY', 'SUSPENDED'].includes(code))
            .map(([code, item]) => h('option', { value: code, text: item.label })));
    statusSelect.value = state.status;
    const prioritySelect = h('select', { class: 'input' },
        h('option', { value: '', text: '全部优先级' }),
        ...Object.entries(WORK_ORDER_PRIORITY).map(([code, item]) => h('option', { value: code, text: item.label })));
    const levelSelect = h('select', { class: 'input' },
        h('option', { value: '', text: '全部等级' }),
        ...Object.entries(WORK_ORDER_LEVEL).map(([code, item]) => h('option', { value: code, text: item.label })));
    const mineCheck = h('input', { type: 'checkbox' });

    const summary = h('span', { class: 'hint', text: '' });
    const tableHost = h('div', {});
    const pagerHost = h('div', {});

    async function load() {
        tableHost.innerHTML = '<div class="table-empty">加载中…</div>';
        const params = { pageNum: state.pageNum, pageSize: state.pageSize };
        if (state.keyword) {
            params.keyword = state.keyword;
        }
        if (state.status) {
            params.status = state.status;
        }
        if (state.priority) {
            params.priority = state.priority;
        }
        if (state.level) {
            params.level = state.level;
        }
        if (state.onlyMine) {
            params.onlyMine = true;
        }
        try {
            const data = await api.get('/api/workorder', params);
            const rows = data.records || [];
            summary.textContent = `共 ${data.total} 张工单`;
            tableHost.innerHTML = renderTable({
                columns: [
                    { title: '工单号', key: 'orderNo', render: (row) => `<span class="mono">${esc(row.orderNo)}</span>` },
                    {
                        title: '故障',
                        render: (row) => `<div>${esc(row.title)}</div>`
                            + `<div class="cell-sub">${esc(row.equipmentName || '')} · ${esc(row.assetNo || '')}</div>`,
                    },
                    {
                        title: '来源',
                        render: (row) => statusTag(WO_SOURCE, row.source),
                    },
                    { title: '等级', render: (row) => statusTag(WORK_ORDER_LEVEL, row.level) },
                    { title: '优先级', render: (row) => priorityTag(row.priority) },
                    { title: '状态', render: (row) => woStatus(row.status) },
                    {
                        title: '维修人',
                        render: (row) => esc(row.assigneeName || '未派单'),
                    },
                    {
                        title: '修复时限',
                        render: (row) => `<span class="cell-sub">${esc(dueText(row.resolveDueTime))}</span>`,
                    },
                    {
                        title: '报修时间',
                        render: (row) => `<span class="cell-sub">${esc(formatDate(row.reportTime, true))}</span>`,
                    },
                ],
                rows,
                empty: '没有匹配的工单',
                rowAttrs: (row) => `data-id="${row.id}" style="cursor:pointer"`,
            });
            tableHost.querySelectorAll('tr[data-id]').forEach((tr) => {
                tr.addEventListener('click', () => ctx.navigate(`/workorder/${tr.dataset.id}`));
            });
            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(
                { total: data.total, pageNum: data.pageNum, pageSize: data.pageSize },
                {
                    onChange: (page) => {
                        state.pageNum = page;
                        load();
                    },
                },
            ));
        } catch (error) {
            toastError(error);
            tableHost.innerHTML = '<div class="table-empty">加载失败</div>';
        }
    }

    const search = () => {
        state.keyword = keywordInput.value.trim();
        state.status = statusSelect.value;
        state.priority = prioritySelect.value;
        state.level = levelSelect.value;
        state.onlyMine = mineCheck.checked;
        state.pageNum = 1;
        load();
    };

    const filters = h('div', { class: 'card' },
        h('div', { class: 'card-body' },
            h('div', { class: 'toolbar' },
                h('div', { class: 'field grow' }, h('label', { text: '关键字' }), keywordInput),
                h('div', { class: 'field' }, h('label', { text: '状态' }), statusSelect),
                h('div', { class: 'field' }, h('label', { text: '优先级' }), prioritySelect),
                h('div', { class: 'field' }, h('label', { text: '等级' }), levelSelect),
                h('div', { class: 'field' },
                    h('label', { text: '范围' }),
                    h('label', { class: 'check-line compact' }, mineCheck, h('span', { text: '只看与我相关' }))),
                h('div', { class: 'field' },
                    h('label', { text: ' ' }),
                    h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: search })))));

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '维修工单' }),
            h('p', { text: '报修、派单、维修、验收、评价全流程留痕；状态只能由动作推进。' })),
        h('div', { class: 'page-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '维修工作台',
                onclick: () => ctx.navigate('/workorder/workbench'),
            }),
            mayCreate ? h('button', {
                class: 'btn btn-primary', type: 'button', text: '＋ 报修',
                onclick: () => ctx.navigate('/workorder/new'),
            }) : null));

    /* ---------------------- 视图切换（列表 / 看板） ---------------------- */

    const viewTabs = h('div', { class: 'card section-tabs', role: 'tablist' },
        h('button', { type: 'button', role: 'tab', text: '列表视图', dataset: { view: 'list' } }),
        h('button', { type: 'button', role: 'tab', text: '看板视图', dataset: { view: 'board' } }));

    const listPane = h('div', {});
    const boardPane = h('div', { style: 'display:none' });
    const boardHost = h('div', { class: 'wo-board' });

    function switchView(view) {
        activeView = view;
        viewTabs.querySelectorAll('button').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.view === view);
        });
        listPane.style.display = view === 'list' ? '' : 'none';
        boardPane.style.display = view === 'board' ? '' : 'none';
    }

    viewTabs.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-view]');
        if (button) {
            switchView(button.dataset.view);
        }
    });

    const boardHead = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '工单看板' }),
            h('span', { class: 'hint', text: '按状态分列展示进行中的工单；状态只能由「详情页里的动作」推进，看板不支持拖拽。' }),
            h('div', { class: 'grow' }),
            h('button', { class: 'btn btn-sm', type: 'button', text: '↻ 刷新', onclick: () => loadBoard() })));

    async function loadBoard() {
        boardHost.innerHTML = '<div class="table-empty">看板加载中…</div>';
        try {
            const results = await Promise.all(BOARD_COLUMNS.map((code) =>
                api.get('/api/workorder', { pageNum: 1, pageSize: BOARD_PAGE_SIZE, status: code })
                    .catch(() => ({ records: [], total: 0 }))));
            boardHost.innerHTML = '';
            BOARD_COLUMNS.forEach((code, index) => {
                const data = results[index];
                const rows = data.records || [];
                const column = h('div', { class: 'board-col' },
                    h('div', { class: 'board-col-head' },
                        h('span', { html: woStatus(code) }),
                        h('span', { class: 'board-count', text: String(data.total ?? rows.length) })));
                const body = h('div', { class: 'board-col-body' });
                if (!rows.length) {
                    body.append(h('div', { class: 'board-empty', text: '暂无工单' }));
                }
                rows.forEach((row) => {
                    body.append(h('button', {
                        class: 'board-card', type: 'button',
                        title: '打开工单详情',
                        onclick: () => ctx.navigate(`/workorder/${row.id}`),
                    },
                    h('div', { class: 'board-card-title' },
                        h('span', { class: 'mono', text: row.orderNo }),
                        h('span', { html: priorityTag(row.priority) })),
                    h('div', { class: 'board-card-main', text: row.title }),
                    h('div', { class: 'board-card-sub', text: [row.equipmentName, row.assigneeName || '未派单'].filter(Boolean).join(' · ') }),
                    h('div', { class: 'board-card-foot' },
                        h('span', { class: 'cell-sub', text: dueText(row.resolveDueTime) }))));
                });
                column.append(body);
                boardHost.append(column);
            });
        } catch (error) {
            boardHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    const listCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '工单列表' }), summary),
        h('div', { class: 'card-body' }, tableHost, pagerHost));

    listPane.append(filters, listCard);
    boardPane.append(boardHead, boardHost);

    container.append(head, viewTabs, listPane, boardPane);
    switchView(activeView);

    // 看板懒加载：首次切到看板（或直接带参进入）时才拉数据
    let boardReady = false;
    if (activeView === 'board') {
        boardReady = true;
        loadBoard();
    }
    viewTabs.addEventListener('click', () => {
        if (activeView === 'board' && !boardReady) {
            boardReady = true;
            loadBoard();
        }
    });

    keywordInput.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            search();
        }
    });

    await load();
}
