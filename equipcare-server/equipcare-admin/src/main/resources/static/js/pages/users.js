/**
 * 用户管理：新增、编辑、启用/禁用、重置密码、删除、角色关联。
 *
 * 说明：所有按钮都按权限码显示，但真正的拦截在后端；
 * 即使有人在浏览器控制台直接调接口，缺少 system:user:manage 也会返回 403。
 */

import { api, auth } from '../api.js';
import {
    confirmDialog, debounce, esc, flattenTree, formatDate, guard, h, modal,
    renderPagination, renderTable, toast, toastError,
} from '../ui.js';

const STATUS_LABEL = { 1: ['启用', 'tag-green'], 0: ['禁用', 'tag-gray'] };

export async function render(container, ctx) {
    const canManage = auth.can('system:user:manage');
    const query = { pageNum: 1, pageSize: 10, keyword: '', status: '', deptId: '' };

    const [depts, roles] = await Promise.all([
        api.get('/api/system/depts/tree').catch(() => []),
        api.get('/api/system/roles').catch(() => []),
    ]);

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '用户管理' }),
            h('p', { text: '账号由管理员创建并分配角色；禁用或改密后该用户全部会话立即失效。' })),
        h('div', { class: 'page-actions' },
            canManage ? h('button', { class: 'btn btn-primary', type: 'button', text: '＋ 新增用户', onclick: () => openCreate() }) : null,
            h('button', {
                class: 'btn', type: 'button', text: '用户操作记录',
                onclick: () => ctx.navigate('/audit'),
            })),
    );

    const keyword = h('input', { class: 'input', type: 'search', placeholder: '登录名 / 姓名 / 手机号', 'aria-label': '关键字' });
    const statusSelect = h('select', { class: 'select', 'aria-label': '状态' },
        h('option', { value: '', text: '全部状态' }),
        h('option', { value: '1', text: '启用' }),
        h('option', { value: '0', text: '禁用' }));
    const deptSelect = h('select', { class: 'select', 'aria-label': '部门' }, h('option', { value: '', text: '全部部门' }));
    flattenTree(depts || []).forEach((node) => {
        deptSelect.append(h('option', { value: node.id, text: `${'　'.repeat(node.level)}${node.deptName}` }));
    });

    const toolbar = h('div', { class: 'toolbar' },
        h('div', { class: 'field grow' }, h('label', { text: '关键字' }), keyword),
        h('div', { class: 'field' }, h('label', { text: '状态' }), statusSelect),
        h('div', { class: 'field' }, h('label', { text: '部门' }), deptSelect),
        h('div', { class: 'spacer' }),
        h('button', {
            class: 'btn', type: 'button', text: '重置',
            onclick: () => {
                keyword.value = '';
                statusSelect.value = '';
                deptSelect.value = '';
                search();
            },
        }),
        h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: () => search() }),
    );

    const tableHost = h('div', {});
    const pagerHost = h('div', {});
    const summary = h('span', { class: 'hint' });

    container.append(head, h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '账号列表' }), summary),
        toolbar, tableHost, pagerHost));

    keyword.addEventListener('input', debounce(() => search(), 400));
    statusSelect.addEventListener('change', () => search());
    deptSelect.addEventListener('change', () => search());

    function search() {
        query.keyword = keyword.value.trim();
        query.status = statusSelect.value;
        query.deptId = deptSelect.value;
        query.pageNum = 1;
        load();
    }

    async function load() {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        try {
            const data = await api.get('/api/system/users', query);
            summary.textContent = `共 ${data.total} 个账号`;
            const rows = data.records || [];

            tableHost.innerHTML = renderTable({
                columns: [
                    { title: '登录名', key: 'username', width: '130px', render: (row) => `<span class="mono">${esc(row.username)}</span>` },
                    { title: '姓名', key: 'realName' },
                    { title: '部门', key: 'deptName', render: (row) => esc(row.deptName || '—') },
                    { title: '手机号', key: 'phone', width: '120px', render: (row) => esc(row.phone || '—') },
                    {
                        title: '角色', key: 'roleNames',
                        render: (row) => (row.roleNames || []).map((name) => `<span class="tag tag-blue plain">${esc(name)}</span>`).join(' ') || '—',
                    },
                    {
                        title: '状态', key: 'status', width: '80px',
                        render: (row) => {
                            const meta = STATUS_LABEL[row.status] || ['未知', 'tag-gray'];
                            return `<span class="tag ${meta[1]}">${meta[0]}</span>`;
                        },
                    },
                    {
                        title: '最近登录', key: 'lastLoginTime', width: '130px',
                        render: (row) => (row.lastLoginTime ? formatDate(row.lastLoginTime, true) : '—'),
                    },
                    {
                        title: '操作', key: 'id', width: '215px', align: 'right',
                        render: (row) => {
                            if (!canManage) {
                                return '<span style="color:var(--text-muted)">只读</span>';
                            }
                            const toggle = row.status === 1
                                ? `<button class="btn btn-sm btn-ghost" data-action="disable" data-id="${row.id}">禁用</button>`
                                : `<button class="btn btn-sm btn-ghost" data-action="enable" data-id="${row.id}">启用</button>`;
                            return [
                                `<button class="btn btn-sm btn-ghost" data-action="edit" data-id="${row.id}">编辑</button>`,
                                `<button class="btn btn-sm btn-ghost" data-action="reset" data-id="${row.id}">重置</button>`,
                                toggle,
                                `<button class="btn btn-sm btn-danger" data-action="delete" data-id="${row.id}">删除</button>`,
                            ].join('');
                        },
                    },
                ],
                rows,
                empty: '没有符合条件的账号',
            });

            tableHost.querySelectorAll('button[data-action]').forEach((button) => {
                const row = rows.find((item) => String(item.id) === button.dataset.id);
                button.addEventListener('click', () => {
                    const action = button.dataset.action;
                    if (action === 'edit') {
                        openEdit(row);
                    } else if (action === 'reset') {
                        openReset(row);
                    } else if (action === 'enable' || action === 'disable') {
                        changeStatus(row, action === 'enable' ? 1 : 0);
                    } else if (action === 'delete') {
                        removeUser(row);
                    }
                });
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

    /* ----------------------------- 新增 ----------------------------- */

    function openCreate() {
        const form = userForm(null);
        modal({
            title: '新增用户',
            body: form.node,
            width: 'wide',
            footer: [
                { label: '取消' },
                {
                    label: '创建账号',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        const payload = form.collect(true);
                        if (!payload) {
                            return false;
                        }
                        let ok = false;
                        await guard(button, async () => {
                            await api.post('/api/system/users', payload);
                            toast('账号已创建', 'success');
                            ok = true;
                        });
                        if (ok) {
                            load();
                        }
                        return ok;
                    },
                },
            ],
        });
    }

    /* ----------------------------- 编辑 ----------------------------- */

    async function openEdit(row) {
        let detail = row;
        try {
            detail = await api.get(`/api/system/users/${row.id}`);
        } catch (error) {
            toastError(error);
        }
        const form = userForm(detail);
        modal({
            title: `编辑用户 · ${detail.username}`,
            body: form.node,
            width: 'wide',
            footer: [
                { label: '取消' },
                {
                    label: '保存',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        const payload = form.collect(false);
                        if (!payload) {
                            return false;
                        }
                        let ok = false;
                        await guard(button, async () => {
                            await api.put(`/api/system/users/${detail.id}`, payload);
                            toast('用户信息已保存', 'success');
                            ok = true;
                        });
                        if (ok) {
                            load();
                        }
                        return ok;
                    },
                },
            ],
        });
    }

    function userForm(detail) {
        const isEdit = Boolean(detail);

        const username = h('input', {
            class: 'input', name: 'username', maxlength: 30,
            placeholder: '字母开头，4-30 位字母/数字/下划线',
            readonly: isEdit ? true : null,
        });
        username.value = detail ? detail.username : '';

        const password = h('input', { class: 'input', name: 'password', type: 'password', placeholder: '8-32 位，含字母与数字' });
        const realName = h('input', { class: 'input', name: 'realName', maxlength: 50 });
        realName.value = detail ? detail.realName || '' : '';
        const phone = h('input', { class: 'input', name: 'phone', maxlength: 11, placeholder: '11 位手机号，可留空' });
        phone.value = detail ? detail.phone || '' : '';
        const email = h('input', { class: 'input', name: 'email', type: 'email', maxlength: 100 });
        email.value = detail ? detail.email || '' : '';
        const skillTags = h('input', { class: 'input', name: 'skillTags', maxlength: 200, placeholder: '如 电气,制冷，派单时参考' });
        skillTags.value = detail ? detail.skillTags || '' : '';

        const dept = h('select', { class: 'select', name: 'deptId' }, h('option', { value: '', text: '不指定' }));
        flattenTree(depts || []).forEach((node) => {
            dept.append(h('option', { value: node.id, text: `${'　'.repeat(node.level)}${node.deptName}` }));
        });
        if (detail && detail.deptId) {
            dept.value = String(detail.deptId);
        }

        const roleBoxes = (roles || []).map((role) => {
            const checked = detail && (detail.roleIds || []).includes(role.id);
            const input = h('input', { type: 'checkbox', value: role.id, name: 'roleIds' });
            input.checked = !!checked;
            return h('label', { class: 'check-line', style: 'border:1px solid var(--border);border-radius:6px;padding:6px 10px;flex:1' },
                input,
                h('span', {},
                    h('b', { text: role.roleName }),
                    h('code', { style: 'margin-left:6px', text: role.roleCode })));
        });

        const node = h('form', { novalidate: true },
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { class: 'required', text: '登录名' }), username),
                h('div', { class: 'field' }, h('label', { class: 'required', text: '姓名' }), realName),
                isEdit ? null : h('div', { class: 'field' },
                    h('label', { class: 'required', text: '初始密码' }), password,
                    h('span', { class: 'help', text: '创建后请通知用户首次登录自行修改' })),
                h('div', { class: 'field' }, h('label', { text: '手机号' }), phone),
                h('div', { class: 'field' }, h('label', { text: '邮箱' }), email),
                h('div', { class: 'field' }, h('label', { text: '部门' }), dept),
                h('div', { class: 'field span-2' }, h('label', { text: '技能标签' }), skillTags),
                h('div', { class: 'field span-2' },
                    h('label', { class: 'required', text: '角色（至少一个）' }),
                    h('div', { style: 'display:flex;flex-wrap:wrap;gap:8px' }, roleBoxes)),
            ),
            h('div', { class: 'alert alert-info', style: 'margin-top:14px', text: '登录名创建后不可修改；调整角色会立即刷新该用户的接口权限。' }),
        );

        function collect(requirePassword) {
            const roleIds = Array.from(node.querySelectorAll('input[name="roleIds"]:checked')).map((input) => Number(input.value));
            if (!realName.value.trim()) {
                toast('请填写姓名', 'error');
                return null;
            }
            if (roleIds.length === 0) {
                toast('请至少选择一个角色', 'error');
                return null;
            }
            if (requirePassword) {
                if (!/^[A-Za-z][A-Za-z0-9_]{3,29}$/.test(username.value.trim())) {
                    toast('登录名需以字母开头，4-30 位字母/数字/下划线', 'error');
                    return null;
                }
                if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(password.value)) {
                    toast('密码需 8-32 位且同时包含字母与数字', 'error');
                    return null;
                }
            }
            const payload = {
                realName: realName.value.trim(),
                phone: phone.value.trim() || '',
                email: email.value.trim() || '',
                deptId: dept.value ? Number(dept.value) : null,
                skillTags: skillTags.value.trim() || null,
                roleIds,
            };
            if (requirePassword) {
                payload.username = username.value.trim();
                payload.password = password.value;
            }
            return payload;
        }

        return { node, collect };
    }

    /* ----------------------------- 其它操作 ----------------------------- */

    async function changeStatus(row, status) {
        const action = status === 1 ? '启用' : '禁用';
        const ok = await confirmDialog({
            title: `${action}账号`,
            message: `确认${action} <b>${esc(row.realName)}（${esc(row.username)}）</b>？${status === 0 ? '<br>禁用后该用户全部会话立即失效。' : ''}`,
            confirmText: action,
            danger: status === 0,
        });
        if (!ok) {
            return;
        }
        try {
            await api.patch(`/api/system/users/${row.id}/status?status=${status}`);
            toast(`已${action}`, 'success');
            load();
        } catch (error) {
            toastError(error);
        }
    }

    function openReset(row) {
        const password = h('input', { class: 'input', type: 'password', placeholder: '8-32 位，含字母与数字' });
        const body = h('div', {},
            h('div', { class: 'alert alert-warn', text: '重置后该用户全部会话立即失效，需用新密码重新登录。' }),
            h('div', { class: 'field' }, h('label', { class: 'required', text: '新密码' }), password),
        );
        modal({
            title: `重置密码 · ${row.username}`,
            body,
            footer: [
                { label: '取消' },
                {
                    label: '确认重置',
                    type: 'danger',
                    onClick: async ({ button }) => {
                        if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(password.value)) {
                            toast('密码需 8-32 位且同时包含字母与数字', 'error');
                            return false;
                        }
                        let ok = false;
                        await guard(button, async () => {
                            await api.put(`/api/system/users/${row.id}/password/reset`, { newPassword: password.value });
                            toast('密码已重置', 'success');
                            ok = true;
                        });
                        return ok;
                    },
                },
            ],
        });
    }

    async function removeUser(row) {
        const ok = await confirmDialog({
            title: '删除用户',
            message: `确认删除 <b>${esc(row.realName)}（${esc(row.username)}）</b>？<br>逻辑删除，登录名会被释放但审计记录保留。`,
            confirmText: '确认删除',
            danger: true,
        });
        if (!ok) {
            return;
        }
        try {
            await api.del(`/api/system/users/${row.id}`);
            toast('用户已删除', 'success');
            load();
        } catch (error) {
            toastError(error);
        }
    }

    await load();
}
