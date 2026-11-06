/**
 * 巡检统计。
 *
 * 不引入图表库：趋势用 CSS 高度画柱，分布用百分比宽度画条，
 * 既保证零外部依赖，也避免首屏为图表库付出额外体积。
 */

import { api, auth } from '../api.js';
import {
    INSPECT_STATUS, esc, formatDate, h, renderTable,
} from '../ui.js';

const RANGES = [
    { value: 7, label: '近 7 天' },
    { value: 30, label: '近 30 天' },
    { value: 90, label: '近 90 天' },
    { value: 365, label: '近一年' },
];

export async function render(container, ctx) {
    const mayFilterMine = auth.can('inspect:task:view');
    let days = 30;
    let mine = false;

    const rangeBar = h('div', { class: 'chip-row' },
        ...RANGES.map((range) => {
            const button = h('button', {
                class: `chip${range.value === days ? ' active' : ''}`, type: 'button', text: range.label,
                onclick: () => {
                    days = range.value;
                    rangeBar.querySelectorAll('.chip').forEach((chip) => chip.classList.remove('active'));
                    button.classList.add('active');
                    load();
                },
            });
            return button;
        }),
        mayFilterMine ? h('label', { class: 'check-line', style: 'margin-left:8px' },
            h('input', {
                type: 'checkbox',
                onchange: (event) => {
                    mine = event.target.checked;
                    load();
                },
            }),
            h('span', { text: '只看我的任务' })) : null);

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '巡检统计' }),
            h('p', { text: '完成率、异常率、计划维度与高频异常项；数据实时来自巡检任务与异常单。' })),
        h('div', { class: 'page-actions' }));

    const metricsHost = h('div', { class: 'metric-grid' });
    const statusHost = h('div', { class: 'card' });
    const trendHost = h('div', { class: 'card' });
    const planHost = h('div', { class: 'card' });
    const itemHost = h('div', { class: 'card' });
    const rangeHost = h('span', { class: 'hint', text: '' });

    container.append(head, rangeBar, rangeHost, metricsHost,
        h('div', { class: 'grid-2' }, statusHost, itemHost), trendHost, planHost);

    async function load() {
        metricsHost.innerHTML = '<div class="skeleton" style="height:96px"></div>';
        try {
            const data = await api.get('/api/inspection/stats', { days, assigneeId: mine ? 0 : '' });
            rangeHost.textContent = `统计区间 ${formatDate(data.from)} ~ ${formatDate(data.to)}，共 ${data.totalTasks ?? 0} 条任务`;
            paintMetrics(data);
            paintStatus(data);
            paintTrend(data);
            paintPlans(data);
            paintItems(data);
        } catch (error) {
            metricsHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function metric(label, value, hint, tone) {
        return h('div', { class: 'metric' },
            h('div', { class: 'metric-label', text: label }),
            h('div', { class: `metric-value ${tone || ''}`, text: value }),
            hint ? h('div', { class: 'metric-hint', text: hint }) : null);
    }

    function paintMetrics(data) {
        metricsHost.innerHTML = '';
        metricsHost.append(
            metric('任务总数', String(data.totalTasks ?? 0), `区间内生成`),
            metric('已提交', String(data.submittedTasks ?? 0), `完成率 ${percent(data.completionRate)}`, 'ok'),
            metric('待执行', String(data.pendingTasks ?? 0), '含执行中'),
            metric('已过期', String(data.expiredTasks ?? 0), '超过时限未开始', (data.expiredTasks || 0) > 0 ? 'warn' : ''),
            metric('异常任务', String(data.abnormalTasks ?? 0), `异常率 ${percent(data.abnormalRate)}`, (data.abnormalTasks || 0) > 0 ? 'danger' : ''),
            metric('异常单', String(data.anomalyTotal ?? 0),
                `待处理 ${data.anomalyOpen ?? 0} · 已转单 ${data.anomalyConverted ?? 0} · 已关闭 ${data.anomalyClosed ?? 0}`));
    }

    function paintStatus(data) {
        statusHost.innerHTML = '';
        const list = data.byStatus || [];
        const total = list.reduce((sum, item) => sum + (item.count || 0), 0);
        statusHost.append(h('div', { class: 'card-head' },
            h('h2', { text: '任务状态分布' }),
            h('span', { class: 'hint', text: `共 ${total} 条` })));
        const body = h('div', { class: 'card-body' });
        if (!total) {
            body.append(h('div', { class: 'hint', text: '区间内没有任务' }));
        } else {
            list.forEach((item) => {
                const ratio = total ? ((item.count || 0) / total) * 100 : 0;
                body.append(h('div', { class: 'bar-row' },
                    h('span', { class: 'bar-label', text: item.label || INSPECT_STATUS[item.code]?.label || item.code }),
                    h('div', { class: 'bar-track' },
                        h('i', { class: `bar-fill fill-${item.code}`, style: `width:${ratio}%` })),
                    h('span', { class: 'bar-value', text: `${item.count}（${ratio.toFixed(0)}%）` })));
            });
        }
        statusHost.append(body);
    }

    function paintTrend(data) {
        trendHost.innerHTML = '';
        const daily = data.daily || [];
        trendHost.append(h('div', { class: 'card-head' },
            h('h2', { text: '每日任务趋势' }),
            h('span', { class: 'hint', text: '灰=总量，绿=已提交，红=异常' })));
        if (!daily.length) {
            trendHost.append(h('div', { class: 'card-body' }, h('div', { class: 'hint', text: '区间内没有任务' })));
            return;
        }
        const max = Math.max(...daily.map((day) => day.total || 0), 1);
        const chart = h('div', { class: 'trend' });
        daily.forEach((day) => {
            const col = h('div', { class: 'trend-col', title: `${day.date} 共 ${day.total} 条，已提交 ${day.submitted}，异常 ${day.abnormal}` },
                h('div', { class: 'trend-bars' },
                    h('i', { class: 'trend-bar total', style: `height:${((day.total || 0) / max) * 100}%` }),
                    h('i', { class: 'trend-bar done', style: `height:${((day.submitted || 0) / max) * 100}%` }),
                    h('i', { class: 'trend-bar abnormal', style: `height:${((day.abnormal || 0) / max) * 100}%` })));
            chart.append(col);
        });
        const axis = h('div', { class: 'trend-axis' });
        daily.forEach((day, index) => {
            // 柱子太密时只标首尾与每 5 天，避免标签重叠
            const show = index === 0 || index === daily.length - 1 || index % 5 === 0;
            axis.append(h('span', { text: show ? String(day.date).slice(5) : '' }));
        });
        trendHost.append(h('div', { class: 'card-body' }, chart, axis));
    }

    function paintPlans(data) {
        planHost.innerHTML = '';
        planHost.append(h('div', { class: 'card-head' },
            h('h2', { text: '按计划维度' }),
            h('span', { class: 'hint', text: '哪个计划拖了后腿一眼可见' })));
        planHost.append(h('div', { class: 'card-body', html: renderTable({
            columns: [
                { title: '计划', key: 'planName', render: (row) => esc(row.planName || '手工发起') },
                { title: '任务数', key: 'total', width: '90px', align: 'right', render: (row) => esc(row.total ?? 0) },
                { title: '已提交', key: 'submitted', width: '90px', align: 'right', render: (row) => esc(row.submitted ?? 0) },
                {
                    title: '异常', key: 'abnormal', width: '80px', align: 'right',
                    render: (row) => (row.abnormal > 0
                        ? `<span style="color:var(--danger)">${row.abnormal}</span>`
                        : '0'),
                },
                {
                    title: '完成率', key: 'completionRate', width: '140px',
                    render: (row) => {
                        const rate = Number(row.completionRate || 0);
                        return `<div class="inline-bar"><i style="width:${Math.min(100, rate)}%"></i></div>`
                            + `<span class="hint">${rate.toFixed(0)}%</span>`;
                    },
                },
            ],
            rows: data.byPlan || [],
            empty: '区间内没有计划任务',
        }) }));
    }

    function paintItems(data) {
        itemHost.innerHTML = '';
        const list = data.topAnomalyItems || [];
        itemHost.append(h('div', { class: 'card-head' },
            h('h2', { text: '高频异常项' }),
            h('span', { class: 'hint', text: '前 10 项' })));
        const body = h('div', { class: 'card-body' });
        if (!list.length) {
            body.append(h('div', { class: 'hint', text: '区间内没有异常，保持住' }));
        } else {
            const max = Math.max(...list.map((item) => item.count || 0), 1);
            list.forEach((item) => {
                body.append(h('div', { class: 'bar-row' },
                    h('span', { class: 'bar-label', text: item.itemName }),
                    h('div', { class: 'bar-track' },
                        h('i', { class: 'bar-fill fill-ABNORMAL', style: `width:${((item.count || 0) / max) * 100}%` })),
                    h('span', { class: 'bar-value', text: `${item.count}` })));
            });
        }
        itemHost.append(body);
    }

    await load();
}

function percent(value) {
    const num = Number(value);
    return Number.isFinite(num) ? `${num.toFixed(1)}%` : '—';
}
