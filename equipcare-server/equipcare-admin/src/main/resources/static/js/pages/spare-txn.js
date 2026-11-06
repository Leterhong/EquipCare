/**
 * 库存流水：全量出入库 / 退料 / 调整记录，支持按备件 / 类型 / 业务 / 时间段过滤。
 * 流水表由数据库触发器保护，只能插入、不能修改删除——追溯的底线。
 */

import { api } from '../api.js';
import { esc, formatDate, h, renderPagination, renderTable } from '../ui.js';
import { loadParts, navLinks, partOptions, txnTypeTag } from './spare-shared.js';

export async function render(container, ctx) {
    const tableHost = h('div', {});
    const pagerHost = h('div', {});
    const summary = h('span', { class: 'hint' });

    const params0 = new URLSearchParams(ctx.query || location.hash.split('?')[1] || '');
    const partSelect = h('select', { class: 'input' });
    const typeSelect = h('select', { class: 'input' },
        h('option', { value: '', text: '全部类型' }),
        h('option', { value: 'IN', text: '入库' }),
        h('option', { value: 'OUT', text: '出库' }),
        h('option', { value: 'RETURN', text: '退料' }),
        h('option', { value: 'ADJUST', text: '调整' }));
    const bizSelect = h('select', { class: 'input' },
        h('option', { value: '', text: '全部业务' }),
        h('option', { value: 'WORK_ORDER', text: '工单领用' }),
        h('option', { value: 'PURCHASE', text: '采购入库' }),
        h('option', { value: 'RETURN', text: '退料' }),
        h('option', { value: 'CHECK', text: '盘点调整' }),
        h('option', { value: 'OTHER', text: '其他出库' }));
    const fromInput = h('input', { class: 'input', type: 'date' });
    const toInput = h('input', { class: 'input', type: 'date' });

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '库存流水' }),
                h('p', { text: 'append-only：流水只能插入、不能修改删除，任何库存变动都可重放追溯。' })),
            h('div', { class: 'page-actions' }, navLinks(ctx, '/spare/txn'))),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: '流水记录' }),
                h('div', { class: 'toolbar' },
                    partSelect, typeSelect, bizSelect, fromInput, toInput,
                    h('button', { class: 'btn btn-primary btn-sm', type: 'button', text: '查询', onclick: () => load(1) }))),
            tableHost,
            h('div', { class: 'card-foot' }, pagerHost)));

    let pagerMeta = null;
    let currentPartId = params0.get('partId') || '';

    try {
        const parts = await loadParts();
        partSelect.innerHTML = partOptions(parts, currentPartId);
    } catch (error) { /* 下拉加载失败不阻断流水列表 */ }
    if (currentPartId) partSelect.value = currentPartId;

    await load(1);

    async function load(pageNum) {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        const params = new URLSearchParams({ pageNum: String(pageNum || 1), pageSize: '15' });
        if (partSelect.value) params.set('partId', partSelect.value);
        if (typeSelect.value) params.set('txnType', typeSelect.value);
        if (bizSelect.value) params.set('bizType', bizSelect.value);
        if (fromInput.value) params.set('timeFrom', fromInput.value + 'T00:00:00');
        if (toInput.value) params.set('timeTo', toInput.value + 'T23:59:59');
        try {
            const page = await api.get('/api/inventory/stock/txns/page?' + params.toString());
            pagerMeta = { pageNum: page.pageNum, pageSize: page.pageSize, total: page.total };
            renderRows(page.records || []);
            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(pagerMeta, { onChange: load }));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function renderRows(records) {
        summary.textContent = `共 ${pagerMeta ? pagerMeta.total : 0} 条流水`;
        tableHost.innerHTML = '';
        tableHost.innerHTML = renderTable({
            columns: [
                { title: '流水号', render: r => `<span class="mono">${esc(r.txnNo)}</span>` },
                { title: '类型', render: r => txnTypeTag(r.txnType) },
                { title: '备件', render: r => esc(r.partName || `#${r.partId}`) },
                { title: '业务', render: r => esc(bizLabel(r.bizType)) },
                { title: '数量', render: r => `<strong>${r.quantity > 0 ? '+' : ''}${r.quantity}</strong>` },
                { title: '库存变化', render: r => `${r.beforeStock} → ${r.afterStock}` },
                { title: '金额', render: r => `¥${Number(r.amount || 0).toFixed(2)}` },
                { title: '备注', render: r => esc(r.remark || '—') },
                { title: '时间', render: r => formatDate(r.txnTime, true) },
            ],
            rows: records,
            empty: '暂无流水',
        });
    }
}

function bizLabel(code) {
    return {
        WORK_ORDER: '工单领用', PURCHASE: '采购入库', RETURN: '退料',
        CHECK: '盘点调整', OTHER: '其他出库',
    }[code] || (code || '—');
}
