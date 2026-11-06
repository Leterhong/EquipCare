/**
 * 用户操作记录（审计日志，只读）。
 *
 * 后端在建单、派单、改权限、删设备等动作上通过 @AuditLog 异步落库，
 * 审计表只允许 INSERT，接口不提供修改与删除，满足「操作留痕不可篡改」要求。
 */

import { api } from '../api.js';
import {
    debounce, esc, formatDate, h, renderPagination, renderTable,
} from '../ui.js';

export async function render(container, ctx) {
    ctx.setTitle('用户操作记录');
    const query = { pageNum: 1, pageSize: 20, username: '', module: '', resultStatus: '', startTime: '', endTime: '' };

    const keyword = h('input', { class: 'input', type: 'search', placeholder: '操作人账号', 'aria-label': '操作人' });
    const moduleInput = h('input', { class: 'input', placeholder: '如 用户 / 角色 / 设备台账', 'aria-label': '模块' });
    const resultSelect = h('select', { class: 'select', 'aria-label': '结果' },
        h('option', { value: '', text: '全部结果' }),
        h('option', { value: 'SUCCESS', text: '成功' }),
        h('option', { value: 'FAIL', text: '失败' }));
    const start = h('input', { class: 'input', type: 'datetime-local', 'aria-label': '开始时间' });
    const end = h('input', { class: 'input', type: 'datetime-local', 'aria-label': '结束时间' });

    const tableHost = h('div', {});
    const pagerHost = h('div', {});
    const summary = h('span', { class: 'hint' });

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '用户操作记录' }),
                h('p', { text: '记录登录、用户与角色变更、设备增删改、文件上传等关键动作；日志只读，不支持修改与删除。' }))),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: '审计日志' }), summary),
            h('div', { class: 'toolbar' },
                h('div', { class: 'field' }, h('label', { text: '操作人' }), keyword),
                h('div', { class: 'field' }, h('label', { text: '模块' }), moduleInput),
                h('div', { class: 'field' }, h('label', { text: '结果' }), resultSelect),
                h('div', { class: 'field' }, h('label', { text: '开始时间' }), start),
                h('div', { class: 'field' }, h('label', { text: '结束时间' }), end),
                h('div', { class: 'spacer' }),
                h('button', { class: 'btn', type: 'button', text: '重置', onclick: () => reset() }),
                h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: () => search() })),
            tableHost,
            pagerHost),
    );

    keyword.addEventListener('input', debounce(() => search(), 400));
    moduleInput.addEventListener('input', debounce(() => search(), 400));
    resultSelect.addEventListener('change', () => search());

    function normalizeTime(value) {
        if (!value) {
            return '';
        }
        return value.replace('T', ' ') + (value.length === 16 ? ':00' : '');
    }

    function search() {
        query.username = keyword.value.trim();
        query.module = moduleInput.value.trim();
        query.resultStatus = resultSelect.value;
        query.startTime = normalizeTime(start.value);
        query.endTime = normalizeTime(end.value);
        query.pageNum = 1;
        load();
    }

    function reset() {
        keyword.value = '';
        moduleInput.value = '';
        resultSelect.value = '';
        start.value = '';
        end.value = '';
        search();
    }

    async function load() {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        try {
            const data = await api.get('/api/system/audit-logs', query);
            summary.textContent = `共 ${data.total} 条记录`;
            tableHost.innerHTML = renderTable({
                columns: [
                    { title: '时间', key: 'createTime', width: '150px', render: (row) => formatDate(row.createTime, true) },
                    { title: '操作人', key: 'username', width: '120px', render: (row) => `<span class="mono">${esc(row.username || '—')}</span>` },
                    { title: '模块 / 动作', key: 'module', width: '150px', render: (row) => `${esc(row.module)} · ${esc(row.action)}` },
                    {
                        title: '请求', key: 'requestUri',
                        render: (row) => `<span class="mono" style="font-size:11.5px">${esc(row.requestMethod || '')} ${esc(row.requestUri || '')}</span>`,
                    },
                    {
                        title: '结果', key: 'resultStatus', width: '80px',
                        render: (row) => (row.resultStatus === 'SUCCESS'
                            ? '<span class="tag tag-green">成功</span>'
                            : `<span class="tag tag-red" title="${esc(row.errorMsg || '')}">失败</span>`),
                    },
                    { title: 'IP', key: 'ip', width: '120px', render: (row) => esc(row.ip || '—') },
                    { title: '耗时', key: 'costMs', width: '80px', align: 'right', render: (row) => (row.costMs !== null && row.costMs !== undefined ? `${row.costMs} ms` : '—') },
                ],
                rows: data.records || [],
                empty: '没有符合条件的操作记录',
            });

            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(
                { total: data.total, pageNum: data.pageNum, pageSize: data.pageSize },
                { onChange: (page) => { query.pageNum = page; load(); } },
            ));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    await load();
}
