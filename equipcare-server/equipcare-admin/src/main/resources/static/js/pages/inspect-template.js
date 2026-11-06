/**
 * 巡检模板管理。
 *
 * 模板 = 一组「检查项 + 检查标准 + 判定规则」。这里负责：
 *  1. 列表分页 / 关键字 / 分类 / 状态筛选；
 *  2. 编辑器里维护检查项顺序（提交时按当前顺序重排 seqNo，服务端以提交顺序为准）；
 *  3. 发布前把「能存进去但现场没法判定」的配置在提交前先挡一次，
 *     服务端也会再校验一遍（以服务端为准）。
 */

import { api, auth } from '../api.js';
import {
    ITEM_TYPE, TEMPLATE_STATUS, confirmDialog, debounce, esc, formatDate, guard, h,
    modal, renderPagination, renderTable, statusTag, toast, toastError,
} from '../ui.js';

const LEVELS = [
    { value: 'CRITICAL', label: '紧急（P1）' },
    { value: 'HIGH', label: '严重（P2）' },
    { value: 'MEDIUM', label: '一般（P3）' },
    { value: 'LOW', label: '轻微（P4）' },
];

let categoryCache = null;

async function loadCategories() {
    if (categoryCache) {
        return categoryCache;
    }
    try {
        categoryCache = await api.get('/api/asset/categories/tree');
    } catch (error) {
        categoryCache = [];
    }
    return categoryCache;
}

function flattenCategories(nodes, level = 0, result = []) {
    (nodes || []).forEach((node) => {
        result.push({ id: node.id, name: node.categoryName || node.name, level });
        if (node.children && node.children.length) {
            flattenCategories(node.children, level + 1, result);
        }
    });
    return result;
}

/** 新检查项的默认值，保证编辑器里每个字段都有可用的初始状态 */
function blankItem() {
    return {
        itemName: '',
        itemType: 'BOOLEAN',
        unit: '',
        minValue: '',
        maxValue: '',
        normalValue: '1',
        enumOptions: '["正常","异常"]',
        required: 1,
        checkMethod: '',
        standardRef: '',
        abnormalRule: '',
        autoWorkOrder: 0,
        photoRequired: 0,
        abnormalLevel: 'MEDIUM',
    };
}

export async function render(container, ctx) {
    const canManage = auth.can('inspect:template:manage');
    const query = { pageNum: 1, pageSize: 10, keyword: '', categoryId: '', status: '' };

    const categories = flattenCategories(await loadCategories());

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '巡检模板' }),
            h('p', { text: '一个模板对应一类设备的点检要求：检查项、检查标准、异常判定规则与证据要求。发布后才能被巡检计划引用。' })),
        h('div', { class: 'page-actions' },
            canManage ? h('button', {
                class: 'btn btn-primary', type: 'button', text: '＋ 新建模板',
                onclick: () => openEditor(null),
            }) : null,
        ));

    const keywordInput = h('input', {
        class: 'input', type: 'search', placeholder: '模板名称 / 编码', 'aria-label': '关键字',
    });
    const categorySelect = h('select', { class: 'select', 'aria-label': '适用分类' },
        h('option', { value: '', text: '全部分类' }),
        ...categories.map((item) => h('option', {
            value: item.id,
            text: `${'　'.repeat(item.level)}${item.name}`,
        })));
    const statusSelect = h('select', { class: 'select', 'aria-label': '状态' },
        h('option', { value: '', text: '全部状态' }),
        ...Object.entries(TEMPLATE_STATUS).map(([code, item]) => h('option', { value: code, text: item.label })));

    const toolbar = h('div', { class: 'toolbar' },
        h('div', { class: 'field grow' }, h('label', { text: '关键字' }), keywordInput),
        h('div', { class: 'field' }, h('label', { text: '适用分类' }), categorySelect),
        h('div', { class: 'field' }, h('label', { text: '状态' }), statusSelect),
        h('div', { class: 'spacer' }),
        h('button', { class: 'btn', type: 'button', text: '重置', onclick: () => resetQuery() }),
        h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: () => load(1) }),
    );

    const summary = h('span', { class: 'hint', text: '' });
    const tableHost = h('div', {});
    const pagerHost = h('div', {});

    container.append(head, h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '模板清单' }), summary),
        toolbar, tableHost, pagerHost));

    keywordInput.addEventListener('input', debounce(() => load(1), 400));
    [categorySelect, statusSelect].forEach((select) => select.addEventListener('change', () => load(1)));

    function resetQuery() {
        keywordInput.value = '';
        categorySelect.value = '';
        statusSelect.value = '';
        load(1);
    }

    async function load(pageNum = query.pageNum) {
        query.pageNum = pageNum;
        query.keyword = keywordInput.value.trim();
        query.categoryId = categorySelect.value;
        query.status = statusSelect.value;

        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:55%"></div></div>';
        try {
            const data = await api.get('/api/inspection/templates', query);
            const rows = data.records || data.list || [];
            summary.textContent = `共 ${data.total} 个模板`;

            tableHost.innerHTML = renderTable({
                columns: [
                    {
                        title: '模板编码', key: 'templateCode', width: '150px',
                        render: (row) => `<span class="mono">${esc(row.templateCode)}</span>`,
                    },
                    { title: '模板名称', key: 'templateName' },
                    { title: '适用分类', key: 'categoryName', width: '130px', render: (row) => esc(row.categoryName || '通用') },
                    { title: '检查项', key: 'itemCount', width: '72px', align: 'right', render: (row) => esc(row.itemCount ?? 0) },
                    { title: '版本', key: 'version', width: '64px', align: 'right', render: (row) => `v${esc(row.version ?? 1)}` },
                    { title: '状态', key: 'status', width: '90px', render: (row) => statusTag(TEMPLATE_STATUS, row.status) },
                    {
                        title: '被引用', key: 'planCount', width: '78px', align: 'right',
                        render: (row) => (row.planCount > 0
                            ? `<span style="color:var(--primary)">${row.planCount} 个计划</span>`
                            : '<span style="color:var(--text-muted)">—</span>'),
                    },
                    { title: '更新时间', key: 'updateTime', width: '110px', render: (row) => formatDate(row.updateTime, true) },
                    {
                        title: '操作', key: 'id', width: '230px', align: 'right',
                        render: (row) => {
                            const buttons = [`<button class="btn btn-sm btn-ghost" data-action="view" data-id="${row.id}">查看</button>`];
                            if (canManage) {
                                buttons.push(`<button class="btn btn-sm btn-ghost" data-action="edit" data-id="${row.id}">编辑</button>`);
                                if (row.status !== 'PUBLISHED') {
                                    buttons.push(`<button class="btn btn-sm btn-ghost" data-action="publish" data-id="${row.id}">发布</button>`);
                                } else {
                                    buttons.push(`<button class="btn btn-sm btn-ghost" data-action="disable" data-id="${row.id}">停用</button>`);
                                }
                                buttons.push(`<button class="btn btn-sm btn-ghost danger" data-action="delete" data-id="${row.id}">删除</button>`);
                            }
                            return buttons.join('');
                        },
                    },
                ],
                rows,
                empty: '还没有巡检模板，先新建一个再发布',
            });

            tableHost.querySelectorAll('button[data-action]').forEach((button) => {
                button.addEventListener('click', () => handleAction(button.dataset.action, Number(button.dataset.id)));
            });

            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(data, { onChange: (page) => load(page) }));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    async function handleAction(action, id) {
        if (action === 'view' || action === 'edit') {
            openEditor(id, { readonly: action === 'view' });
            return;
        }
        if (action === 'publish') {
            try {
                await api.post(`/api/inspection/templates/${id}/publish`);
                toast('模板已发布', 'success');
                load();
            } catch (error) {
                toastError(error);
            }
            return;
        }
        if (action === 'disable') {
            const ok = await confirmDialog({
                title: '停用模板',
                message: '停用后已有计划仍保留历史记录，但不能再用该模板新建计划。确定停用？',
                confirmText: '停用',
            });
            if (!ok) {
                return;
            }
            try {
                await api.post(`/api/inspection/templates/${id}/disable`);
                toast('模板已停用', 'success');
                load();
            } catch (error) {
                toastError(error);
            }
            return;
        }
        if (action === 'delete') {
            const ok = await confirmDialog({
                title: '删除模板',
                message: '删除后不可恢复。若模板已被巡检计划引用，系统会拒绝删除，请先停用相关计划。',
                confirmText: '删除',
                danger: true,
            });
            if (!ok) {
                return;
            }
            try {
                await api.del(`/api/inspection/templates/${id}`);
                toast('模板已删除', 'success');
                load();
            } catch (error) {
                toastError(error);
            }
        }
    }

    /* ---------------------------- 编辑器 ---------------------------- */

    async function openEditor(id, { readonly = false } = {}) {
        let detail = null;
        if (id) {
            try {
                detail = await api.get(`/api/inspection/templates/${id}`);
            } catch (error) {
                toastError(error);
                return;
            }
        }

        const state = {
            items: (detail && detail.items ? detail.items : []).map((item) => ({
                itemName: item.itemName || '',
                itemType: item.itemType === 'BOOL' ? 'BOOLEAN' : (item.itemType || 'BOOLEAN'),
                unit: item.unit || '',
                minValue: item.minValue ?? '',
                maxValue: item.maxValue ?? '',
                normalValue: item.normalValue ?? '1',
                enumOptions: item.enumOptions || '["正常","异常"]',
                required: item.required ?? 1,
                checkMethod: item.checkMethod || '',
                standardRef: item.standardRef || '',
                abnormalRule: item.abnormalRule || '',
                autoWorkOrder: item.autoWorkOrder ?? 0,
                photoRequired: item.photoRequired ?? 0,
                abnormalLevel: item.abnormalLevel || 'MEDIUM',
            })),
        };
        if (!state.items.length) {
            state.items.push(blankItem());
        }

        const codeInput = h('input', {
            class: 'input mono', type: 'text', name: 'templateCode', value: detail ? detail.templateCode : '',
            placeholder: '留空自动生成，如 TPL-PUMP-01', maxlength: '40', disabled: !!detail || readonly,
        });
        const nameInput = h('input', {
            class: 'input', type: 'text', name: 'templateName', value: detail ? detail.templateName : '',
            placeholder: '如 工业水泵日常巡检', maxlength: '60', disabled: readonly,
        });
        const categorySelect2 = h('select', { class: 'select', name: 'categoryId', disabled: readonly },
            h('option', { value: '', text: '通用（不限分类）' }),
            ...categories.map((item) => h('option', {
                value: item.id,
                text: `${'　'.repeat(item.level)}${item.name}`,
                selected: detail && String(detail.categoryId) === String(item.id),
            })));
        const descInput = h('textarea', {
            class: 'input', rows: '2', name: 'description',
            placeholder: '这份模板用来检什么、适用哪些设备', disabled: readonly,
        });
        descInput.value = detail ? (detail.description || '') : '';

        const itemsHost = h('div', { class: 'tpl-items' });
        const itemsSummary = h('span', { class: 'hint', text: '' });

        const body = h('div', {},
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { text: '模板编码' }), codeInput),
                h('div', { class: 'field' }, h('label', { text: '模板名称' }), nameInput),
                h('div', { class: 'field' }, h('label', { text: '适用设备分类' }), categorySelect2),
                h('div', { class: 'field grow' }, h('label', { text: '巡检说明' }), descInput)),
            h('div', { class: 'tpl-items-head' },
                h('div', {},
                    h('h4', { text: '巡检项目' }),
                    h('span', { class: 'hint', text: '按检查顺序排列；执行页会按此顺序逐项展开。' })),
                itemsSummary,
                readonly ? null : h('button', {
                    class: 'btn btn-sm btn-primary', type: 'button', text: '＋ 添加检查项',
                    onclick: () => {
                        state.items.push(blankItem());
                        renderItems();
                    },
                })),
            itemsHost);

        const dialog = modal({
            title: readonly ? '模板详情' : (detail ? '编辑模板' : '新建模板'),
            width: 'wide',
            body,
            footer: readonly
                ? [{ label: '关闭' }]
                : [{
                    label: '取消',
                }, {
                    label: '保存',
                    type: 'primary',
                    close: false,
                    onClick: async ({ button }) => {
                        const payload = collect(detail);
                        if (!payload) {
                            return false;
                        }
                        await guard(button, async () => {
                            if (detail) {
                                await api.put(`/api/inspection/templates/${detail.id}`, payload);
                                toast('模板已更新', 'success');
                            } else {
                                await api.post('/api/inspection/templates', payload);
                                toast('模板已创建，发布后才能被计划引用', 'success');
                            }
                            dialog.close();
                            load();
                        });
                        return false;
                    },
                }],
        });

        function itemCard(item, index) {
            const type = item.itemType;
            const typeFields = [];
            if (type === 'NUMBER') {
                typeFields.push(
                    h('div', { class: 'field' }, h('label', { text: '单位' }),
                        h('input', {
                            class: 'input', value: item.unit, placeholder: '℃ / MPa / mm',
                            'data-field': 'unit', 'data-index': index, disabled: readonly,
                        })),
                    h('div', { class: 'field' }, h('label', { text: '下限' }),
                        h('input', {
                            class: 'input', type: 'number', step: 'any', value: item.minValue,
                            'data-field': 'minValue', 'data-index': index, disabled: readonly,
                        })),
                    h('div', { class: 'field' }, h('label', { text: '上限' }),
                        h('input', {
                            class: 'input', type: 'number', step: 'any', value: item.maxValue,
                            'data-field': 'maxValue', 'data-index': index, disabled: readonly,
                        })));
            } else if (type === 'ENUM') {
                typeFields.push(h('div', { class: 'field grow' }, h('label', { text: '可选值（逗号分隔）' }),
                    h('input', {
                        class: 'input', value: enumToText(item.enumOptions),
                        placeholder: '正常,异响,噪声过大',
                        'data-field': 'enumText', 'data-index': index, disabled: readonly,
                    })));
            } else if (type === 'BOOLEAN') {
                typeFields.push(h('div', { class: 'field' }, h('label', { text: '正常值' }),
                    h('select', { class: 'select', 'data-field': 'normalValue', 'data-index': index, disabled: readonly },
                        h('option', { value: '1', text: '是', selected: String(item.normalValue) === '1' }),
                        h('option', { value: '0', text: '否', selected: String(item.normalValue) === '0' }))));
            }

            return h('div', { class: 'tpl-item' },
                h('div', { class: 'tpl-item-head' },
                    h('span', { class: 'tpl-item-seq', text: String(index + 1) }),
                    h('input', {
                        class: 'input tpl-item-name', value: item.itemName, maxlength: '60',
                        placeholder: '检查项名称，如 检查设备外壳温度', name: 'itemName',
                        'data-field': 'itemName', 'data-index': index, disabled: readonly,
                    }),
                    h('select', { class: 'select', style: 'width:110px', 'data-field': 'itemType', 'data-index': index, disabled: readonly },
                        ...Object.entries(ITEM_TYPE).map(([code, meta]) => h('option', {
                            value: code, text: meta.label, selected: code === type,
                        }))),
                    readonly ? null : h('div', { class: 'tpl-item-tools' },
                        h('button', {
                            class: 'icon-btn', type: 'button', title: '上移', 'aria-label': '上移',
                            disabled: index === 0, onclick: () => moveItem(index, -1),
                        }, '↑'),
                        h('button', {
                            class: 'icon-btn', type: 'button', title: '下移', 'aria-label': '下移',
                            disabled: index === state.items.length - 1, onclick: () => moveItem(index, 1),
                        }, '↓'),
                        h('button', {
                            class: 'icon-btn danger', type: 'button', title: '删除', 'aria-label': '删除',
                            onclick: () => removeItem(index),
                        }, '×'))),
                h('div', { class: 'form-grid' },
                    typeFields,
                    h('div', { class: 'field' }, h('label', { text: '异常等级' }),
                        h('select', { class: 'select', 'data-field': 'abnormalLevel', 'data-index': index, disabled: readonly },
                            ...LEVELS.map((level) => h('option', {
                                value: level.value, text: level.label, selected: level.value === item.abnormalLevel,
                            })))),
                    h('div', { class: 'field' }, h('label', { text: '检查方法' }),
                        h('input', {
                            class: 'input', value: item.checkMethod, placeholder: '目视 / 红外测温 / 听诊',
                            'data-field': 'checkMethod', 'data-index': index, disabled: readonly,
                        })),
                    h('div', { class: 'field' }, h('label', { text: '检查标准' }),
                        h('input', {
                            class: 'input', value: item.standardRef, placeholder: 'GB/T 29529-2013 或作业规程条款',
                            'data-field': 'standardRef', 'data-index': index, disabled: readonly,
                        })),
                    h('div', { class: 'field grow' }, h('label', { text: '异常判定规则' }),
                        h('input', {
                            class: 'input', value: item.abnormalRule, placeholder: '什么情况算异常，便于现场判断一致',
                            'data-field': 'abnormalRule', 'data-index': index, disabled: readonly,
                        }))),
                h('div', { class: 'tpl-item-flags' },
                    flag('必检项（不能跳过）', 'required', item.required, index, readonly),
                    flag('异常需现场照片', 'photoRequired', item.photoRequired, index, readonly),
                    flag('异常自动建工单', 'autoWorkOrder', item.autoWorkOrder, index, readonly)));
        }

        function flag(label, field, value, index, readonly) {
            const input = h('input', {
                type: 'checkbox', 'data-field': field, 'data-index': index, disabled: readonly,
            });
            input.checked = Number(value) === 1;
            return h('label', { class: 'check-line compact' }, input, h('span', { text: label }));
        }

        function renderItems() {
            itemsHost.innerHTML = '';
            state.items.forEach((item, index) => itemsHost.append(itemCard(item, index)));
            itemsSummary.textContent = `${state.items.length} 项 · 其中必检 ${state.items.filter((it) => Number(it.required) === 1).length} 项`;
        }

        function moveItem(index, delta) {
            const target = index + delta;
            if (target < 0 || target >= state.items.length) {
                return;
            }
            const [moved] = state.items.splice(index, 1);
            state.items.splice(target, 0, moved);
            renderItems();
        }

        function removeItem(index) {
            state.items.splice(index, 1);
            if (!state.items.length) {
                state.items.push(blankItem());
            }
            renderItems();
        }

        // 事件委托：输入即写回 state，避免每次键入都重建 DOM 丢焦点
        itemsHost.addEventListener('input', (event) => {
            const field = event.target.dataset.field;
            const index = Number(event.target.dataset.index);
            if (field === undefined || Number.isNaN(index) || !state.items[index]) {
                return;
            }
            const item = state.items[index];
            if (field === 'enumText') {
                item.enumOptions = textToEnum(event.target.value);
            } else if (field === 'required' || field === 'photoRequired' || field === 'autoWorkOrder') {
                item[field] = event.target.checked ? 1 : 0;
            } else {
                item[field] = event.target.value;
            }
            if (field === 'itemName' || field === 'required') {
                itemsSummary.textContent = `${state.items.length} 项 · 其中必检 ${state.items.filter((it) => Number(it.required) === 1).length} 项`;
            }
        });
        itemsHost.addEventListener('change', (event) => {
            const field = event.target.dataset.field;
            const index = Number(event.target.dataset.index);
            if (field === 'itemType' && state.items[index]) {
                state.items[index].itemType = event.target.value;
                renderItems();
            }
        });

        function collect(detail) {
            const name = nameInput.value.trim();
            if (!name) {
                toast('请填写模板名称', 'error');
                return null;
            }
            const items = state.items.map((item, index) => ({
                itemName: (item.itemName || '').trim(),
                itemType: item.itemType,
                unit: item.itemType === 'NUMBER' ? (item.unit || null) : null,
                minValue: item.itemType === 'NUMBER' ? toNumber(item.minValue) : null,
                maxValue: item.itemType === 'NUMBER' ? toNumber(item.maxValue) : null,
                normalValue: item.itemType === 'BOOLEAN' ? String(item.normalValue) : null,
                enumOptions: item.itemType === 'ENUM' ? item.enumOptions : null,
                required: Number(item.required) === 1 ? 1 : 0,
                checkMethod: item.checkMethod || null,
                standardRef: item.standardRef || null,
                abnormalRule: item.abnormalRule || null,
                autoWorkOrder: Number(item.autoWorkOrder) === 1 ? 1 : 0,
                photoRequired: Number(item.photoRequired) === 1 ? 1 : 0,
                abnormalLevel: item.abnormalLevel,
                seqNo: index + 1,
            }));

            const unnamed = items.findIndex((item) => !item.itemName);
            if (unnamed >= 0) {
                toast(`第 ${unnamed + 1} 个检查项还没填名称`, 'error');
                return null;
            }
            if (!items.length) {
                toast('至少需要一个检查项', 'error');
                return null;
            }
            return {
                templateCode: (codeInput.value || '').trim() || null,
                templateName: name,
                categoryId: categorySelect2.value ? Number(categorySelect2.value) : null,
                description: descInput.value.trim() || null,
                items,
            };
        }

        renderItems();
    }

    await load(1);
}

/* ------------------------------ 小工具 ------------------------------ */

function toNumber(value) {
    if (value === '' || value === null || value === undefined) {
        return null;
    }
    const num = Number(value);
    return Number.isFinite(num) ? num : null;
}

/** 后端存的是 JSON 字符串，编辑器里用逗号分隔更好输入 */
function enumToText(raw) {
    if (!raw) {
        return '';
    }
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed.join(',') : String(raw);
    } catch (error) {
        return String(raw);
    }
}

function textToEnum(text) {
    const list = String(text || '')
        .split(/[,，\n]/)
        .map((item) => item.trim())
        .filter(Boolean);
    return JSON.stringify(list);
}
