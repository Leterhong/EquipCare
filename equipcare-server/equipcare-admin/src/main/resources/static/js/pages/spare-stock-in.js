/**
 * 入库页面：采购 / 退料回补入库。入库后立即生成库存流水。
 */

import { api, auth } from '../api.js';
import { esc, guard, h, toast, toastError } from '../ui.js';
import { loadParts, navLinks, partPicker } from './spare-shared.js';

export async function render(container, ctx) {
    const canIn = auth.can('spare:stock:in');
    const formHost = h('div', {});
    const resultHost = h('div', {});

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '备件入库' }),
                h('p', { text: '入库会生成 IN 类型库存流水，记录变动前后库存与单价快照。' })),
            h('div', { class: 'page-actions' }, navLinks(ctx, '/spare/stock/in'))),
        h('div', { class: 'grid-2' },
            h('div', { class: 'card no-stretch' },
                h('div', { class: 'card-head' }, h('h2', { text: '入库单' })),
                h('div', { class: 'card-body' }, formHost)),
            h('div', { class: 'card' },
                h('div', { class: 'card-head' }, h('h2', { text: '最近入库记录' })),
                h('div', { class: 'card-body' }, resultHost))));

    if (!canIn) {
        formHost.append(h('div', { class: 'empty-state', text: '没有 spare:stock:in 权限，无法入库' }));
        return;
    }

    let parts = [];
    try {
        parts = await loadParts();
    } catch (error) {
        toastError(error);
        return;
    }
    const picker = partPicker(parts);

    const form = h('form', { class: 'form' },
        h('div', { class: 'field' }, h('label', { text: '备件 *' }), picker.node),
        h('div', { class: 'field' },
            h('label', { text: '入库数量 *' }),
            h('input', { class: 'input', type: 'number', name: 'quantity', min: '1', value: '1' })),
        h('div', { class: 'field' },
            h('label', { text: '入库单价（元，留空用档案单价）' }),
            h('input', { class: 'input', type: 'number', name: 'unitPrice', min: '0', step: '0.01', placeholder: '—' })),
        h('div', { class: 'field' },
            h('label', { text: '备注' }),
            h('input', { class: 'input', name: 'remark', maxlength: '200', placeholder: '如：采购单 PO-2026-001 到货' })),
        h('div', { class: 'form-actions' },
            h('button', { class: 'btn btn-primary', type: 'button', text: '确认入库' })));

    formHost.append(form);
    form.querySelector('.btn-primary').addEventListener('click', guard(async () => {
        const partId = picker.select.value;
        const quantity = Number(form.querySelector('[name=quantity]').value);
        if (!partId) { toast('请选择备件', 'error'); return; }
        if (!quantity || quantity <= 0) { toast('请输入正确的入库数量', 'error'); return; }
        const data = { partId: Number(partId), quantity };
        const unitPrice = form.querySelector('[name=unitPrice]').value;
        if (unitPrice) data.unitPrice = Number(unitPrice);
        const remark = form.querySelector('[name=remark]').value;
        if (remark) data.remark = remark;
        const txn = await api.post('/api/inventory/stock/in', data);
        toast(`入库成功，流水号 ${txn.txnNo}`);
        form.querySelector('[name=quantity]').value = '1';
        await loadRecent();
    }));

    async function loadRecent() {
        resultHost.innerHTML = '<div class="skeleton" style="width:60%"></div>';
        try {
            const page = await api.get('/api/inventory/stock/txns/page?pageNum=1&pageSize=8&txnType=IN');
            const records = page.records || [];
            resultHost.innerHTML = records.length ? '' : '<div class="table-empty">暂无入库记录</div>';
            resultHost.append(...records.map(t => h('div', { class: 'stack-row' },
                h('div', { class: 'stack-head' },
                    h('strong', { text: t.partName || `备件 #${t.partId}` }),
                    h('span', { class: 'cell-sub mono', text: t.txnNo })),
                h('div', { class: 'cell-sub' },
                    `+${t.quantity} → 库存 ${t.afterStock} · ¥${Number(t.amount || 0).toFixed(2)} · ${esc(t.remark || '')}`))));
        } catch (error) {
            resultHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    await loadRecent();
}
