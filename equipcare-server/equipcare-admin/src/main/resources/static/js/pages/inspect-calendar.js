/**
 * 巡检日历。
 *
 * 按计划日聚合任务状态：每天显示任务总量与四种状态的分桶数量，
 * 点某一天在下方列出当天任务，方便班组长一眼看出哪天排得满、哪天有逾期。
 */

import { api, auth } from '../api.js';
import {
    INSPECT_STATUS, esc, formatDate, h, renderTable, statusTag,
} from '../ui.js';

const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];

export async function render(container, ctx) {
    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '巡检日历' }),
            h('p', { text: '按计划日查看任务分布；点某一天可看到当天的任务清单与执行状态。' })));

    container.append(head);
    await renderCalendarView(container, ctx);
}

/**
 * 可嵌入的日历视图：独立的日历卡片 + 当日任务清单。
 *
 * 供「巡检日历」页与「巡检任务」页的日历 Tab 共用；
 * month 参数允许调用方带参直达（如 /inspect/calendar?month=2026-10）。
 */
export async function renderCalendarView(container, ctx, initialMonth) {
    const mayFilterMine = auth.can('inspect:task:view');
    const today = new Date();
    let month = initialMonth || `${today.getFullYear()}-${pad(today.getMonth() + 1)}`;
    let selectedDate = null;
    let calendar = [];

    const monthLabel = h('strong', { style: 'font-size:15px', text: '' });
    const dayHost = h('div', { class: 'card' });
    const legend = h('div', { class: 'calendar-legend' },
        ...['PENDING', 'IN_PROGRESS', 'SUBMITTED', 'EXPIRED'].map((code) => h('span', { class: 'legend-item' },
            h('i', { class: `legend-dot dot-${code}` }),
            h('span', { text: INSPECT_STATUS[code].label }))));

    const grid = h('div', { class: 'calendar-grid' });
    const calendarCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '任务分布' }), h('div', { class: 'grow' }), monthLabel, legend,
            h('div', { class: 'calendar-nav' },
                h('button', { class: 'btn btn-sm', type: 'button', text: '← 上月', onclick: () => shiftMonth(-1) }),
                h('button', { class: 'btn btn-sm', type: 'button', text: '本月', onclick: () => gotoMonth(new Date()) }),
                h('button', { class: 'btn btn-sm', type: 'button', text: '下月 →', onclick: () => shiftMonth(1) }))),
        h('div', { class: 'card-body' }, grid));

    container.append(calendarCard, dayHost);

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function shiftMonth(delta) {
        const [year, monthNo] = month.split('-').map(Number);
        const date = new Date(year, monthNo - 1 + delta, 1);
        gotoMonth(date);
    }

    function gotoMonth(date) {
        month = `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
        selectedDate = null;
        load();
    }

    async function load() {
        monthLabel.textContent = month.replace('-', ' 年 ') + ' 月';
        grid.innerHTML = '<div class="skeleton" style="height:240px"></div>';
        try {
            calendar = await api.get('/api/inspection/calendar', { month });
            paint();
        } catch (error) {
            grid.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function paint() {
        const [year, monthNo] = month.split('-').map(Number);
        const first = new Date(year, monthNo - 1, 1);
        const daysInMonth = new Date(year, monthNo, 0).getDate();
        // JS 里 getDay() 周日是 0，这里换算成以周一为首列
        const offset = (first.getDay() + 6) % 7;

        const byDate = new Map();
        calendar.forEach((day) => byDate.set(String(day.date), day));

        grid.innerHTML = '';
        WEEK_LABELS.forEach((label) => grid.append(h('div', { class: 'calendar-head', text: label })));

        for (let i = 0; i < offset; i += 1) {
            grid.append(h('div', { class: 'calendar-cell empty' }));
        }

        for (let dayNo = 1; dayNo <= daysInMonth; dayNo += 1) {
            const dateText = `${month}-${pad(dayNo)}`;
            const data = byDate.get(dateText);
            const isToday = dateText === `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
            const cell = h('button', {
                class: `calendar-cell${isToday ? ' today' : ''}${selectedDate === dateText ? ' selected' : ''}${data ? ' has-task' : ''}`,
                type: 'button',
                onclick: () => {
                    selectedDate = dateText;
                    paint();
                    loadDay(dateText);
                },
            },
            h('span', { class: 'calendar-day', text: String(dayNo) }));
            if (data && data.total) {
                cell.append(h('span', { class: 'calendar-total', text: `${data.total} 项` }));
                const bars = h('div', { class: 'calendar-bars' });
                ['PENDING', 'IN_PROGRESS', 'SUBMITTED', 'EXPIRED'].forEach((code) => {
                    const count = data[statusKey(code)] || 0;
                    if (count > 0) {
                        bars.append(h('i', {
                            class: `bar bar-${code}`,
                            title: `${INSPECT_STATUS[code].label} ${count} 项`,
                            style: `flex:${count}`,
                        }));
                    }
                });
                cell.append(bars);
                if (data.abnormal > 0) {
                    cell.append(h('span', { class: 'calendar-abnormal', text: `${data.abnormal} 异常` }));
                }
            }
            grid.append(cell);
        }
    }

    function statusKey(code) {
        return {
            PENDING: 'pending',
            IN_PROGRESS: 'inProgress',
            SUBMITTED: 'submitted',
            EXPIRED: 'expired',
        }[code];
    }

    async function loadDay(dateText) {
        dayHost.innerHTML = '';
        dayHost.append(h('div', { class: 'card-head' },
            h('h2', { text: `${dateText} 的任务` }),
            h('div', { class: 'grow' }),
            mayFilterMine ? h('button', {
                class: 'btn btn-sm', type: 'button', text: '在任务列表里打开',
                onclick: () => ctx.navigate('/inspect/task'),
            }) : null));

        try {
            const data = await api.get('/api/inspection/tasks', {
                pageNum: 1, pageSize: 50, planDate: dateText,
            });
            const rows = data.records || data.list || [];
            dayHost.append(h('div', { class: 'card-body', html: renderTable({
                columns: [
                    {
                        title: '任务号', key: 'taskCode', width: '130px',
                        render: (row) => `<a href="#/inspect/task/${row.id}" class="mono">${esc(row.taskCode)}</a>`,
                    },
                    { title: '设备', key: 'equipmentName', render: (row) => esc(row.equipmentName) },
                    { title: '计划', key: 'planName', width: '150px', render: (row) => esc(row.planName || '手工发起') },
                    { title: '执行人', key: 'assigneeName', width: '100px', render: (row) => esc(row.assigneeName || '—') },
                    {
                        title: '进度', key: 'finishedItemCount', width: '80px', align: 'right',
                        render: (row) => `${row.finishedItemCount ?? 0}/${row.itemCount ?? 0}`,
                    },
                    { title: '状态', key: 'status', width: '96px', render: (row) => statusTag(INSPECT_STATUS, row.status) },
                    {
                        title: '时限', key: 'slaDueTime', width: '120px',
                        render: (row) => formatDate(row.slaDueTime, true),
                    },
                ],
                rows,
                empty: '这一天没有任务',
            }) }));
        } catch (error) {
            dayHost.append(h('div', { class: 'table-empty', style: 'color:var(--danger)', text: error.message }));
        }
    }

    await load();
}
