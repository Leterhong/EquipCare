/**
 * 设备健康列表：全部非报废设备的健康分与等级（后端规则计算，前端不持有数字）。
 * 数据来自 /api/analytics/health/at-risk?threshold=0（阈值 0 即全量）。
 */

import { api } from '../api.js';
import { esc, h, HEALTH_LEVEL, renderTable, statusTag } from '../ui.js';

export async function render(container, ctx) {
    const tableHost = h('div', {});
    const summary = h('span', { class: 'hint' });
    const levelFilter = h('select', { class: 'input' },
        h('option', { value: '', text: '全部等级' }),
        h('option', { value: 'DANGER', text: '危险' }),
        h('option', { value: 'WARNING', text: '预警' }),
        h('option', { value: 'SUB_HEALTHY', text: '亚健康' }),
        h('option', { value: 'HEALTHY', text: '健康' }));

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '设备健康' }),
                h('p', { text: '规则评分模型（后端计算）：故障频次 / 维修超时 / 巡检异常 / 巡检逾期 / 未完成工单 / 久未维修 / 运行状态，巡检正常完成可加分。点击行查看逐项扣分原因。' })),
            h('div', { class: 'page-actions' })),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: '健康总览' }),
                h('div', { class: 'toolbar' }, levelFilter)),
            tableHost));

    let records = [];
    await load();

    levelFilter.addEventListener('change', () => renderRows());

    async function load() {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        try {
            records = await api.get('/api/analytics/health/at-risk?threshold=0&limit=200');
            renderRows();
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function renderRows() {
        const filtered = levelFilter.value
            ? records.filter(r => r.level === levelFilter.value)
            : records;
        summary.textContent = `共 ${filtered.length} 台设备`;
        tableHost.innerHTML = '';
        tableHost.innerHTML = renderTable({
            columns: [
                { title: '设备', render: r => esc(r.equipmentName || `#${r.equipmentId}`) },
                { title: '资产编号', render: r => `<span class="mono">${esc(r.assetNo || '—')}</span>` },
                { title: '健康分', render: r => `<strong>${r.healthScore == null ? '—' : Number(r.healthScore).toFixed(0)}</strong>` },
                { title: '等级', render: r => statusTag(HEALTH_LEVEL, r.level, r.level) },
                { title: '在途工单', render: r => r.openWoCount == null ? '—' : r.openWoCount },
                { title: '距上次维修', render: r => r.idleDays == null ? '无记录' : `${r.idleDays} 天` },
                { title: '工单数(30天)', render: r => r.workOrderCount == null ? '—' : r.workOrderCount },
                { title: '异常数(30天)', render: r => r.anomalyCount == null ? '—' : r.anomalyCount },
            ],
            rows: filtered,
            empty: '暂无设备或尚未计算健康分（点「重算」生成）',
        });
        // 整行点击进详情
        const rows = tableHost.querySelectorAll('tbody tr');
        rows.forEach((tr, index) => {
            tr.style.cursor = 'pointer';
            tr.addEventListener('click', () => ctx.navigate(`/health/${filtered[index].equipmentId}`));
        });
    }
}
