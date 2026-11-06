/**
 * 巡检任务列表。
 *
 * 任务有两个来源：定时引擎按计划自动生成（AUTO）、扫码或补检时人工发起（MANUAL）。
 * 「只看我的」用 assigneeId=0 传给后端，由服务端翻译成当前登录人，避免前端传错人。
 */

import { api, auth } from '../api.js';
import {
    INSPECT_STATUS, canExecute, debounce, esc, formatDate, guard, h, modal,
    renderPagination, renderTable, statusTag, toast, toastError,
} from '../ui.js';
import { renderCalendarView } from './inspect-calendar.js';

/** 后端约定的「只看我的」哨兵值 */
const MINE = 0;

/** 状态筛选标签：与状态下拉框双向同步 */
const STATUS_CHIPS = ['PENDING', 'IN_PROGRESS', 'SUBMITTED', 'EXPIRED'];

export async function render(container, ctx) {
    const mayExecute = auth.can('inspect:task:execute');
    const query = {
        pageNum: 1,
        pageSize: 10,
        keyword: '',
        status: '',
        result: '',
        dateFrom: '',
        dateTo: '',
        assigneeId: '',
        overdueOnly: '',
    };
    // 支持带参直达：/inspect/task?status=PENDING 或 ?view=calendar
    const urlView = ctx.query && ctx.query.get('view') === 'calendar' ? 'calendar' : 'list';
    let activeView = urlView;

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '巡检任务' }),
            h('p', { text: '按计划日查看待执行与已完成的任务；进入任务可看设备二维码、逐项检查标准与已填结果。' })),
        h('div', { class: 'page-actions' },
            mayExecute ? h('button', {
                class: 'btn btn-primary', type: 'button', text: '＋ 手工发起巡检',
                onclick: () => openCreate(),
            }) : null));

    const keywordInput = h('input', {
        class: 'input', type: 'search', placeholder: '任务号 / 设备名称 / 资产编号', 'aria-label': '关键字',
    });
    const statusSelect = h('select', { class: 'select', 'aria-label': '任务状态' },
        h('option', { value: '', text: '全部状态' }),
        ...Object.entries(INSPECT_STATUS)
            .filter(([code]) => ['PENDING', 'IN_PROGRESS', 'SUBMITTED', 'EXPIRED'].includes(code))
            .map(([code, item]) => h('option', { value: code, text: item.label })));
    const resultSelect = h('select', { class: 'select', 'aria-label': '巡检结果' },
        h('option', { value: '', text: '全部结果' }),
        h('option', { value: 'ABNORMAL', text: '有异常' }),
        h('option', { value: 'NORMAL', text: '全部正常' }));
    const fromInput = h('input', { class: 'input', type: 'date', 'aria-label': '开始日期' });
    const toInput = h('input', { class: 'input', type: 'date', 'aria-label': '结束日期' });
    const mineCheck = h('input', { type: 'checkbox' });
    const overdueCheck = h('input', { type: 'checkbox' });

    const toolbar = h('div', { class: 'toolbar' },
        h('div', { class: 'field grow' }, h('label', { text: '关键字' }), keywordInput),
        h('div', { class: 'field' }, h('label', { text: '状态' }), statusSelect),
        h('div', { class: 'field' }, h('label', { text: '结果' }), resultSelect),
        h('div', { class: 'field' }, h('label', { text: '计划日从' }), fromInput),
        h('div', { class: 'field' }, h('label', { text: '到' }), toInput),
        h('div', { class: 'spacer' }),
        h('label', { class: 'check-line' }, mineCheck, h('span', { text: '只看我的' })),
        h('label', { class: 'check-line' }, overdueCheck, h('span', { text: '仅逾期' })),
        h('button', { class: 'btn', type: 'button', text: '重置', onclick: () => resetQuery() }),
        h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: () => load(1) }));

    /* ------------------------ 视图切换（列表 / 日历） ------------------------ */

    const viewTabs = h('div', { class: 'card section-tabs', role: 'tablist' },
        h('button', { type: 'button', role: 'tab', text: '任务列表', dataset: { view: 'list' } }),
        h('button', { type: 'button', role: 'tab', text: '巡检日历', dataset: { view: 'calendar' } }));

    const listPane = h('div', {});
    const calendarPane = h('div', { style: 'display:none' });

    function switchView(view) {
        activeView = view;
        viewTabs.querySelectorAll('button').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.view === view);
        });
        listPane.style.display = view === 'list' ? '' : 'none';
        calendarPane.style.display = view === 'calendar' ? '' : 'none';
    }

    viewTabs.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-view]');
        if (button) {
            switchView(button.dataset.view);
        }
    });

    const statusChips = h('div', { class: 'chip-row status-chips', style: 'padding:0 18px 4px' },
        h('span', { class: 'chip-label', text: '状态' }),
        h('button', { class: 'chip active', type: 'button', dataset: { status: '' }, text: '全部' }),
        ...STATUS_CHIPS.map((code) => h('button', {
            class: 'chip', type: 'button', dataset: { status: code },
            text: INSPECT_STATUS[code].label,
        })));

    statusChips.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-status]');
        if (!button) {
            return;
        }
        statusSelect.value = button.dataset.status;
        statusChips.querySelectorAll('button').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.status === button.dataset.status);
        });
        load(1);
    });

    // 状态下拉框变化时反向同步标签高亮
    statusSelect.addEventListener('change', () => {
        statusChips.querySelectorAll('button').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.status === statusSelect.value);
        });
    });

    const quickBar = h('div', { class: 'chip-row', style: 'padding:0 18px 12px' },
        chip('今天', () => {
            const today = todayText();
            fromInput.value = today;
            toInput.value = today;
            load(1);
        }),
        chip('最近 7 天', () => {
            const to = new Date();
            const from = new Date();
            from.setDate(from.getDate() - 6);
            fromInput.value = isoDate(from);
            toInput.value = isoDate(to);
            load(1);
        }),
        chip('我的待执行', () => {
            mineCheck.checked = true;
            statusSelect.value = 'PENDING';
            fromInput.value = '';
            toInput.value = '';
            load(1);
        }));

    const summary = h('span', { class: 'hint', text: '' });
    const tableHost = h('div', {});
    const pagerHost = h('div', {});

    listPane.append(h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '任务清单' }), summary),
        toolbar, statusChips, quickBar, tableHost, pagerHost));

    container.append(head, viewTabs, listPane, calendarPane);
    switchView(activeView);

    // 日历 Tab 懒加载：首次切到日历时才拉数据
    let calendarReady = false;
    if (activeView === 'calendar') {
        calendarReady = true;
        renderCalendarView(calendarPane, ctx, ctx.query && ctx.query.get('month') ? ctx.query.get('month') : undefined);
    }
    viewTabs.addEventListener('click', () => {
        if (activeView === 'calendar' && !calendarReady) {
            calendarReady = true;
            renderCalendarView(calendarPane, ctx, ctx.query && ctx.query.get('month') ? ctx.query.get('month') : undefined);
        }
    });

    keywordInput.addEventListener('input', debounce(() => load(1), 380));
    [statusSelect, resultSelect, fromInput, toInput].forEach((node) => node.addEventListener('change', () => load(1)));
    [mineCheck, overdueCheck].forEach((node) => node.addEventListener('change', () => load(1)));

    // URL 带参初始化状态筛选（/inspect/task?status=PENDING）
    if (ctx.query && ctx.query.get('status') && STATUS_CHIPS.includes(ctx.query.get('status'))) {
        statusSelect.value = ctx.query.get('status');
        statusChips.querySelectorAll('button').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.status === ctx.query.get('status'));
        });
    }

    function resetQuery() {
        keywordInput.value = '';
        statusSelect.value = '';
        resultSelect.value = '';
        fromInput.value = '';
        toInput.value = '';
        mineCheck.checked = false;
        overdueCheck.checked = false;
        statusChips.querySelectorAll('button').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.status === '');
        });
        load(1);
    }

    async function load(pageNum = query.pageNum) {
        query.pageNum = pageNum;
        query.keyword = keywordInput.value.trim();
        query.status = statusSelect.value;
        query.result = resultSelect.value;
        query.dateFrom = fromInput.value;
        query.dateTo = toInput.value;
        query.assigneeId = mineCheck.checked ? MINE : '';
        query.overdueOnly = overdueCheck.checked ? 'true' : '';

        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        try {
            const data = await api.get('/api/inspection/tasks', query);
            const rows = data.records || data.list || [];
            summary.textContent = `共 ${data.total} 条任务`;

            tableHost.innerHTML = renderTable({
                columns: [
                    {
                        title: '任务号', key: 'taskCode', width: '130px',
                        render: (row) => `<a href="#/inspect/task/${row.id}" class="mono">${esc(row.taskCode)}</a>`
                            + (row.source === 'MANUAL' ? '<span class="tag tag-outline" style="margin-left:6px">补检</span>' : ''),
                    },
                    {
                        title: '设备', key: 'equipmentName',
                        render: (row) => `${esc(row.equipmentName)}<div class="cell-sub mono">${esc(row.assetNo || '')}</div>`,
                    },
                    {
                        title: '来自计划', key: 'planName', width: '150px',
                        render: (row) => esc(row.planName || row.templateName || '手工发起'),
                    },
                    { title: '计划日', key: 'planDate', width: '100px', render: (row) => formatDate(row.planDate) },
                    {
                        title: '进度', key: 'finishedItemCount', width: '86px', align: 'right',
                        render: (row) => {
                            const total = row.itemCount ?? 0;
                            const done = row.finishedItemCount ?? 0;
                            if (row.status === 'PENDING') {
                                return `<span style="color:var(--text-muted)">${total} 项</span>`;
                            }
                            return `<span${done === total ? ' style="color:var(--success)"' : ''}>${done}/${total}</span>`;
                        },
                    },
                    { title: '执行人', key: 'assigneeName', width: '90px', render: (row) => esc(row.assigneeName || '—') },
                    {
                        title: '状态', key: 'status', width: '96px',
                        render: (row) => statusTag(INSPECT_STATUS, row.status)
                            + (row.overdueFlag === 1 && row.status !== 'EXPIRED' ? '<span class="tag tag-red" style="margin-left:4px">超时</span>' : ''),
                    },
                    {
                        title: '时限', key: 'slaDueTime', width: '120px',
                        render: (row) => (row.slaDueTime ? formatDate(row.slaDueTime, true) : '—'),
                    },
                    {
                        title: '操作', key: 'id', width: '140px', align: 'right',
                        render: (row) => {
                            const buttons = [`<button class="btn btn-sm btn-ghost" data-action="detail" data-id="${row.id}">详情</button>`];
                            if (mayExecute && canExecute(row.status)) {
                                buttons.push(`<button class="btn btn-sm btn-primary" data-action="execute" data-id="${row.id}">去执行</button>`);
                            }
                            return buttons.join('');
                        },
                    },
                ],
                rows,
                empty: '没有符合条件的任务。计划启用后由定时引擎自动生成',
            });

            tableHost.querySelectorAll('button[data-action]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = Number(button.dataset.id);
                    ctx.navigate(button.dataset.action === 'execute'
                        ? `/inspect/task/${id}/execute`
                        : `/inspect/task/${id}`);
                });
            });

            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(data, { onChange: (page) => load(page) }));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    /* -------------------------- 手工发起巡检 -------------------------- */

    async function openCreate() {
        let templates = [];
        let equipments = [];
        try {
            const [templateData, equipmentData] = await Promise.all([
                api.get('/api/inspection/templates/options'),
                api.get('/api/asset/equipment', { pageNum: 1, pageSize: 100 }),
            ]);
            templates = templateData || [];
            equipments = equipmentData.records || equipmentData.list || [];
        } catch (error) {
            toastError(error);
            return;
        }

        const equipmentSelect = h('select', { class: 'select' },
            h('option', { value: '', text: '请选择设备' }),
            ...equipments.map((item) => h('option', {
                value: item.id,
                text: `${item.equipmentName}（${item.assetNo}）· ${item.locationPath || ''}`,
            })));
        const templateSelect = h('select', { class: 'select' },
            h('option', { value: '', text: '使用设备类别的默认模板' }),
            ...templates.map((item) => h('option', {
                value: item.id, text: `${item.templateName}（${item.itemCount} 项）`,
            })));
        const remarkInput = h('input', { class: 'input', type: 'text', placeholder: '为什么临时补检', maxlength: '100' });

        const body = h('div', {},
            h('div', { class: 'form-grid' },
                h('div', { class: 'field grow' }, h('label', { text: '设备' }), equipmentSelect),
                h('div', { class: 'field grow' }, h('label', { text: '巡检模板' }), templateSelect),
                h('div', { class: 'field grow' }, h('label', { text: '备注' }), remarkInput)),
            h('div', { class: 'hint', text: '手工发起的任务同样计入巡检统计，但不影响计划的周期排程。' }));

        const dialog = modal({
            title: '手工发起巡检',
            body,
            footer: [
                { label: '取消' },
                {
                    label: '创建任务',
                    type: 'primary',
                    close: false,
                    onClick: async ({ button }) => {
                        if (!equipmentSelect.value) {
                            toast('请选择设备', 'error');
                            return false;
                        }
                        await guard(button, async () => {
                            try {
                                const id = await api.post('/api/inspection/tasks', {
                                    equipmentId: Number(equipmentSelect.value),
                                    templateId: templateSelect.value ? Number(templateSelect.value) : null,
                                    remark: remarkInput.value.trim() || null,
                                });
                                dialog.close();
                                toast('巡检任务已创建', 'success');
                                ctx.navigate(`/inspect/task/${id}/execute`);
                            } catch (error) {
                                toastError(error);
                            }
                        });
                        return false;
                    },
                },
            ],
        });
    }

    await load(1);
}

/* ------------------------------ 小工具 ------------------------------ */

function chip(text, onClick) {
    return h('button', { class: 'chip', type: 'button', text, onclick: onClick });
}

function isoDate(date) {
    return date.toISOString().slice(0, 10);
}

function todayText() {
    return isoDate(new Date());
}
