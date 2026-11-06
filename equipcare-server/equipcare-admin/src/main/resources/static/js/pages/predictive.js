/**
 * 预测性维护页（第九期创新升级）。
 *
 * MTBF/MTTR（90 天窗口）+ 健康分线性外推 7 天，最危险的设备排最前。
 * 页面只读，风险处置走既有工单流程（创建维修单 / 调整巡检计划）。
 */

import { api } from '../api.js';
import { esc, formatDate, h, statusTag, toastError } from '../ui.js';

const EQUIP_STATUS = {
    IN_USE: { label: '在用', tag: 'tag-green' },
    IDLE: { label: '停用/闲置', tag: 'tag-gray' },
    REPAIRING: { label: '维修中', tag: 'tag-amber' },
    SCRAPPED: { label: '已报废', tag: 'tag-red' },
};

const RISK = {
    HIGH: { label: '高风险', tag: 'tag-red' },
    WATCH: { label: '观察', tag: 'tag-amber' },
    LOW: { label: '平稳', tag: 'tag-green' },
};

export async function render(container, ctx) {
    ctx.setTitle('预测性维护');
    let rows;
    try {
        rows = await api.get('/api/analytics/predictive');
    } catch (error) {
        toastError(error);
        container.append(h('div', { class: 'card' }, h('div', { class: 'card-body' }, h('p', { text: error.message }))));
        return;
    }

    const high = rows.filter((r) => r.riskLevel === 'HIGH').length;
    const watch = rows.filter((r) => r.riskLevel === 'WATCH').length;

    const summary = h('div', { class: 'stat-grid' },
        statCard('设备总数', rows.length, ''),
        statCard('高风险', high, high > 0 ? '建议立即安排保养' : ''),
        statCard('观察中', watch, watch > 0 ? '健康分趋势下滑' : ''),
        statCard('平均 MTBF', avgMtbf(rows), 'h · 90 天窗口'));

    const table = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '设备预测视图' }),
            h('span', { class: 'hint', text: '按 7 天预测健康分升序 · MTBF/MTTR 口径见 Swagger 文档' })),
        h('div', { class: 'card-body table-wrap' },
            h('table', { class: 'table' },
                h('thead', {}, h('tr', {},
                    th('设备'), th('最新分'), th('健康趋势'), th('7 天预测'), th('风险'),
                    th('MTBF(h)'), th('MTTR(h)'), th('故障数'), th('状态'), th(''))),
                h('tbody', {}, ...rows.map((r) => h('tr', {},
                    td(h('div', {},
                        h('div', { text: r.equipmentName || '—' }),
                        h('div', { class: 'cell-sub mono', text: r.assetNo || '' }))),
                    td(boldScore(r.latestScore)),
                    td(trendBar(r.trend, r.slopePerDay)),
                    td(boldScore(r.predictedScore7d, true)),
                    td(h('span', { html: statusTag(RISK, r.riskLevel) }), ' ',
                        h('div', { class: 'cell-sub', text: r.riskReason || '' })),
                    td(num(r.mtbfHours)),
                    td(num(r.mttrHours)),
                    td(String(r.failureCount90d ?? '—')),
                    td(h('span', { html: statusTag(EQUIP_STATUS, r.status) })),
                    td(h('button', {
                        class: 'btn btn-sm', type: 'button', text: '健康详情',
                        onclick: () => ctx.navigate(`/health/${r.equipmentId}`),
                    }))))))));

    container.append(summary, table);
}

function statCard(label, value, note) {
    return h('div', { class: 'card' }, h('div', { class: 'card-body', style: 'text-align:center;padding:14px' },
        h('div', { class: 'hint', text: label }),
        h('div', { style: 'font-size:26px;font-weight:800;color:var(--primary)', text: String(value) }),
        note ? h('div', { class: 'cell-sub', text: note }) : null));
}

function th(text) {
    return h('th', { text });
}

function td(...children) {
    return h('td', {}, ...children);
}

function num(v) {
    return v == null ? '—' : String(v);
}

function boldScore(v, isForecast) {
    if (v == null) {
        return td('—');
    }
    const color = v < 60 ? 'var(--danger)' : v < 75 ? 'var(--warning)' : 'var(--success)';
    return td(h('b', {
        style: `color:${color}`,
        text: (isForecast ? '≈' : '') + Math.round(v),
    }));
}

/** 纯 CSS 迷你趋势条：8 个点，颜色随分数，下滑标红箭头 */
function trendBar(trend, slope) {
    if (!trend || !trend.length) {
        return td(h('span', { class: 'hint', text: '积累中' }));
    }
    const bars = trend.map((p) => {
        const score = p.score == null ? 0 : p.score;
        const color = score < 60 ? 'var(--danger)' : score < 75 ? 'var(--warning)' : 'var(--brand, #16845b)';
        return h('i', {
            style: `display:inline-block;width:6px;height:${Math.max(4, Math.round(score / 4))}px;`
                + `background:${color};border-radius:2px;margin-right:2px;vertical-align:bottom`,
            title: `${p.date}：${p.score == null ? '—' : p.score}`,
        });
    });
    const arrow = slope != null && slope < -0.5
        ? h('span', { style: 'color:var(--danger);margin-left:5px;font-size:12px', text: '↘ ' + slope.toFixed(2) })
        : null;
    return td(h('div', { style: 'display:flex;align-items:flex-end;min-height:26px' }, ...bars, arrow));
}

function avgMtbf(rows) {
    const values = rows.map((r) => r.mtbfHours).filter((v) => v != null);
    if (!values.length) {
        return '—';
    }
    return (values.reduce((a, b) => a + b, 0) / values.length).toFixed(1);
}
