/**
 * 出库页面：非工单场景出库（外借 / 报废领用等）。
 * 工单领用出库走「工单详情 → 备件申请 → 审批出库」，不在此操作。
 * 库存不足时后端返回 4001，出库事务回滚、不产生流水。
 */

import { api, auth } from '../api.js';
import { esc, guard, h, toast, toastError } from '../ui.js';
import { loadParts, navLinks, partPicker } from './spare-shared.js';

export async function render(container, ctx) {
    const canOut = auth.can('spare:issue');
    const formHost = h('div', {});
    const resultHost = h('div', {});

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '备件出库' }),
                h('p', { text: '非工单场景出库。扣减是原子操作：库存不足时整笔拒绝（4001），绝不出现负库存。' })),
            h('div', { class: 'page-actions' }, navLinks(ctx, '/spare/stock/out'))),
        h('div', { class: 'grid-2' },
            h('div', { class: 'card no-stretch' },
                h('div', { class: 'card-head' }, h('h2', { text: '出库单' })),
                h('div', { class: 'card-body' }, formHost)),
            h('div', { class: 'card' },
                h('div', { class: 'card-head' }, h('h2', { text: '最近出库记录' })),
                h('div', { class: 'card-body' }, resultHost))));

    if (!canOut) {
        formHost.append(h('div', { class: 'empty-state', text: '没有 spare:issue 权限，无法出库' }));
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
            h('label', { text: '出库数量 *' }),
            h('input', { class: 'input', type: 'number', name: 'quantity', min: '1', value: '1' })),
        h('div', { class: 'field' },
            h('label', { text: '出库单价（元，留空用档案单价）' }),
            h('input', { class: 'input', type: 'number', name: 'unitPrice', min: '0', step: '0.01', placeholder: '—' })),
        h('div', { class: 'field' },
            h('label', { text: '出库原因 / 备注 *' }),
            h('input', { class: 'input', name: 'remark', maxlength: '200', placeholder: '如：外借给 X 项目 / 报废处理' })),
        h('div', { class: 'form-actions' },
            h('button', { class: 'btn btn-primary', type: 'button', text: '确认出库' })));

    formHost.append(form);
    form.querySelector('.btn-primary').addEventListener('click', guard(async () => {
        const partId = picker.select.value;
        const quantity = Number(form.querySelector('[name=quantity]').value);
        const remark = form.querySelector('[name=remark]').value;
        if (!partId) { toast('请选择备件', 'error'); return; }
        if (!quantity || quantity <= 0) { toast('请输入正确的出库数量', 'error'); return; }
        if (!remark.trim()) { toast('请填写出库原因', 'error'); return; }
        const data = { partId: Number(partId), quantity, remark: remark.trim() };
        const unitPrice = form.querySelector('[name=unitPrice]').value;
        if (unitPrice) data.unitPrice = Number(unitPrice);
        const txn = await api.post('/api/inventory/stock/out', data);
        toast(`出库成功，流水号 ${txn.txnNo}`);
        form.querySelector('[name=quantity]').value = '1';
        await loadRecent();
    }));

    async function loadRecent() {
        resultHost.innerHTML = '<div class="skeleton" style="width:60%"></div>';
        try {
            const page = await api.get('/api/inventory/stock/txns/page?pageNum=1&pageSize=8&txnType=OUT');
            const records = page.records || [];
            resultHost.innerHTML = records.length ? '' : '<div class="table-empty">暂无出库记录</div>';
            resultHost.append(...records.map(t => h('div', { class: 'stack-row' },
                h('div', { class: 'stack-head' },
                    h('strong', { text: t.partName || `备件 #${t.partId}` }),
                    h('span', { class: 'cell-sub mono', text: t.txnNo })),
                h('div', { class: 'cell-sub' },
                    `-${t.quantity} → 库存 ${t.afterStock} · ¥${Number(t.amount || 0).toFixed(2)} · ${esc(t.remark || '')}`))));
        } catch (error) {
            resultHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    await loadRecent();
}
