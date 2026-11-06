/**
 * 库存预警：低于安全库存的备件清单。
 * 系统每小时自动扫描一次并对同一备件同一天只发一次站内提醒；
 * 本页是实时视图，供采购与库管主动核对。
 */

import { api } from '../api.js';
import { esc, h, renderTable } from '../ui.js';
import { navLinks, stockCell } from './spare-shared.js';

export async function render(container, ctx) {
    const tableHost = h('div', {});
    const summary = h('span', { class: 'hint' });

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '库存预警' }),
                h('p', { text: '当前库存低于安全库存的备件。系统每小时自动扫描并提醒管理员，同一备件每天只提醒一次。' })),
            h('div', { class: 'page-actions' },
                navLinks(ctx, '/spare/alert'))),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: '低于安全库存' }), summary),
            tableHost));

    await load();

    async function load() {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        try {
            const records = await api.get('/api/inventory/parts/below-safety');
            summary.textContent = `${records.length} 个备件低于安全库存`;
            tableHost.innerHTML = '';
            tableHost.innerHTML = renderTable({
                columns: [
                    { title: '编码', render: r => `<span class="mono">${esc(r.partNo)}</span>` },
                    { title: '名称', render: r => esc(r.partName) },
                    { title: '分类', render: r => esc(r.category || '—') },
                    { title: '库存 / 安全', render: r => stockCell(r) },
                    { title: '缺口', render: r => `<strong style="color:var(--danger)">${Number(r.safetyStock) - Number(r.currentStock)}</strong>` },
                    { title: '单价', render: r => `¥${Number(r.unitPrice).toFixed(2)}` },
                    { title: '建议补货金额', render: r => {
                        const gap = Number(r.safetyStock) * 2 - Number(r.currentStock);
                        return gap > 0 ? `¥${(gap * Number(r.unitPrice)).toFixed(2)}` : '—';
                    } },
                    { title: '供应商', render: r => esc(r.supplier || '—') },
                ],
                rows: records,
                empty: '✅ 全部备件库存充足，无预警',
            });
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }
}
