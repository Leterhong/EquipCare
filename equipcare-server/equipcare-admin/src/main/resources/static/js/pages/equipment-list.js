/**
 * 设备台账列表：分页 + 关键字 / 分类 / 位置 / 状态筛选。
 */

import { api, auth } from '../api.js';
import {
    EQUIPMENT_STATUS, confirmDialog, debounce, esc, formatDate, guard, h,
    renderPagination, renderTable, statusTag, toast, toastError,
} from '../ui.js';

export async function render(container, ctx) {
    const query = {
        pageNum: 1,
        pageSize: 10,
        keyword: '',
        categoryId: '',
        locationId: '',
        status: '',
    };

    const canCreate = auth.can('asset:equipment:create');
    const canUpdate = auth.can('asset:equipment:update');
    const canQr = auth.can('asset:equipment:qrcode');

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '设备台账' }),
            h('p', { text: '按编号、名称、分类、位置与状态检索设备档案；状态变更走独立权限，维修中由工单驱动。' })),
        h('div', { class: 'page-actions' },
            canCreate ? h('button', {
                class: 'btn btn-primary', type: 'button', text: '＋ 新增设备',
                onclick: () => ctx.navigate('/equipment/new'),
            }) : null,
        ),
    );

    const keywordInput = h('input', {
        class: 'input', type: 'search', placeholder: '资产编号 / 名称 / 序列号', 'aria-label': '关键字',
    });
    const categorySelect = h('select', { class: 'select', 'aria-label': '设备分类' },
        h('option', { value: '', text: '全部分类' }));
    const locationSelect = h('select', { class: 'select', 'aria-label': '所在位置' },
        h('option', { value: '', text: '全部位置' }));
    const statusSelect = h('select', { class: 'select', 'aria-label': '设备状态' },
        h('option', { value: '', text: '全部状态' }),
        ...Object.entries(EQUIPMENT_STATUS).map(([code, item]) => h('option', { value: code, text: item.label })));

    const toolbar = h('div', { class: 'toolbar' },
        h('div', { class: 'field grow' }, h('label', { text: '关键字' }), keywordInput),
        h('div', { class: 'field' }, h('label', { text: '分类' }), categorySelect),
        h('div', { class: 'field' }, h('label', { text: '位置' }), locationSelect),
        h('div', { class: 'field' }, h('label', { text: '状态' }), statusSelect),
        h('div', { class: 'spacer' }),
        h('button', { class: 'btn', type: 'button', text: '重置', onclick: () => resetQuery() }),
        h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: () => search() }),
    );

    const tableHost = h('div', {});
    const pagerHost = h('div', {});
    const summary = h('span', { class: 'hint', text: '' });

    /* 顶部状态统计卡：4 个轻量查询取 total（真实接口），点击即作为状态筛选 */
    const statHost = h('div', { class: 'equip-stat-grid' });
    const statDefs = [
        { code: '', label: '设备总数', cls: 'equip-stat-total' },
        { code: 'IN_USE', label: '在用' },
        { code: 'REPAIRING', label: '维修中' },
        { code: 'IDLE', label: '停用/闲置' },
    ];
    const statCards = new Map();
    statDefs.forEach(({ code, label, cls }) => {
        const value = h('div', { class: 'value', text: '—' });
        const node = h('button', {
            class: `stat equip-stat${cls ? ` ${cls}` : ''}`,
            type: 'button',
            title: `筛选${label}设备`,
            onclick: () => {
                statusSelect.value = code;
                query.status = code;
                query.pageNum = 1;
                search();
            },
        }, h('div', { class: 'label', text: label }), value);
        statCards.set(code, value);
        statHost.append(node);
    });
    loadStats();

    async function loadStats() {
        const results = await Promise.all(statDefs.map(({ code }) =>
            api.get('/api/asset/equipment', { pageNum: 1, pageSize: 1, ...(code ? { status: code } : {}) })
                .then((data) => [code, data.total ?? (data.records || data.list || []).length])
                .catch(() => [code, null])));
        results.forEach(([code, total]) => {
            statCards.get(code).textContent = total == null ? '—' : String(total);
        });
    }

    const card = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '设备档案' }), summary),
        toolbar,
        tableHost,
        pagerHost,
    );

    container.append(head, statHost, card);

    async function loadOptions() {
        try {
            const [categories, locations] = await Promise.all([
                api.get('/api/asset/categories/tree'),
                api.get('/api/asset/locations/tree'),
            ]);
            fillTreeOptions(categorySelect, categories, '全部分类');
            fillTreeOptions(locationSelect, locations, '全部位置');
        } catch (error) {
            toastError(error);
        }
    }

    function fillTreeOptions(select, nodes, placeholder, level = 0) {
        (nodes || []).forEach((node) => {
            const option = h('option', {
                value: node.id,
                text: `${'　'.repeat(level)}${node.categoryName || node.locationName || node.name}`,
            });
            select.append(option);
            if (node.children && node.children.length) {
                fillTreeOptions(select, node.children, placeholder, level + 1);
            }
        });
    }

    function search() {
        query.keyword = keywordInput.value.trim();
        query.categoryId = categorySelect.value;
        query.locationId = locationSelect.value;
        query.status = statusSelect.value;
        query.pageNum = 1;
        load();
    }

    function resetQuery() {
        keywordInput.value = '';
        categorySelect.value = '';
        locationSelect.value = '';
        statusSelect.value = '';
        search();
    }

    const debouncedSearch = debounce(() => search(), 400);
    keywordInput.addEventListener('input', debouncedSearch);
    [categorySelect, locationSelect, statusSelect].forEach((select) => {
        select.addEventListener('change', () => search());
    });

    function healthCell(score) {
        if (score === null || score === undefined) {
            return '<span style="color:var(--text-muted)">—</span>';
        }
        const value = Number(score);
        const color = value >= 90 ? 'var(--success)' : value >= 75 ? 'var(--primary)' : value >= 60 ? 'var(--warning)' : 'var(--danger)';
        return `<span style="font-weight:600;color:${color}">${value.toFixed(1)}</span>`;
    }

    async function load() {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        try {
            const data = await api.get('/api/asset/equipment', query);
            summary.textContent = `共 ${data.total} 台设备`;

            const rows = data.list || data.records || [];
            tableHost.innerHTML = renderTable({
                columns: [
                    {
                        title: '资产编号', key: 'assetNo', width: '160px',
                        render: (row) => `<a href="#/equipment/${row.id}" class="mono">${esc(row.assetNo)}</a>`,
                    },
                    {
                        title: '设备名称', key: 'equipmentName',
                        render: (row) => `<a href="#/equipment/${row.id}">${esc(row.equipmentName)}</a>`,
                    },
                    { title: '分类', key: 'categoryName', render: (row) => esc(row.categoryName || '—') },
                    { title: '位置', key: 'locationPath', render: (row) => esc(row.locationPath || '—') },
                    { title: '状态', key: 'status', width: '96px', render: (row) => statusTag(EQUIPMENT_STATUS, row.status) },
                    { title: '健康分', key: 'healthScore', width: '76px', align: 'right', render: (row) => healthCell(row.healthScore) },
                    {
                        title: '最近巡检', key: 'lastInspectTime', width: '110px',
                        render: (row) => formatDate(row.lastInspectTime, true),
                    },
                    {
                        title: '操作', key: 'id', width: '176px', align: 'right',
                        render: (row) => {
                            const buttons = [
                                `<button class="btn btn-sm btn-ghost" data-action="detail" data-id="${row.id}">详情</button>`,
                            ];
                            if (canUpdate) {
                                buttons.push(`<button class="btn btn-sm btn-ghost" data-action="edit" data-id="${row.id}">编辑</button>`);
                            }
                            if (canQr) {
                                buttons.push(`<button class="btn btn-sm btn-ghost" data-action="qrcode" data-id="${row.id}">二维码</button>`);
                            }
                            // 删除入口放在详情页，避免列表操作区过挤导致误点
                            return buttons.join('');
                        },
                    },
                ],
                rows,
                empty: '没有符合条件的设备，试试调整筛选条件',
            });

            tableHost.querySelectorAll('button[data-action]').forEach((button) => {
                button.addEventListener('click', () => {
                    const id = button.dataset.id;
                    const action = button.dataset.action;
                    if (action === 'detail') {
                        ctx.navigate(`/equipment/${id}`);
                    } else if (action === 'edit') {
                        ctx.navigate(`/equipment/${id}/edit`);
                    } else if (action === 'qrcode') {
                        ctx.navigate(`/equipment/${id}/qrcode`);
                    }
                });
            });

            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(
                { total: data.total, pageNum: data.pageNum, pageSize: data.pageSize },
                {
                    onChange: (page) => {
                        query.pageNum = page;
                        load();
                    },
                },
            ));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    await loadOptions();
    await load();
}
