/**
 * 设备健康详情：分数、等级、逐项扣分原因与建议措施、30 天走势。
 * 「每项扣分原因」来自后端 riskItems——分数必须可解释，不允许前端编数字。
 */

import { api } from '../api.js';
import { esc, h, HEALTH_LEVEL, statusTag, toastError } from '../ui.js';

export async function render(container, ctx) {
    const equipmentId = ctx.params.id;
    const body = h('div', { class: 'stack' });
    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '设备健康详情' }),
                h('p', { text: '分数与扣分原因由后端规则计算，每一项都给出原始计数、计算规则与建议措施。' })),
            h('div', { class: 'page-actions' },
                h('button', { class: 'btn btn-ghost', type: 'button', text: '重算健康分' }))),
        body);

    const recalcBtn = container.querySelector('.page-actions .btn-ghost');
    recalcBtn.addEventListener('click', async () => {
        try {
            await api.post(`/api/analytics/health/${equipmentId}/recalculate`);
            await load();
        } catch (error) {
            toastError(error);
        }
    });

    await load();

    async function load() {
        body.innerHTML = '<div class="card"><div class="card-body"><div class="skeleton" style="width:60%"></div></div></div>';
        let vo;
        try {
            vo = await api.get(`/api/analytics/health/${equipmentId}`);
        } catch (error) {
            body.innerHTML = `<div class="card"><div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div></div>`;
            return;
        }
        body.innerHTML = '';

        const levelTag = statusTag(HEALTH_LEVEL, vo.level, vo.level);
        // 顶部概览：分数 + 等级 + 分项
        body.append(h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: vo.equipmentName || `设备 #${equipmentId}` }),
                h('span', { class: 'cell-sub mono', text: vo.assetNo || '' })),
            h('div', { class: 'card-body' },
                h('div', { class: 'metric-grid' },
                    metric(`${vo.healthScore == null ? '—' : Number(vo.healthScore).toFixed(0)}`, '健康分', scoreColor(vo.healthScore)),
                    metric(levelTag, '等级'),
                    metric(vo.openWoCount == null ? '—' : vo.openWoCount, '在途工单'),
                    metric(vo.idleDays == null ? '无记录' : `${vo.idleDays} 天`, '距上次维修'),
                    metric(vo.workOrderCount == null ? '—' : vo.workOrderCount, '工单数(30天)'),
                    metric(vo.anomalyCount == null ? '—' : vo.anomalyCount, '异常数(30天)')))));

        // 逐项扣分原因
        const items = vo.riskItems || [];
        body.append(h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: '扣分明细与建议' })),
            h('div', { class: 'card-body' },
                items.length ? items.map(item => h('div', { class: 'stack-row' },
                    h('div', { class: 'stack-head' },
                        h('strong', { text: item.label }),
                        h('span', {
                            style: `font-weight:700;color:${Number(item.deduction) > 0 ? 'var(--danger)' : 'var(--success)'}`,
                            text: Number(item.deduction) > 0 ? `-${Number(item.deduction).toFixed(0)} 分` : `+${Math.abs(Number(item.deduction)).toFixed(0)} 分`,
                        })),
                    h('div', { class: 'cell-sub', text: item.detail }),
                    item.suggestion ? h('div', { class: 'cell-sub', style: 'color:var(--primary)' }, '建议：', item.suggestion) : null))
                    : h('div', { class: 'table-empty', text: '无扣分项 —— 满分状态' }))));

        // 走势（CSS 条形，零依赖）
        const trend = vo.trend || [];
        if (trend.length) {
            const max = Math.max(...trend.map(p => Number(p.healthScore) || 0), 100);
            body.append(h('div', { class: 'card' },
                h('div', { class: 'card-head' }, h('h2', { text: '近 30 天走势' })),
                h('div', { class: 'card-body' },
                    h('div', { class: 'trend-bars', style: 'display:flex;align-items:flex-end;gap:6px;height:120px' },
                        trend.slice().reverse().map(point => {
                            const pct = Math.max(2, Number(point.healthScore) / max * 100);
                            return h('div', {
                                title: `${point.statDate}：${Number(point.healthScore).toFixed(0)} 分`,
                                style: `flex:1;background:${scoreColor(point.healthScore)};opacity:.75;height:${pct}%;border-radius:4px 4px 0 0;min-width:8px`,
                            });
                        })))));
        }
    }
}

function metric(value, label, color) {
    return h('div', { class: 'metric-card' },
        h('div', { style: 'font-size:22px;font-weight:700;' + (color ? `color:${color}` : '') }, value),
        h('div', { class: 'cell-sub', text: label }));
}

function scoreColor(score) {
    const value = Number(score);
    if (Number.isNaN(value)) return 'var(--text-secondary)';
    if (value >= 90) return 'var(--success)';
    if (value >= 75) return 'var(--info)';
    if (value >= 60) return 'var(--warning)';
    return 'var(--danger)';
}
