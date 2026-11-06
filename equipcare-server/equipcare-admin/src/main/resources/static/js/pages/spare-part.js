/**
 * 备件管理：备件档案列表（编码/名称/分类/规格/单位/库存/单价）+
 * 新增 / 编辑 / 删除 + 分类管理（弹层内增删改）。
 */

import { api, auth } from '../api.js';
import { confirmDialog, esc, formatDate, guard, h, modal, renderPagination, renderTable, toast, toastError } from '../ui.js';
import { categoryOptions, loadCategories, stockCell } from './spare-shared.js';

export async function render(container) {
    const canManage = auth.can('spare:part:manage');

    const tableHost = h('div', {});
    const pagerHost = h('div', {});
    const summary = h('span', { class: 'hint' });
    const keywordInput = h('input', { class: 'input', placeholder: '编码 / 名称 / 规格', style: 'width:200px' });
    const categorySelect = h('select', { class: 'input' });
    const statusSelect = h('select', { class: 'input' },
        h('option', { value: '', text: '全部状态' }),
        h('option', { value: '1', text: '启用' }),
        h('option', { value: '0', text: '停用' }));
    const belowOnly = h('input', { type: 'checkbox', id: 'below-only-chk' });

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '备件管理' }),
                h('p', { text: '备件档案与分类。库存数量只能通过入库 / 出库 / 盘点调整变动，不允许直接改数。' })),
            h('div', { class: 'page-actions' },
                canManage ? h('button', { class: 'btn btn-ghost', type: 'button', text: '分类管理', onclick: () => openCategoryManager() }) : null,
                canManage ? h('button', { class: 'btn btn-primary', type: 'button', text: '＋ 新增备件', onclick: () => openForm(null) }) : null)),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: '备件档案' }),
                h('div', { class: 'toolbar' },
                    keywordInput,
                    categorySelect,
                    statusSelect,
                    h('label', { class: 'check-line', for: 'below-only-chk' }, belowOnly, ' 只看低于安全库存'),
                    h('button', { class: 'btn btn-primary btn-sm', type: 'button', text: '查询', onclick: () => load(1) }))),
            tableHost,
            h('div', { class: 'card-foot' }, pagerHost)));

    let categories = [];
    let pagerMeta = null;

    await load(1);

    async function load(pageNum) {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        const params = new URLSearchParams({
            pageNum: String(pageNum || 1), pageSize: '10',
        });
        if (keywordInput.value.trim()) params.set('keyword', keywordInput.value.trim());
        if (categorySelect.value) params.set('categoryId', categorySelect.value);
        if (statusSelect.value) params.set('status', statusSelect.value);
        if (belowOnly.checked) params.set('belowSafety', 'true');
        try {
            const page = await api.get('/api/inventory/parts?' + params.toString());
            pagerMeta = { pageNum: page.pageNum, pageSize: page.pageSize, total: page.total };
            renderRows(page.records || []);
            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(pagerMeta, { onChange: load }));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function renderRows(records) {
        summary.textContent = `共 ${pagerMeta ? pagerMeta.total : 0} 个备件`;
        tableHost.innerHTML = '';
        tableHost.innerHTML = renderTable({
            columns: [
                { title: '编码', render: r => `<span class="mono">${esc(r.partNo)}</span>` },
                { title: '名称', render: r => esc(r.partName) },
                { title: '分类', render: r => esc(r.category || '—') },
                { title: '规格型号', render: r => esc(r.spec || '—') },
                { title: '单位', render: r => esc(r.unit) },
                { title: '库存 / 安全', render: r => stockCell(r) },
                { title: '单价', render: r => `¥${Number(r.unitPrice).toFixed(2)}` },
                { title: '库位', render: r => esc(r.warehouseLocation || '—') },
                { title: '操作', render: r => canManage
                    ? `<button class="btn btn-sm btn-ghost" data-action="edit" data-id="${r.id}">编辑</button>
                       <button class="btn btn-sm btn-danger" data-action="delete" data-id="${r.id}">删除</button>`
                    : '—' },
            ],
            rows: records,
            empty: '暂无备件，点右上角「新增备件」建档',
        });
        tableHost.querySelectorAll('button[data-action]').forEach(btn => {
            const id = Number(btn.dataset.id);
            const part = records.find(p => p.id === id);
            btn.addEventListener('click', () =>
                btn.dataset.action === 'edit' ? openForm(part) : openDelete(part));
        });
    }

    async function refreshCategories() {
        categories = await loadCategories();
        categorySelect.innerHTML = categoryOptions(categories, categorySelect.value);
    }

    await refreshCategories();
    keywordInput.addEventListener('keydown', e => { if (e.key === 'Enter') load(1); });
    categorySelect.addEventListener('change', () => load(1));
    statusSelect.addEventListener('change', () => load(1));
    belowOnly.addEventListener('change', () => load(1));

    // ------------------------------------------------------------------
    // 新增 / 编辑备件
    // ------------------------------------------------------------------
    function openForm(part) {
        const isEdit = !!part;
        const body = h('form', { class: 'form' },
            field('备件编码 *', h('input', { class: 'input', name: 'partNo', value: part ? part.partNo : '', placeholder: '如 BEARING-6208', maxlength: '40' })),
            field('备件名称 *', h('input', { class: 'input', name: 'partName', value: part ? part.partName : '', maxlength: '100' })),
            field('分类', h('select', { class: 'input', name: 'categoryId' },
                `<option value="">未分类</option>${categories.map(c =>
                    `<option value="${c.id}" ${part && part.categoryId === c.id ? 'selected' : ''}>${esc(c.categoryName)}</option>`).join('')}`)),
            field('规格型号', h('input', { class: 'input', name: 'spec', value: part ? (part.spec || '') : '', maxlength: '120' })),
            field('单位 *', h('input', { class: 'input', name: 'unit', value: part ? part.unit : '个', maxlength: '20' })),
            field('品牌', h('input', { class: 'input', name: 'brand', value: part ? (part.brand || '') : '', maxlength: '60' })),
            field('安全库存', h('input', { class: 'input', type: 'number', name: 'safetyStock', min: '0', value: part ? part.safetyStock : '0' })),
            field('单价（元）', h('input', { class: 'input', type: 'number', name: 'unitPrice', min: '0', step: '0.01', value: part ? part.unitPrice : '0.00' })),
            field('库位', h('input', { class: 'input', name: 'warehouseLocation', value: part ? (part.warehouseLocation || '') : '', maxlength: '60' })),
            field('供应商', h('input', { class: 'input', name: 'supplier', value: part ? (part.supplier || '') : '', maxlength: '100' })),
            isEdit ? h('div', { class: 'hint', text: '库存数量不在此修改：请用「库存管理」的入库 / 出库 / 盘点调整，所有变动都会生成流水。' }) : null);

        const dialog = modal({
            title: isEdit ? `编辑备件：${part.partName}` : '新增备件',
            body,
            footer: [
                h('button', { class: 'btn btn-ghost', type: 'button', text: '取消', onclick: () => dialog.close() }),
                h('button', { class: 'btn btn-primary', type: 'button', text: '保存' }),
            ],
        });
        dialog.footer.querySelector('.btn-primary').addEventListener('click', guard(async () => {
            const data = formValues(body);
            if (!data.categoryId) delete data.categoryId;
            if (isEdit) {
                await api.put(`/api/inventory/parts/${part.id}`, data);
            } else {
                await api.post('/api/inventory/parts', data);
            }
            toast('已保存');
            dialog.close();
            await refreshCategories();
            await load(1);
        }));
    }

    async function openDelete(part) {
        const ok = await confirmDialog({
            title: '删除备件',
            message: `确认删除备件「${part.partName}」（${part.partNo}）？存在待审批领用或仍有库存时会被拒绝。`,
            danger: true,
        });
        if (!ok) return;
        try {
            await api.delete(`/api/inventory/parts/${part.id}`);
            toast('已删除');
            await load(1);
        } catch (error) {
            toastError(error);
        }
    }

    // ------------------------------------------------------------------
    // 分类管理（弹层）
    // ------------------------------------------------------------------
    async function openCategoryManager() {
        const listHost = h('div', { class: 'stack' });
        const dialog = modal({
            title: '备件分类管理',
            body: h('div', {}, listHost, h('div', { class: 'hint', text: '分类下仍有备件时不可删除；重命名会同步备件档案上的分类快照。' })),
            footer: [h('button', { class: 'btn btn-primary', type: 'button', text: '＋ 新增分类' })],
        });
        const addBtn = dialog.footer.querySelector('.btn-primary');
        addBtn.addEventListener('click', () => openCategoryForm(null, reload));

        async function reload() {
            const list = await api.get('/api/inventory/categories');
            listHost.innerHTML = '';
            listHost.append(...list.map(c => h('div', { class: 'stack-row' },
                h('div', { class: 'stack-head' },
                    h('strong', { text: c.categoryName }),
                    h('span', { class: 'cell-sub', text: `${c.partCount} 个备件` })),
                c.remark ? h('div', { class: 'cell-sub', text: c.remark }) : null,
                h('div', { style: 'margin-top:6px' },
                    h('button', { class: 'btn btn-sm btn-ghost', type: 'button', text: '编辑', onclick: () => openCategoryForm(c, reload) }),
                    h('button', {
                        class: 'btn btn-sm btn-danger', type: 'button', text: '删除',
                        onclick: async () => {
                            const ok = await confirmDialog({ title: '删除分类', message: `确认删除分类「${c.categoryName}」？`, danger: true });
                            if (!ok) return;
                            try {
                                await api.delete(`/api/inventory/categories/${c.id}`);
                                toast('已删除');
                                await reload();
                                await refreshCategories();
                                await load(1);
                            } catch (error) {
                                toastError(error);
                            }
                        },
                    })))));
        }
        await reload();
    }

    function openCategoryForm(category, onSaved) {
        const body = h('form', { class: 'form' },
            field('分类名称 *', h('input', { class: 'input', name: 'categoryName', value: category ? category.categoryName : '', maxlength: '50' })),
            field('排序（小在前）', h('input', { class: 'input', type: 'number', name: 'sort', value: category ? (category.sort || 0) : '0' })),
            field('说明', h('input', { class: 'input', name: 'remark', value: category ? (category.remark || '') : '', maxlength: '200' })));
        const dialog = modal({
            title: category ? `编辑分类：${category.categoryName}` : '新增分类',
            body,
            footer: [
                h('button', { class: 'btn btn-ghost', type: 'button', text: '取消', onclick: () => dialog.close() }),
                h('button', { class: 'btn btn-primary', type: 'button', text: '保存' }),
            ],
        });
        dialog.footer.querySelector('.btn-primary').addEventListener('click', guard(async () => {
            const data = formValues(body);
            if (category) {
                await api.put(`/api/inventory/categories/${category.id}`, data);
            } else {
                await api.post('/api/inventory/categories', data);
            }
            toast('已保存');
            dialog.close();
            onSaved();
        }));
    }
}

function field(label, control) {
    return h('div', { class: 'field' }, h('label', { text: label }), control);
}

function formValues(form) {
    const data = {};
    form.querySelectorAll('[name]').forEach(input => {
        data[input.name] = input.type === 'number' ? (input.value === '' ? null : Number(input.value)) : input.value;
    });
    return data;
}
