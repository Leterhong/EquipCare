/**
 * 库存管理：库存视角的备件列表（当前库存 / 安全库存 / 库存价值）+
 * 盘点调整（盘盈 / 盘亏）。入库、出库、流水、预警是独立页面，顶部互跳。
 */

import { api, auth } from '../api.js';
import { esc, guard, h, modal, renderPagination, renderTable, toast, toastError } from '../ui.js';
import { categoryOptions, loadCategories, navLinks, stockCell } from './spare-shared.js';

export async function render(container, ctx) {
    const canAdjust = auth.can('spare:stock:adjust');

    const tableHost = h('div', {});
    const pagerHost = h('div', {});
    const summary = h('span', { class: 'hint' });
    const keywordInput = h('input', { class: 'input', placeholder: '编码 / 名称', style: 'width:180px' });
    const categorySelect = h('select', { class: 'input' });
    const belowOnly = h('input', { type: 'checkbox', id: 'stock-below-chk' });

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '库存管理' }),
                h('p', { text: '库存数量只能通过入库 / 出库 / 盘点调整变动，每笔变动都写流水、可追溯。' })),
            h('div', { class: 'page-actions' },
                navLinks(ctx, '/spare/stock'))),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: '库存总览' }),
                h('div', { class: 'toolbar' },
                    keywordInput,
                    categorySelect,
                    h('label', { class: 'check-line', for: 'stock-below-chk' }, belowOnly, ' 只看预警'),
                    h('button', { class: 'btn btn-primary btn-sm', type: 'button', text: '查询', onclick: () => load(1) }))),
            tableHost,
            h('div', { class: 'card-foot' }, pagerHost)));

    let pagerMeta = null;

    async function load(pageNum) {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        const params = new URLSearchParams({ pageNum: String(pageNum || 1), pageSize: '10' });
        if (keywordInput.value.trim()) params.set('keyword', keywordInput.value.trim());
        if (categorySelect.value) params.set('categoryId', categorySelect.value);
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
        const totalValue = records.reduce((sum, r) => sum + Number(r.unitPrice) * Number(r.currentStock), 0);
        summary.textContent = `共 ${pagerMeta ? pagerMeta.total : 0} 个备件 · 本页库存价值 ¥${totalValue.toFixed(2)}`;
        tableHost.innerHTML = '';
        tableHost.innerHTML = renderTable({
            columns: [
                { title: '编码', render: r => `<span class="mono">${esc(r.partNo)}</span>` },
                { title: '名称', render: r => esc(r.partName) },
                { title: '分类', render: r => esc(r.category || '—') },
                { title: '库存 / 安全', render: r => stockCell(r) },
                { title: '单价', render: r => `¥${Number(r.unitPrice).toFixed(2)}` },
                { title: '库存价值', render: r => `¥${(Number(r.unitPrice) * Number(r.currentStock)).toFixed(2)}` },
                { title: '库位', render: r => esc(r.warehouseLocation || '—') },
                { title: '操作', render: r => canAdjust
                    ? `<button class="btn btn-sm btn-ghost" data-action="adjust" data-id="${r.id}">盘点调整</button>
                       <button class="btn btn-sm btn-ghost" data-action="txn" data-id="${r.id}">流水</button>`
                    : `<button class="btn btn-sm btn-ghost" data-action="txn" data-id="${r.id}">流水</button>` },
            ],
            rows: records,
            rowAttrs: (r) => (Number(r.currentStock) < Number(r.safetyStock) ? 'class="row-below-safety"' : ''),
            empty: '暂无备件，请先到「备件管理」建档',
        });
        tableHost.querySelectorAll('button[data-action]').forEach(btn => {
            const part = records.find(p => p.id === Number(btn.dataset.id));
            if (btn.dataset.action === 'adjust') {
                btn.addEventListener('click', () => openAdjust(part, () => load(1)));
            } else {
                btn.addEventListener('click', () => ctx.navigate('/spare/txn?partId=' + part.id));
            }
        });
    }

    async function refreshCategories() {
        const categories = await loadCategories();
        categorySelect.innerHTML = categoryOptions(categories, categorySelect.value);
    }
    await refreshCategories();
    categorySelect.addEventListener('change', () => load(1));
    belowOnly.addEventListener('change', () => load(1));

    await load(1);
}

/**
 * 盘点调整弹层：positive=true 盘盈回增，false 盘亏扣减（超过库存会被 4001 拒绝）。
 */
export function openAdjust(part, onDone) {
    const body = h('form', { class: 'form' },
        h('div', { class: 'hint', text: `备件：${part.partName}（${part.partNo}），当前库存 ${part.currentStock} ${part.unit}，安全库存 ${part.safetyStock}。` }),
        h('div', { class: 'field' },
            h('label', { text: '调整方向 *' }),
            h('select', { class: 'input', name: 'positive' },
                h('option', { value: 'true', text: '盘盈（回增库存）' }),
                h('option', { value: 'false', text: '盘亏（扣减库存）' }))),
        h('div', { class: 'field' },
            h('label', { text: '调整数量 *' }),
            h('input', { class: 'input', type: 'number', name: 'quantity', min: '1', value: '1' })),
        h('div', { class: 'field' },
            h('label', { text: '调整原因 / 备注' }),
            h('input', { class: 'input', name: 'remark', maxlength: '200', placeholder: '如：月度盘点差异' })));
    const dialog = modal({
        title: `盘点调整：${part.partName}`,
        body,
        footer: [
            h('button', { class: 'btn btn-ghost', type: 'button', text: '取消', onclick: () => dialog.close() }),
            h('button', { class: 'btn btn-primary', type: 'button', text: '确认调整' }),
        ],
    });
    dialog.footer.querySelector('.btn-primary').addEventListener('click', guard(async () => {
        const positive = body.querySelector('[name=positive]').value === 'true';
        const quantity = Number(body.querySelector('[name=quantity]').value);
        const remark = body.querySelector('[name=remark]').value;
        if (!quantity || quantity <= 0) {
            toast('请输入正确的调整数量', 'error');
            return;
        }
        const params = new URLSearchParams({
            partId: String(part.id), quantity: String(quantity), positive: String(positive),
        });
        if (remark) params.set('remark', remark);
        await api.post('/api/inventory/stock/adjust?' + params.toString());
        toast('调整完成，已生成流水');
        dialog.close();
        if (onDone) onDone();
    }));
}
