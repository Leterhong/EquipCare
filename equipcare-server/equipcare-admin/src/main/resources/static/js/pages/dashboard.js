/**
 * 运维数据驾驶舱（第七阶段建立，第十阶段重构为五层信息架构）。
 *
 * 数据全部来自 /api/analytics/dashboard/summary 与 /charts，前端零计算：
 * 指标为 null 或图表列表为空时显示「暂无数据」，绝不编造数字。
 * 近期动态来自 /api/inspection/anomalies 与 /api/workorder 真实列表。
 * 图表引擎为本仓库内置的 ECharts（/js/lib/echarts.min.js，本地文件、无 CDN）。
 */

import { api, auth } from '../api.js';
import { esc, h, toastError } from '../ui.js';
import { WORK_ORDER_STATUS } from '../ui.js';

const EMPTY_TEXT = '暂无数据';
const PENDING_STATUSES = ['PENDING_ASSIGN', 'PENDING_ACCEPT', 'IN_PROGRESS', 'PENDING_REVIEW', 'REJECTED'];

export async function render(container, ctx) {
    let charts = [];
    let currentRange = { startDate: isoOf(-29), endDate: isoOf(0) };

    /* ---------------- 欢迎区 ---------------- */
    const user = ctx.user || {};
    const name = user.realName || user.username || '';
    const hour = new Date().getHours();
    const greet = hour < 6 ? '凌晨好' : hour < 9 ? '早上好' : hour < 12 ? '上午好'
        : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好';
    const dateText = new Date().toLocaleDateString('zh-CN', {
        year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
    });

    const quickActions = [];
    if (auth.can('wo:workorder:create')) {
        quickActions.push(h('button', { class: 'btn', type: 'button', text: '快捷报修', onclick: () => ctx.navigate('/workorder/new') }));
    }
    if (auth.can('asset:equipment:create')) {
        quickActions.push(h('button', { class: 'btn', type: 'button', text: '新增设备', onclick: () => ctx.navigate('/equipment/new') }));
    }
    if (auth.can('inspect:plan:manage')) {
        quickActions.push(h('button', { class: 'btn btn-primary', type: 'button', text: '创建巡检计划', onclick: () => ctx.navigate('/inspect/plan') }));
    }

    /* ---------------- KPI 区 ---------------- */
    const kpiHost = h('div', { class: 'kpi-grid' });
    const coreHost = h('div', { class: 'dash-core-grid' });

    /* ---------------- 图表区 ---------------- */
    const chartDefs = [
        { key: 'equipmentStatusDist', title: '设备状态分布', kind: 'pie' },
        { key: 'monthlyFaultTrend', title: '故障趋势', kind: 'line' },
        { key: 'workOrderStatusDist', title: '工单状态分布', kind: 'pie' },
        { key: 'monthlyInspectionTrend', title: '巡检完成情况', kind: 'bar-two' },
        { key: 'equipmentFaultRank', title: '设备健康排行（按故障数）', kind: 'bar-rank' },
        { key: 'faultTypeRank', title: '高频故障类型', kind: 'bar-rank' },
        { key: 'monthlyCostTrend', title: '维修成本趋势（元）', kind: 'line' },
        { key: 'sparePartRank', title: '备件消耗排行（元）', kind: 'bar-rank' },
        { key: 'repairmanRank', title: '维修人员工作量排行', kind: 'bar-rank' },
    ];

    const chartHosts = new Map();
    const gridTwo = (keyA, keyB) => h('div', { class: 'dash-grid-2' },
        chartCard(keyA), chartCard(keyB));
    const chartCard = (key) => {
        const def = chartDefs.find((d) => d.key === key);
        const host = h('div', { class: 'chart-host' });
        chartHosts.set(key, host);
        return h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: def.title })),
            host);
    };
    const repairmanHost = h('div', { class: 'chart-host' });
    chartHosts.set('repairmanRank', repairmanHost);

    /* ---------------- 近期动态 ---------------- */
    const anomalyHost = h('div', { class: 'dash-feed' }, loadingRow());
    const pendingHost = h('div', { class: 'dash-feed' }, loadingRow());

    /* ---------------- 导出区（仅持 analytics:export 者可见） ---------------- */
    const exportSection = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '报表导出（Excel）' }),
            h('span', { class: 'hint', text: '行数上限 5000；巡检 / 工单 / 成本按当前筛选范围导出' })));
    const exportBody = h('div', { class: 'card-body export-row' });
    exportSection.append(exportBody);

    /* ---------------- 时间筛选 ---------------- */
    const rangeHint = h('span', { class: 'hint' });
    const quickRange = h('select', { class: 'input', 'aria-label': '统计范围' },
        h('option', { value: '7', text: '近 7 天' }),
        h('option', { value: '30', selected: true, text: '近 30 天' }),
        h('option', { value: '90', text: '近 90 天' }),
        h('option', { value: '365', text: '近一年' }),
        h('option', { value: 'custom', text: '自定义' }));
    const startInput = h('input', { type: 'date', class: 'input', value: currentRange.startDate, 'aria-label': '起始日期' });
    const endInput = h('input', { type: 'date', class: 'input', value: currentRange.endDate, 'aria-label': '结束日期' });
    const applyBtn = h('button', { class: 'btn btn-primary', type: 'button', text: '应用' });

    container.append(
        /* 欢迎区 */
        h('div', { class: 'dash-welcome' },
            h('div', {},
                h('h1', { class: 'dash-greet', text: `${greet}，${name}` }),
                h('p', { class: 'dash-date', text: `欢迎使用巡维云设备运维管理平台 · ${dateText}` })),
            h('div', { class: 'page-actions' }, ...quickActions)),
        /* 时间筛选 */
        h('div', { class: 'card' },
            h('div', { class: 'card-body toolbar' },
                quickRange, startInput, endInput, applyBtn, rangeHint)),
        /* 第一层：核心指标 */
        coreHost,
        /* 第二层：设备状态 + 故障趋势 */
        gridTwo('equipmentStatusDist', 'monthlyFaultTrend'),
        /* 第三层：工单状态 + 巡检完成 */
        gridTwo('workOrderStatusDist', 'monthlyInspectionTrend'),
        /* 第四层：全量指标总览（14 项，紧凑网格） */
        h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: '运维指标总览' }),
                h('span', { class: 'hint', text: 'MTTR 从开始维修计时；MTBF = 周期小时 × 正常设备数 ÷ 故障工单数' })),
            kpiHost),
        /* 第五层：排行与分析 */
        gridTwo('equipmentFaultRank', 'faultTypeRank'),
        gridTwo('monthlyCostTrend', 'sparePartRank'),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: '维修人员工作量排行' })),
            repairmanHost),
        /* 近期动态 */
        h('div', { class: 'dash-grid-2' },
            h('div', { class: 'card' },
                h('div', { class: 'card-head' },
                    h('h2', { text: '最近异常事件' }),
                    h('button', { class: 'btn btn-ghost btn-sm', type: 'button', text: '查看全部', onclick: () => ctx.navigate('/inspect/anomaly') })),
                anomalyHost),
            h('div', { class: 'card' },
                h('div', { class: 'card-head' },
                    h('h2', { text: '待处理维修工单' }),
                    h('button', { class: 'btn btn-ghost btn-sm', type: 'button', text: '查看全部', onclick: () => ctx.navigate('/workorder') })),
                pendingHost)),
        exportSection);

    if (auth.can('analytics:export')) {
        exportBody.append(
            exportButton('设备清单', '/api/export/devices'),
            exportButton('巡检记录', `/api/export/inspections?startDate=${currentRange.startDate}&endDate=${currentRange.endDate}`),
            exportButton('维修工单', `/api/export/work-orders?startDate=${currentRange.startDate}&endDate=${currentRange.endDate}`),
            exportButton('备件库存', '/api/export/inventory'),
            exportButton('维修成本统计', `/api/export/cost-stats?startDate=${currentRange.startDate}&endDate=${currentRange.endDate}`));
    } else {
        exportBody.append(h('span', { class: 'hint', text: '需要「报表导出」权限' }));
    }

    quickRange.addEventListener('change', () => {
        const custom = quickRange.value === 'custom';
        startInput.disabled = !custom;
        endInput.disabled = !custom;
        if (!custom) {
            currentRange = { startDate: isoOf(-(Number(quickRange.value) - 1)), endDate: isoOf(0) };
            startInput.value = currentRange.startDate;
            endInput.value = currentRange.endDate;
            refresh();
        }
    });
    applyBtn.addEventListener('click', () => refresh());

    await refresh();

    async function refresh() {
        currentRange = { startDate: startInput.value, endDate: endInput.value };
        if (!currentRange.startDate || !currentRange.endDate) {
            rangeHint.textContent = '请选择统计起止日期';
            return;
        }
        if (currentRange.startDate > currentRange.endDate) {
            rangeHint.textContent = '起始日期不能晚于结束日期';
            return;
        }
        const query = `startDate=${currentRange.startDate}&endDate=${currentRange.endDate}`;
        rangeHint.textContent = `统计范围 ${currentRange.startDate} 至 ${currentRange.endDate}`;
        loadSummary(query);
        loadCharts(query);
        loadFeeds();
        // 导出按钮跟随筛选范围刷新
        if (auth.can('analytics:export')) {
            exportBody.querySelectorAll('button[data-export]').forEach((btn) => {
                const base = btn.getAttribute('data-base');
                btn.setAttribute('data-export', `${base}?startDate=${currentRange.startDate}&endDate=${currentRange.endDate}`);
            });
        }
    }

    /* ---------------- 第一层：核心指标卡 ---------------- */
    async function loadSummary(query) {
        kpiHost.innerHTML = '<div class="skeleton" style="width:80%"></div>';
        coreHost.innerHTML = '<div class="skeleton" style="width:60%"></div>';
        try {
            const vo = await api.get(`/api/analytics/dashboard/summary?${query}`);
            renderCore(vo);
            renderKpis(vo);
        } catch (error) {
            toastError(error);
            coreHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
            kpiHost.innerHTML = '';
        }
    }

    function coreCard(item) {
        const display = item.value == null ? EMPTY_TEXT
            : `${Number(item.value).toLocaleString('zh-CN', { maximumFractionDigits: 1 })} ${item.unit || ''}`;
        return h('div', { class: 'stat dash-core-card' },
            h('div', { class: 'label', text: item.label }),
            h('div', { class: 'value', text: display, style: item.tone ? `color:${item.tone}` : '' }));
    }

    function renderCore(vo) {
        coreHost.innerHTML = '';
        coreHost.append(
            coreCard({ label: '设备总数', value: vo.equipmentTotal, unit: '台' }),
            coreCard({ label: '正常运行设备', value: vo.equipmentNormal, unit: '台' }),
            coreCard({ label: '故障设备', value: vo.equipmentFault, unit: '台', tone: 'var(--warning)' }),
            coreCard({ label: '待处理工单', value: vo.workOrderPending, unit: '单', tone: 'var(--danger)' }));
    }

    function renderKpis(vo) {
        const items = [
            { label: '停用设备', value: vo.equipmentDisabled, unit: '台' },
            { label: '设备健康率', value: vo.equipmentHealthRate, unit: '%' },
            { label: '巡检完成率', value: vo.inspectionCompletionRate, unit: '%' },
            { label: '异常发现数', value: vo.anomalyCount, unit: '项' },
            { label: '已完成工单', value: vo.workOrderCompleted, unit: '单' },
            { label: '超时工单', value: vo.workOrderOverdue, unit: '单', tone: 'var(--danger)' },
            { label: '平均维修时长 MTTR', value: vo.mttrMinutes, unit: '分钟' },
            { label: '平均故障间隔 MTBF', value: vo.mtbfHours, unit: '小时' },
            { label: '维修成本', value: vo.maintenanceCost, unit: '元' },
            { label: '备件消耗金额', value: vo.spareConsumptionAmount, unit: '元' },
        ];
        kpiHost.innerHTML = '';
        items.forEach((item) => {
            const display = item.value == null ? EMPTY_TEXT
                : `${Number(item.value).toLocaleString('zh-CN', { maximumFractionDigits: 1 })} ${item.unit}`;
            kpiHost.append(
                h('div', { class: 'stat' },
                    h('div', { class: 'label', text: item.label }),
                    h('div', { class: 'value', text: display, style: item.tone ? `color:${item.tone}` : '' })));
        });
    }

    /* ---------------- 图表 ---------------- */
    async function loadCharts(query) {
        try {
            const vo = await api.get(`/api/analytics/dashboard/charts?${query}`);
            charts.forEach((c) => c.dispose());
            charts = [];
            chartDefs.forEach((def) => {
                renderOne(def, vo[def.key]);
            });
        } catch (error) {
            toastError(error);
        }
    }

    function renderOne(def, data) {
        const host = chartHosts.get(def.key);
        if (!host) return;
        host.innerHTML = '';
        const dom = h('div', { class: 'chart-dom' });
        host.append(dom);
        const list = normalize(def.kind, data);
        if (!list.length) {
            host.append(h('div', { class: 'chart-empty', text: EMPTY_TEXT }));
            return;
        }
        const chart = window.echarts.init(dom);
        chart.setOption(buildOption(def, list));
        charts.push(chart);
    }

    function normalize(kind, data) {
        if (data == null) return [];
        if ((kind === 'line' || kind.startsWith('bar')) && Array.isArray(data) && data.length && data[0].month != null) {
            if (kind === 'bar-two') {
                return data.map((r) => ({ name: r.month, total: Number(r.total || 0), part: Number(r.part || 0) }));
            }
            return data.map((r) => ({ name: r.month, value: Number(r.total || 0) }));
        }
        return (data || []).map((r) => ({ name: r.name || '—', value: Number(r.value || 0) }));
    }

    function buildOption(def, list) {
        // 浅色企业主题：统一图表文本 / 轴线 / 提示框配色
        const axisLabel = { color: '#64748b', fontSize: 11 };
        const splitLine = { lineStyle: { color: '#e8edf2' } };
        const tooltip = {
            backgroundColor: '#ffffff',
            borderColor: '#e8edf2',
            textStyle: { color: '#263445', fontSize: 12 },
            extraCssText: 'box-shadow: 0 2px 8px rgba(15,23,42,.1);',
        };
        const palette = ['#16845b', '#2563eb', '#d97706', '#dc2626', '#3fae82', '#94a3b8', '#0ea5e9', '#cbd5e1'];
        const option = {
            color: palette,
            tooltip,
            grid: { left: 40, right: 24, top: 32, bottom: 28, containLabel: true },
        };
        if (def.kind === 'pie') {
            option.tooltip = { trigger: 'item', formatter: '{b}: {c} ({d}%)', ...tooltip };
            option.legend = { bottom: 0, textStyle: { color: '#64748b', fontSize: 11 } };
            option.series = [{
                type: 'pie', radius: ['38%', '66%'], data: list,
                label: { formatter: '{b}\n{c}', color: '#64748b', fontSize: 11 },
            }];
            return option;
        }
        if (def.kind === 'bar-rank') {
            const sorted = [...list].sort((a, b) => a.value - b.value);
            option.tooltip = { trigger: 'axis', ...tooltip };
            option.xAxis = { type: 'value', minInterval: 1, axisLabel, splitLine };
            option.yAxis = { type: 'category', data: sorted.map((r) => r.name), axisLabel, splitLine };
            option.series = [{ type: 'bar', data: sorted.map((r) => r.value), itemStyle: { color: '#16845b', borderRadius: [0, 3, 3, 0] } }];
            return option;
        }
        if (def.kind === 'bar-two') {
            option.tooltip = { trigger: 'axis', ...tooltip };
            option.legend = { bottom: 0, textStyle: { color: '#64748b', fontSize: 11 } };
            option.xAxis = { type: 'category', data: list.map((r) => r.name), axisLabel, splitLine };
            option.yAxis = { type: 'value', minInterval: 1, axisLabel, splitLine };
            option.series = [
                { name: '应执行', type: 'bar', data: list.map((r) => r.total), itemStyle: { color: '#cbd5e1', borderRadius: [3, 3, 0, 0] } },
                { name: '已提交', type: 'bar', data: list.map((r) => r.part), itemStyle: { color: '#16845b', borderRadius: [3, 3, 0, 0] } },
            ];
            return option;
        }
        option.tooltip = { trigger: 'axis', ...tooltip };
        option.xAxis = { type: 'category', data: list.map((r) => r.name), boundaryGap: def.kind === 'line', axisLabel, splitLine };
        option.yAxis = { type: 'value', axisLabel, splitLine };
        option.series = [{
            type: def.kind === 'line' ? 'line' : 'bar',
            data: list.map((r) => r.value),
            smooth: def.kind === 'line',
            symbolSize: 6,
            lineStyle: { width: 2.5, color: '#16845b' },
            itemStyle: { color: '#16845b', borderRadius: def.kind === 'line' ? 0 : [3, 3, 0, 0] },
            areaStyle: def.kind === 'line' ? { color: 'rgba(22, 132, 91, .08)' } : undefined,
        }];
        return option;
    }

    /* ---------------- 近期动态（真实列表） ---------------- */
    async function loadFeeds() {
        // 最近异常事件
        if (auth.can('inspect:anomaly:view')) {
            try {
                const data = await api.get('/api/inspection/anomalies', { pageNum: 1, pageSize: 5 });
                const rows = (data && (data.records || data.list)) || [];
                renderAnomalies(rows);
            } catch (error) {
                anomalyHost.innerHTML = `<div class="table-empty">${esc(error.message)}</div>`;
            }
        } else {
            anomalyHost.innerHTML = `<div class="table-empty">${EMPTY_TEXT}</div>`;
        }
        // 待处理维修工单
        if (auth.can('wo:workorder:view')) {
            try {
                const data = await api.get('/api/workorder', { pageNum: 1, pageSize: 20 });
                const rows = ((data && (data.records || data.list)) || [])
                    .filter((r) => PENDING_STATUSES.includes(r.status))
                    .slice(0, 5);
                renderPending(rows);
            } catch (error) {
                pendingHost.innerHTML = `<div class="table-empty">${esc(error.message)}</div>`;
            }
        } else {
            pendingHost.innerHTML = `<div class="table-empty">${EMPTY_TEXT}</div>`;
        }
    }

    function feedRow(main, sub, tag) {
        return h('div', { class: 'feed-row' },
            h('div', { class: 'feed-main' },
                h('b', { text: main }),
                h('span', { class: 'hint', text: sub })),
            tag || '');
    }

    function renderAnomalies(rows) {
        anomalyHost.innerHTML = '';
        if (!rows.length) {
            anomalyHost.append(h('div', { class: 'table-empty', text: '近期没有异常记录，运行平稳' }));
            return;
        }
        rows.forEach((row) => {
            anomalyHost.append(feedRow(
                `${row.anomalyNo || '—'} · ${row.equipmentName || '—'}`,
                `${row.itemName || ''}${row.description ? `：${row.description}` : ''}`,
                statusTagOf(row.status)));
        });
    }

    function renderPending(rows) {
        pendingHost.innerHTML = '';
        if (!rows.length) {
            pendingHost.append(h('div', { class: 'table-empty', text: '没有待处理工单' }));
            return;
        }
        rows.forEach((row) => {
            pendingHost.append(feedRow(
                `${row.code || row.workOrderNo || '—'} · ${row.equipmentName || row.title || '—'}`,
                row.title || '',
                statusTagOf(row.status)));
        });
    }

    function statusTagOf(status) {
        const item = WORK_ORDER_STATUS[status];
        const label = item ? item.label : status || '—';
        const cls = item ? item.tag : 'tag-gray';
        return h('span', { class: `tag ${cls}`, text: label });
    }

    const onResize = () => charts.forEach((c) => c.resize());
    window.addEventListener('resize', onResize);
    ctx.onCleanup?.(() => {
        window.removeEventListener('resize', onResize);
        charts.forEach((c) => c.dispose());
        charts = [];
    });

    function exportButton(label, path) {
        return h('button', {
            class: 'btn',
            type: 'button',
            text: label,
            dataset: { export: path, base: path.split('?')[0] },
            onclick: (event) => {
                const btn = event.currentTarget;
                window.open(btn.getAttribute('data-export'), '_blank');
            },
        });
    }
}

function isoOf(offsetDays) {
    const d = new Date();
    d.setDate(d.getDate() + offsetDays);
    return d.toISOString().slice(0, 10);
}

function loadingRow() {
    return h('div', { class: 'skeleton', style: 'width:70%' });
}
