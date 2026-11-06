/**
 * 巡检计划管理。
 *
 * 计划把「模板 + 作用范围 + 执行人 + 周期」绑在一起，由定时引擎按周期生成任务。
 * 页面上的作用范围是「配置」，服务端保存时会展开成设备快照；
 * 之后新增设备会在下一轮生成前自动并入快照，设备报废则自动移出。
 */

import { api, auth } from '../api.js';
import {
    CYCLE_TYPE, EXECUTOR_STRATEGY, INSPECT_STATUS, PLAN_STATUS, SCOPE_TYPE, WEEKDAYS,
    confirmDialog, debounce, esc, formatDate, guard, h, modal,
    renderPagination, renderTable, statusTag, toast, toastError,
} from '../ui.js';

let templateCache = null;
let categoryCache = null;
let locationCache = null;
let userCache = null;
let equipmentCache = null;

async function ensureOptions() {
    const tasks = [];
    if (!templateCache) {
        tasks.push(api.get('/api/inspection/templates/options')
            .then((data) => { templateCache = data || []; })
            .catch(() => { templateCache = []; }));
    }
    if (!categoryCache) {
        tasks.push(api.get('/api/asset/categories/tree')
            .then((data) => { categoryCache = flatten(data, 'categoryName'); })
            .catch(() => { categoryCache = []; }));
    }
    if (!locationCache) {
        tasks.push(api.get('/api/asset/locations/tree')
            .then((data) => { locationCache = flatten(data, 'locationName'); })
            .catch(() => { locationCache = []; }));
    }
    if (!userCache) {
        tasks.push(api.get('/api/system/users/options')
            .then((data) => { userCache = data || []; })
            .catch(() => { userCache = []; }));
    }
    await Promise.all(tasks);
}

async function ensureEquipments() {
    if (equipmentCache) {
        return equipmentCache;
    }
    // 设备列表接口每页上限 100，逐页拉全量，保证计划能覆盖到全部在役设备
    const all = [];
    try {
        for (let page = 1; page <= 30; page += 1) {
            const data = await api.get('/api/asset/equipment', { pageNum: page, pageSize: 100 });
            const rows = data.records || data.list || [];
            all.push(...rows);
            if (rows.length < 100 || all.length >= (data.total || 0)) {
                break;
            }
        }
    } catch (error) {
        // 权限不足或网络异常时降级为已取到的部分，页面仍可用分类/位置作用域
    }
    equipmentCache = all;
    return equipmentCache;
}

function flatten(nodes, nameKey, level = 0, result = []) {
    (nodes || []).forEach((node) => {
        result.push({ id: node.id, name: node[nameKey] || node.name, level });
        if (node.children && node.children.length) {
            flatten(node.children, nameKey, level + 1, result);
        }
    });
    return result;
}

export async function render(container, ctx) {
    const canManage = auth.can('inspect:plan:manage');
    const canGenerate = auth.can('inspect:plan:generate');
    const query = { pageNum: 1, pageSize: 10, keyword: '', status: '', cycleType: '' };

    await ensureOptions();

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '巡检计划' }),
            h('p', { text: '按周期自动生成巡检任务的规则。启用后由定时引擎在每天的任务窗口内补齐，暂停即刻停止生成。' })),
        h('div', { class: 'page-actions' },
            canManage ? h('button', {
                class: 'btn btn-primary', type: 'button', text: '＋ 新建计划',
                onclick: () => openEditor(null),
            }) : null));

    const keywordInput = h('input', {
        class: 'input', type: 'search', placeholder: '计划名称 / 编码', 'aria-label': '关键字',
    });
    const statusSelect = h('select', { class: 'select', 'aria-label': '状态' },
        h('option', { value: '', text: '全部状态' }),
        ...Object.entries(PLAN_STATUS).map(([code, item]) => h('option', { value: code, text: item.label })));
    const cycleSelect = h('select', { class: 'select', 'aria-label': '周期' },
        h('option', { value: '', text: '全部周期' }),
        ...Object.entries(CYCLE_TYPE).map(([code, item]) => h('option', { value: code, text: item.label })));

    const toolbar = h('div', { class: 'toolbar' },
        h('div', { class: 'field grow' }, h('label', { text: '关键字' }), keywordInput),
        h('div', { class: 'field' }, h('label', { text: '状态' }), statusSelect),
        h('div', { class: 'field' }, h('label', { text: '周期' }), cycleSelect),
        h('div', { class: 'spacer' }),
        h('button', { class: 'btn', type: 'button', text: '重置', onclick: () => resetQuery() }),
        h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: () => load(1) }));

    const summary = h('span', { class: 'hint', text: '' });
    const tableHost = h('div', {});
    const pagerHost = h('div', {});

    container.append(head, h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '计划清单' }), summary),
        toolbar, tableHost, pagerHost));

    keywordInput.addEventListener('input', debounce(() => load(1), 400));
    [statusSelect, cycleSelect].forEach((select) => select.addEventListener('change', () => load(1)));

    function resetQuery() {
        keywordInput.value = '';
        statusSelect.value = '';
        cycleSelect.value = '';
        load(1);
    }

    async function load(pageNum = query.pageNum) {
        query.pageNum = pageNum;
        query.keyword = keywordInput.value.trim();
        query.status = statusSelect.value;
        query.cycleType = cycleSelect.value;

        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:55%"></div></div>';
        try {
            const data = await api.get('/api/inspection/plans', query);
            const rows = data.records || data.list || [];
            summary.textContent = `共 ${data.total} 个计划`;

            tableHost.innerHTML = renderTable({
                columns: [
                    {
                        title: '计划', key: 'planName',
                        render: (row) => `<a href="#/inspect/plan" data-detail="${row.id}">${esc(row.planName)}</a>`
                            + `<div class="cell-sub mono">${esc(row.planCode)}</div>`,
                    },
                    {
                        title: '模板', key: 'templateName', width: '150px',
                        render: (row) => esc(row.templateName || '—'),
                    },
                    {
                        title: '范围', key: 'scopeLabel', width: '170px',
                        render: (row) => `<div>${esc(row.scopeLabel || SCOPE_TYPE[row.scopeType]?.label || '—')}</div>`
                            + `<div class="cell-sub">${esc(row.equipmentCount ?? 0)} 台设备</div>`,
                    },
                    {
                        title: '周期', key: 'cycleLabel', width: '160px',
                        render: (row) => `${esc(row.cycleLabel || CYCLE_TYPE[row.cycleType]?.label || '—')}`,
                    },
                    {
                        title: '执行人', key: 'executorNames', width: '130px',
                        render: (row) => esc(row.executorNames || EXECUTOR_STRATEGY[row.executorStrategy]?.label || '—'),
                    },
                    { title: '状态', key: 'status', width: '86px', render: (row) => statusTag(PLAN_STATUS, row.status) },
                    {
                        title: '下次生成', key: 'nextGenerateTime', width: '120px',
                        render: (row) => (row.status === 'ENABLED'
                            ? formatDate(row.nextGenerateTime, true)
                            : '<span style="color:var(--text-muted)">已暂停</span>'),
                    },
                    {
                        title: '操作', key: 'id', width: '250px', align: 'right',
                        render: (row) => {
                            const buttons = [`<button class="btn btn-sm btn-ghost" data-action="detail" data-id="${row.id}">详情</button>`];
                            if (canManage) {
                                buttons.push(`<button class="btn btn-sm btn-ghost" data-action="edit" data-id="${row.id}">编辑</button>`);
                                buttons.push(row.status === 'ENABLED'
                                    ? `<button class="btn btn-sm btn-ghost" data-action="disable" data-id="${row.id}">暂停</button>`
                                    : `<button class="btn btn-sm btn-ghost" data-action="enable" data-id="${row.id}">启用</button>`);
                            }
                            if (canGenerate) {
                                buttons.push(`<button class="btn btn-sm btn-ghost" data-action="generate" data-id="${row.id}">立即生成</button>`);
                            }
                            if (canManage) {
                                buttons.push(`<button class="btn btn-sm btn-ghost danger" data-action="delete" data-id="${row.id}">删除</button>`);
                            }
                            return buttons.join('');
                        },
                    },
                ],
                rows,
                empty: '还没有巡检计划。先建一个模板，再按周期安排计划',
            });

            tableHost.querySelectorAll('button[data-action]').forEach((button) => {
                button.addEventListener('click', () => handleAction(button.dataset.action, Number(button.dataset.id)));
            });
            tableHost.querySelectorAll('a[data-detail]').forEach((link) => {
                link.addEventListener('click', (event) => {
                    event.preventDefault();
                    openDetail(Number(link.dataset.detail));
                });
            });

            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(data, { onChange: (page) => load(page) }));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    async function handleAction(action, id) {
        if (action === 'detail') {
            openDetail(id);
            return;
        }
        if (action === 'edit') {
            openEditor(id);
            return;
        }
        if (action === 'enable' || action === 'disable') {
            try {
                await api.patch(`/api/inspection/plans/${id}/status?status=${action === 'enable' ? 'ENABLED' : 'DISABLED'}`);
                toast(action === 'enable' ? '计划已启用' : '计划已暂停', 'success');
                load();
            } catch (error) {
                toastError(error);
            }
            return;
        }
        if (action === 'generate') {
            try {
                const created = await api.post(`/api/inspection/plans/${id}/generate`);
                toast(`本次生成 ${created} 条任务（已存在的不会重复生成）`, 'success', 3600);
                load();
            } catch (error) {
                toastError(error);
            }
            return;
        }
        if (action === 'delete') {
            const ok = await confirmDialog({
                title: '删除计划',
                message: '删除后不再生成新任务，已生成的任务与巡检记录仍保留。启用中的计划需要先暂停。',
                confirmText: '删除',
                danger: true,
            });
            if (!ok) {
                return;
            }
            try {
                await api.del(`/api/inspection/plans/${id}`);
                toast('计划已删除', 'success');
                load();
            } catch (error) {
                toastError(error);
            }
        }
    }

    /* ---------------------------- 详情 ---------------------------- */

    async function openDetail(id) {
        let detail;
        try {
            detail = await api.get(`/api/inspection/plans/${id}`);
        } catch (error) {
            toastError(error);
            return;
        }
        const kv = (label, value) => h('div', { class: 'kv' },
            h('span', { class: 'kv-label', text: label }),
            h('span', { class: 'kv-value', html: value }));
        const body = h('div', {},
            h('div', { class: 'kv-grid' },
                kv('计划编码', `<span class="mono">${esc(detail.planCode)}</span>`),
                kv('状态', statusTag(PLAN_STATUS, detail.status)),
                kv('巡检模板', esc(detail.templateName || '—')),
                kv('执行周期', esc(detail.cycleLabel || '—')),
                kv('作用范围', `${esc(detail.scopeLabel || '—')}（${detail.equipmentCount ?? 0} 台设备）`),
                kv('执行人', esc(detail.executorNames || '—')),
                kv('生效时间', `${formatDate(detail.startDate)} ~ ${detail.endDate ? formatDate(detail.endDate) : '长期'}`),
                kv('时限 / 提前生成', `${detail.slaMinutes ?? '—'} 分钟 / 提前 ${detail.generateAheadHours ?? 0} 小时`),
                kv('下次生成', formatDate(detail.nextGenerateTime, true)),
                kv('已生成任务', `${detail.taskCount ?? 0} 条`)),
            h('div', { class: 'form-section-title', text: '范围内的设备' }),
            h('div', { html: renderTable({
                columns: [
                    { title: '资产编号', key: 'assetNo', render: (row) => `<span class="mono">${esc(row.assetNo)}</span>` },
                    { title: '设备名称', key: 'equipmentName', render: (row) => esc(row.equipmentName) },
                    { title: '分类', key: 'categoryName', render: (row) => esc(row.categoryName || '—') },
                    { title: '位置', key: 'locationPath', render: (row) => esc(row.locationPath || '—') },
                ],
                rows: detail.equipment || [],
                empty: '该计划范围内暂时没有设备',
            }) }),
            h('div', { class: 'form-section-title', text: '最近生成的任务' }),
            h('div', { html: renderTable({
                columns: [
                    { title: '任务号', key: 'taskCode', render: (row) => `<span class="mono">${esc(row.taskCode)}</span>` },
                    { title: '计划日', key: 'planDate', width: '100px', render: (row) => formatDate(row.planDate) },
                    { title: '设备', key: 'equipmentName', render: (row) => esc(row.equipmentName) },
                    { title: '执行人', key: 'assigneeName', width: '100px', render: (row) => esc(row.assigneeName || '—') },
                    { title: '状态', key: 'status', width: '88px', render: (row) => statusTag(INSPECT_STATUS, row.status) },
                ],
                rows: detail.recentTasks || [],
                empty: '还没有生成任务',
            }) }));

        const dialog = modal({
            title: '计划详情',
            width: 'wide',
            body,
            footer: [
                { label: '关闭' },
                canManage ? {
                    label: '编辑',
                    type: 'primary',
                    onClick: () => {
                        dialog.close();
                        openEditor(id);
                    },
                } : null,
            ].filter(Boolean),
        });
    }

    /* ---------------------------- 编辑器 ---------------------------- */

    async function openEditor(id) {
        let detail = null;
        if (id) {
            try {
                detail = await api.get(`/api/inspection/plans/${id}`);
            } catch (error) {
                toastError(error);
                return;
            }
        }
        const equipments = await ensureEquipments();

        const state = {
            scopeType: detail ? detail.scopeType : 'CATEGORY',
            scopeIds: detail && detail.scopeIds ? [...detail.scopeIds] : [],
            executorStrategy: detail ? detail.executorStrategy : 'FIXED',
            executorIds: detail && detail.executorIdList ? [...detail.executorIdList] : [],
            weekDays: parseCycle(detail, 'weekDays', [1]),
            monthDays: parseCycle(detail, 'monthDays', [1]),
        };

        const codeInput = h('input', {
            class: 'input mono', type: 'text', name: 'planCode', value: detail ? detail.planCode : '',
            placeholder: '留空自动生成', maxlength: '40', disabled: !!detail,
        });
        const nameInput = h('input', {
            class: 'input', type: 'text', name: 'planName', value: detail ? detail.planName : '', placeholder: '如 水泵每周巡检', maxlength: '60',
        });
        const templateSelect = h('select', { class: 'select', name: 'templateId' },
            h('option', { value: '', text: '请选择已发布的模板' }),
            ...templateCache.map((item) => h('option', {
                value: item.id,
                text: `${item.templateName}（${item.itemCount} 项）`,
                selected: detail && String(detail.templateId) === String(item.id),
            })));
        const scopeTypeSelect = h('select', { class: 'select', name: 'scopeType' },
            ...Object.entries(SCOPE_TYPE).map(([code, meta]) => h('option', {
                value: code, text: meta.label, selected: code === state.scopeType,
            })));
        const strategySelect = h('select', { class: 'select', name: 'executorStrategy' },
            ...Object.entries(EXECUTOR_STRATEGY).map(([code, meta]) => h('option', {
                value: code, text: meta.label, selected: code === state.executorStrategy,
            })));
        const cycleSelect = h('select', { class: 'select', name: 'cycleType' },
            ...Object.entries(CYCLE_TYPE).map(([code, meta]) => h('option', {
                value: code, text: meta.label, selected: detail ? detail.cycleType === code : code === 'DAILY',
            })));
        const timeInput = h('input', {
            class: 'input', type: 'time', name: 'executeTime', value: parseCycle(detail, 'time', '09:00'),
        });
        const startInput = h('input', {
            class: 'input', type: 'date', name: 'startDate', value: detail ? detail.startDate : new Date().toISOString().slice(0, 10),
        });
        const endInput = h('input', {
            class: 'input', type: 'date', name: 'endDate', value: detail && detail.endDate ? detail.endDate : '',
        });
        const slaInput = h('input', {
            class: 'input', type: 'number', min: '0', name: 'slaMinutes', value: detail ? (detail.slaMinutes ?? 480) : 480,
        });
        const aheadInput = h('input', {
            class: 'input', type: 'number', min: '0', max: '168', name: 'generateAheadHours',
            value: detail ? (detail.generateAheadHours ?? 24) : 24,
        });

        const scopeHost = h('div', { class: 'stack' });
        const weekHost = h('div', { class: 'chip-row' });
        const monthHost = h('div', { class: 'chip-row' });
        const executorHost = h('div', { class: 'stack' });
        const cycleExtra = h('div', { class: 'form-grid' }, weekHost, monthHost);

        const body = h('div', {},
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { text: '计划编码' }), codeInput),
                h('div', { class: 'field' }, h('label', { text: '计划名称' }), nameInput),
                h('div', { class: 'field' }, h('label', { text: '巡检模板' }), templateSelect),
                h('div', { class: 'field' }, h('label', { text: '执行周期' }), cycleSelect),
                h('div', { class: 'field' }, h('label', { text: '每次执行时间' }), timeInput),
                h('div', { class: 'field' }, h('label', { text: '生效开始日' }), startInput),
                h('div', { class: 'field' }, h('label', { text: '生效结束日（可空）' }), endInput),
                h('div', { class: 'field' }, h('label', { text: '任务时限（分钟）' }), slaInput),
                h('div', { class: 'field' }, h('label', { text: '提前生成（小时）' }), aheadInput)),
            cycleExtra,
            h('div', { class: 'form-section-title', text: '作用范围' }),
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { text: '范围类型' }), scopeTypeSelect)),
            scopeHost,
            h('div', { class: 'form-section-title', text: '执行人' }),
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { text: '指派方式' }), strategySelect)),
            executorHost);

        const dialog = modal({
            title: detail ? '编辑巡检计划' : '新建巡检计划',
            width: 'wide',
            body,
            footer: [
                { label: '取消' },
                {
                    label: '保存',
                    type: 'primary',
                    close: false,
                    onClick: async ({ button }) => {
                        const payload = collect();
                        if (!payload) {
                            return false;
                        }
                        await guard(button, async () => {
                            try {
                                if (detail) {
                                    await api.put(`/api/inspection/plans/${detail.id}`, payload);
                                    toast('计划已更新', 'success');
                                } else {
                                    await api.post('/api/inspection/plans', payload);
                                    toast('计划已创建，当前为暂停状态；确认范围无误后点「启用」开始生成任务', 'success', 4200);
                                }
                                dialog.close();
                                load();
                            } catch (error) {
                                toastError(error);
                            }
                        });
                        return false;
                    },
                },
            ],
        });

        /* 范围选择：三种类型共用一套复选列表 */
        function renderScope() {
            scopeHost.innerHTML = '';
            const config = { EQUIPMENT: equipments.map((e) => ({ id: e.id, name: `${e.equipmentName}（${e.assetNo}）` })), CATEGORY: categoryCache, LOCATION: locationCache }[state.scopeType] || [];
            if (!config.length) {
                scopeHost.append(h('div', { class: 'hint', text: '没有可选项，请先在设备台账里维护' }));
                return;
            }
            const hint = {
                EQUIPMENT: '直接挑选设备，逐台生成任务',
                CATEGORY: '选择分类后，该分类（含子分类）下的在役设备都会纳入范围',
                LOCATION: '选择位置后，该位置（含子位置）下的设备都会纳入范围',
            }[state.scopeType];
            scopeHost.append(h('div', { class: 'hint', text: hint }));
            scopeHost.append(picker(config, state.scopeIds, (ids) => { state.scopeIds = ids; }));
            scopeHost.append(h('div', { class: 'hint', text: `已选 ${state.scopeIds.length} 项` }));
        }

        function renderExecutors() {
            executorHost.innerHTML = '';
            if (state.executorStrategy === 'DEPT_OWNER') {
                executorHost.append(h('div', { class: 'hint', text: '任务自动派给设备的责任人或负责人，无需指定人员' }));
                return;
            }
            executorHost.append(h('div', {
                class: 'hint',
                text: state.executorStrategy === 'ROUND_ROBIN'
                    ? '在选中的人员里按计划日轮流分配，避免总是同一个人'
                    : '所有任务都指派给第一个选中的人',
            }));
            executorHost.append(picker(
                userCache.map((u) => ({ id: u.id, name: u.realName || u.username })),
                state.executorIds,
                (ids) => { state.executorIds = ids; },
            ));
        }

        function renderCycle() {
            const type = cycleSelect.value;
            weekHost.innerHTML = '';
            monthHost.innerHTML = '';
            weekHost.style.display = type === 'WEEKLY' ? '' : 'none';
            monthHost.style.display = type === 'MONTHLY' ? '' : 'none';

            if (type === 'WEEKLY') {
                weekHost.append(h('label', { text: '每周执行日' }));
                const row = h('div', { class: 'chip-row' });
                WEEKDAYS.forEach((day) => {
                    const button = h('button', {
                        class: `chip${state.weekDays.includes(day.value) ? ' active' : ''}`,
                        type: 'button',
                        text: day.label,
                        onclick: () => {
                            state.weekDays = toggle(state.weekDays, day.value);
                            button.classList.toggle('active', state.weekDays.includes(day.value));
                        },
                    });
                    row.append(button);
                });
                weekHost.append(row);
            }
            if (type === 'MONTHLY') {
                monthHost.append(h('label', { text: '每月执行日（月末不足时顺延到当月最后一天）' }));
                const row = h('div', { class: 'chip-row' });
                [1, 5, 10, 15, 20, 25, 28].forEach((day) => {
                    const button = h('button', {
                        class: `chip${state.monthDays.includes(day) ? ' active' : ''}`,
                        type: 'button',
                        text: `${day} 日`,
                        onclick: () => {
                            state.monthDays = toggle(state.monthDays, day);
                            button.classList.toggle('active', state.monthDays.includes(day));
                        },
                    });
                    row.append(button);
                });
                monthHost.append(row);
            }
        }

        function collect() {
            const planName = nameInput.value.trim();
            if (!planName) {
                toast('请填写计划名称', 'error');
                return null;
            }
            if (!templateSelect.value) {
                toast('请选择巡检模板', 'error');
                return null;
            }
            if (!state.scopeIds.length) {
                toast('请至少选择一个适用范围', 'error');
                return null;
            }
            if (!timeInput.value) {
                toast('请设置执行时间', 'error');
                return null;
            }
            const cycle = cycleSelect.value;
            if (cycle === 'WEEKLY' && !state.weekDays.length) {
                toast('每周计划至少要选一个执行日', 'error');
                return null;
            }
            if (cycle === 'MONTHLY' && !state.monthDays.length) {
                toast('每月计划至少要选一个执行日', 'error');
                return null;
            }
            if (state.executorStrategy !== 'DEPT_OWNER' && !state.executorIds.length) {
                toast('请选择执行人，或改为按设备负责人指派', 'error');
                return null;
            }
            return {
                planCode: (codeInput.value || '').trim() || null,
                planName,
                templateId: Number(templateSelect.value),
                scopeType: state.scopeType,
                scopeIds: state.scopeIds,
                executorStrategy: state.executorStrategy,
                executorIds: state.executorStrategy === 'DEPT_OWNER' ? [] : state.executorIds,
                cycleType: cycle,
                executeTime: timeInput.value,
                weekDays: cycle === 'WEEKLY' ? state.weekDays : null,
                monthDays: cycle === 'MONTHLY' ? state.monthDays : null,
                startDate: startInput.value,
                endDate: endInput.value || null,
                slaMinutes: Number(slaInput.value || 480),
                generateAheadHours: Number(aheadInput.value || 24),
            };
        }

        scopeTypeSelect.addEventListener('change', () => {
            state.scopeType = scopeTypeSelect.value;
            state.scopeIds = [];
            renderScope();
        });
        strategySelect.addEventListener('change', () => {
            state.executorStrategy = strategySelect.value;
            renderExecutors();
        });
        cycleSelect.addEventListener('change', renderCycle);

        renderScope();
        renderExecutors();
        renderCycle();
    }

    await load(1);
}

/* ------------------------------ 小工具 ------------------------------ */

function toggle(list, value) {
    return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

/** 从 cycleConfig JSON 里取某段配置，解析失败时用默认值 */
function parseCycle(detail, key, fallback) {
    if (!detail || !detail.cycleConfig) {
        return fallback;
    }
    try {
        const config = JSON.parse(detail.cycleConfig);
        return config[key] !== undefined && config[key] !== null ? config[key] : fallback;
    } catch (error) {
        return fallback;
    }
}

/** 带搜索的多选列表 */
function picker(items, selected, onChange) {
    const selectedSet = new Set(selected.map(String));
    const box = h('div', { class: 'picker' });
    const search = h('input', { class: 'input', type: 'search', placeholder: '输入关键字过滤' });
    const list = h('div', { class: 'picker-list' });

    function paint(keyword = '') {
        list.innerHTML = '';
        const filter = keyword.trim().toLowerCase();
        items
            .filter((item) => !filter || String(item.name).toLowerCase().includes(filter))
            .slice(0, 300)
            .forEach((item) => {
                const checkbox = h('input', { type: 'checkbox', value: item.id });
                checkbox.checked = selectedSet.has(String(item.id));
                checkbox.addEventListener('change', () => {
                    if (checkbox.checked) {
                        selectedSet.add(String(item.id));
                    } else {
                        selectedSet.delete(String(item.id));
                    }
                    onChange(Array.from(selectedSet).map(Number));
                });
                list.append(h('label', { class: 'picker-item' }, checkbox, h('span', { text: item.name })));
            });
        if (!list.children.length) {
            list.append(h('div', { class: 'hint', text: '没有匹配项' }));
        }
    }

    search.addEventListener('input', () => paint(search.value));
    paint();
    box.append(search, list);
    return box;
}
