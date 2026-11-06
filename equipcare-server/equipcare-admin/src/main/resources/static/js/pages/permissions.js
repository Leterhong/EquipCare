/**
 * 权限管理（只读）：展示系统全部权限码及其被授权角色数。
 *
 * 权限码与后端 @PreAuthorize 字符串一一对应，运行期不允许改码，
 * 否则会出现「界面能点、接口 403」的不一致，因此本页只读。
 */

import { api, auth } from '../api.js';
import { debounce, esc, h, renderTable, toastError } from '../ui.js';

const TYPE_META = {
    MENU: { label: '菜单', tag: 'tag-blue' },
    BUTTON: { label: '按钮', tag: 'tag-purple' },
    API: { label: '接口', tag: 'tag-gray' },
};

export async function render(container, ctx) {
    ctx.setTitle('权限管理');
    let permissions = [];
    try {
        permissions = await api.get('/api/system/permissions');
    } catch (error) {
        toastError(error);
    }

    const counts = { ALL: permissions.length, MENU: 0, BUTTON: 0, API: 0 };
    permissions.forEach((item) => {
        counts[item.type] = (counts[item.type] || 0) + 1;
    });

    const keyword = h('input', { class: 'input', type: 'search', placeholder: '权限码 / 名称', 'aria-label': '搜索权限' });
    const typeSelect = h('select', { class: 'select', 'aria-label': '类型' },
        h('option', { value: '', text: '全部类型' }),
        h('option', { value: 'MENU', text: '菜单' }),
        h('option', { value: 'BUTTON', text: '按钮' }),
        h('option', { value: 'API', text: '接口' }));

    const stats = h('div', { class: 'grid-3', style: 'margin-bottom:16px' },
        stat('权限总数', counts.ALL),
        stat('菜单权限', counts.MENU),
        stat('按钮 / 接口', counts.BUTTON + counts.API));

    const tableHost = h('div', {});
    const summary = h('span', { class: 'hint' });

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '权限管理' }),
                h('p', { text: '权限清单由代码与初始化脚本维护；角色授权在「角色管理 → 配置权限」中完成。' })),
            h('div', { class: 'page-actions' },
                h('button', {
                    class: 'btn', type: 'button', text: '去配置角色权限',
                    onclick: () => ctx.navigate('/roles'),
                }))),
        stats,
        h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: '权限清单' }),
                h('span', {
                    class: 'hint',
                    text: `当前账号角色：${(auth.user && auth.user.roles || []).join(' / ') || '—'}`,
                })),
            h('div', { class: 'toolbar' },
                h('div', { class: 'field grow' }, h('label', { text: '搜索' }), keyword),
                h('div', { class: 'field' }, h('label', { text: '类型' }), typeSelect),
                h('div', { class: 'spacer' }),
                summary),
            tableHost),
    );

    function stat(label, value) {
        return h('div', { class: 'stat' },
            h('div', { class: 'label', text: label }),
            h('div', { class: 'value', text: value }));
    }

    function apply() {
        const kw = keyword.value.trim().toLowerCase();
        const type = typeSelect.value;
        const filtered = permissions.filter((item) => {
            const matchType = !type || item.type === type;
            const matchKeyword = !kw || item.code.toLowerCase().includes(kw) || (item.name || '').includes(kw);
            return matchType && matchKeyword;
        });
        summary.textContent = `显示 ${filtered.length} / ${permissions.length} 项`;

        tableHost.innerHTML = renderTable({
            columns: [
                { title: '权限码', key: 'code', width: '230px', render: (row) => `<span class="mono">${esc(row.code)}</span>` },
                { title: '名称', key: 'name' },
                {
                    title: '类型', key: 'type', width: '80px',
                    render: (row) => {
                        const meta = TYPE_META[row.type] || { label: row.type, tag: 'tag-gray' };
                        return `<span class="tag ${meta.tag} plain">${meta.label}</span>`;
                    },
                },
                { title: '前端路由', key: 'path', render: (row) => (row.path ? `<span class="mono">${esc(row.path)}</span>` : '—') },
                { title: '排序', key: 'sort', width: '70px', align: 'right' },
                {
                    title: '状态', key: 'status', width: '80px',
                    render: (row) => (row.status === 1
                        ? '<span class="tag tag-green">启用</span>'
                        : '<span class="tag tag-gray">停用</span>'),
                },
                {
                    title: '已授权角色', key: 'roleCount', width: '110px', align: 'right',
                    render: (row) => (row.roleCount > 0 ? `<b>${row.roleCount}</b>` : '<span style="color:var(--text-muted)">0</span>'),
                },
            ],
            rows: filtered,
            empty: '没有匹配的权限项',
        });
    }

    keyword.addEventListener('input', debounce(apply, 200));
    typeSelect.addEventListener('change', apply);
    apply();
}
