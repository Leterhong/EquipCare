/**
 * 角色管理：新增、编辑、删除、权限配置。
 *
 * 权限配置采用「菜单 → 按钮/接口」两级勾选，勾选父级会级联子级；
 * 保存后后端会清除受影响用户的会话，权限立即生效。
 */

import { api, auth } from '../api.js';
import { confirmDialog, esc, guard, h, modal, toast, toastError } from '../ui.js';

const SCOPE_LABEL = { ALL: '全部数据', DEPT: '本部门', SELF: '仅本人' };

export async function render(container, ctx) {
    const canManage = auth.can('system:role:manage');

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '角色管理' }),
            h('p', { text: '角色决定接口权限；内置管理员角色不可修改权限，也不可删除。' })),
        h('div', { class: 'page-actions' },
            canManage ? h('button', { class: 'btn btn-primary', type: 'button', text: '＋ 新增角色', onclick: () => openCreate() }) : null,
            h('button', { class: 'btn', type: 'button', text: '权限清单', onclick: () => ctx.navigate('/permissions') })),
    );

    const listHost = h('div', { class: 'role-list' });
    const summary = h('span', { class: 'hint' });
    const card = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '角色列表' }), summary),
        listHost);
    container.append(head, card);

    async function load() {
        listHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:50%"></div></div>';
        try {
            const roles = await api.get('/api/system/roles');
            summary.textContent = `共 ${roles.length} 个角色`;
            listHost.innerHTML = '';
            roles.forEach((role) => {
                const builtin = role.builtin === 1;
                const actions = h('div', { style: 'display:flex;gap:6px' },
                    canManage ? h('button', {
                        class: 'btn btn-sm btn-ghost', type: 'button', text: '编辑',
                        onclick: () => openEdit(role),
                    }) : null,
                    canManage && !builtin ? h('button', {
                        class: 'btn btn-sm', type: 'button', text: '配置权限',
                        onclick: () => openPermissions(role),
                    }) : null,
                    canManage && !builtin ? h('button', {
                        class: 'btn btn-sm btn-danger', type: 'button', text: '删除',
                        onclick: () => remove(role),
                    }) : null);

                listHost.append(h('div', { class: 'role-row' },
                    h('div', { class: 'grow' },
                        h('div', { style: 'display:flex;align-items:center;gap:8px' },
                            h('span', { class: 'name', text: role.roleName }),
                            builtin ? h('span', { class: 'tag tag-amber plain', text: '内置' }) : null,
                            role.status === 1
                                ? h('span', { class: 'tag tag-green plain', text: '启用' })
                                : h('span', { class: 'tag tag-gray plain', text: '停用' })),
                        h('div', { class: 'code', text: `${role.roleCode} · ${SCOPE_LABEL[role.dataScope] || role.dataScope}` }),
                        role.remark ? h('div', { style: 'font-size:12px;color:var(--text-muted);margin-top:2px', text: role.remark }) : null),
                    h('div', { style: 'font-size:12.5px;color:var(--text-secondary);text-align:right;min-width:120px;line-height:1.7' },
                        h('div', {},
                            h('b', { style: 'color:var(--text)', text: role.userCount ?? 0 }),
                            ' 个用户'),
                        h('div', { text: SCOPE_LABEL[role.dataScope] || role.dataScope })),
                    actions));
            });
        } catch (error) {
            listHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    /* ----------------------------- 新增 / 编辑 ----------------------------- */

    function roleForm(role) {
        const isEdit = Boolean(role);
        const code = h('input', { class: 'input', maxlength: 50, placeholder: '如 EQUIP_LEADER', readonly: isEdit ? true : null });
        code.value = role ? role.roleCode : '';
        const name = h('input', { class: 'input', maxlength: 50 });
        name.value = role ? role.roleName : '';
        const scope = h('select', { class: 'select' },
            h('option', { value: 'ALL', text: '全部数据' }),
            h('option', { value: 'DEPT', text: '本部门数据' }),
            h('option', { value: 'SELF', text: '仅本人数据' }));
        scope.value = role ? role.dataScope : 'SELF';
        const sort = h('input', { class: 'input', type: 'number', min: '0', max: '9999' });
        sort.value = role ? role.sort : 99;
        const remark = h('textarea', { class: 'textarea', maxlength: 200 });
        remark.value = role ? role.remark || '' : '';

        const node = h('form', { novalidate: true },
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { class: 'required', text: '角色编码' }), code,
                    h('span', { class: 'help', text: '大写字母与下划线，创建后不可修改' })),
                h('div', { class: 'field' }, h('label', { class: 'required', text: '角色名称' }), name),
                h('div', { class: 'field' }, h('label', { class: 'text', text: '数据范围' }), scope),
                h('div', { class: 'field' }, h('label', { text: '排序号' }), sort),
                h('div', { class: 'field span-2' }, h('label', { text: '备注' }), remark),
            ));

        function collect() {
            if (!name.value.trim()) {
                toast('请填写角色名称', 'error');
                return null;
            }
            if (!isEdit && !/^[A-Za-z][A-Za-z0-9_]{1,49}$/.test(code.value.trim())) {
                toast('角色编码需以字母开头，2-50 位字母/数字/下划线', 'error');
                return null;
            }
            const payload = {
                roleName: name.value.trim(),
                dataScope: scope.value,
                sort: sort.value === '' ? 99 : Number(sort.value),
                remark: remark.value.trim() || null,
            };
            if (!isEdit) {
                payload.roleCode = code.value.trim().toUpperCase();
            } else {
                payload.roleCode = role.roleCode;
            }
            return payload;
        }

        return { node, collect };
    }

    function openCreate() {
        const form = roleForm(null);
        modal({
            title: '新增角色',
            body: form.node,
            footer: [
                { label: '取消' },
                {
                    label: '创建角色',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        const payload = form.collect();
                        if (!payload) {
                            return false;
                        }
                        let ok = false;
                        await guard(button, async () => {
                            await api.post('/api/system/roles', payload);
                            toast('角色已创建，请继续配置权限', 'success');
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

    function openEdit(role) {
        const form = roleForm(role);
        modal({
            title: `编辑角色 · ${role.roleName}`,
            body: form.node,
            footer: [
                { label: '取消' },
                {
                    label: '保存',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        const payload = form.collect();
                        if (!payload) {
                            return false;
                        }
                        let ok = false;
                        await guard(button, async () => {
                            await api.put(`/api/system/roles/${role.id}`, payload);
                            toast('角色已更新', 'success');
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

    /* ----------------------------- 权限配置 ----------------------------- */

    async function openPermissions(role) {
        let tree;
        let assigned;
        try {
            [tree, assigned] = await Promise.all([
                api.get('/api/system/permissions/tree'),
                api.get(`/api/system/roles/${role.id}`),
            ]);
        } catch (error) {
            toastError(error);
            return;
        }
        const assignedIds = new Set(assigned.permissionIds || []);
        const checked = new Set();

        const body = h('div', {});
        body.append(h('div', { class: 'alert alert-info',
            text: '勾选父级会级联选中该菜单下的全部按钮与接口权限；保存后该角色下用户会被强制重新登录以刷新权限。' }));

        const groups = h('div', { class: 'perm-groups' });
        (tree || []).forEach((root) => {
            const box = h('input', { type: 'checkbox' });
            const children = root.children || [];
            const childBoxes = [];

            const list = h('div', {});
            children.forEach((child) => {
                const childBox = h('input', { type: 'checkbox', value: child.id });
                childBox.checked = assignedIds.has(child.id);
                if (childBox.checked) {
                    checked.add(child.id);
                }
                childBoxes.push(childBox);
                list.append(h('label', { class: 'check-line' }, childBox,
                    h('span', {},
                        h('span', { text: child.name }),
                        h('code', { text: child.code }),
                        h('span', {
                            class: 'tag tag-gray plain',
                            style: 'margin-left:6px',
                            text: child.type === 'MENU' ? '菜单' : child.type === 'BUTTON' ? '按钮' : '接口',
                        }))));
            });

            const allChecked = assignedIds.has(root.id) || (childBoxes.length > 0 && childBoxes.every((item) => item.checked));
            box.checked = allChecked;
            if (box.checked) {
                checked.add(root.id);
            }

            box.addEventListener('change', () => {
                childBoxes.forEach((item) => {
                    item.checked = box.checked;
                    if (box.checked) {
                        checked.add(Number(item.value));
                    } else {
                        checked.delete(Number(item.value));
                    }
                });
                if (box.checked) {
                    checked.add(root.id);
                } else {
                    checked.delete(root.id);
                }
            });
            childBoxes.forEach((item) => {
                item.addEventListener('change', () => {
                    if (item.checked) {
                        checked.add(Number(item.value));
                    } else {
                        checked.delete(Number(item.value));
                    }
                    const all = childBoxes.every((child) => child.checked);
                    box.checked = all;
                    if (all) {
                        checked.add(root.id);
                    } else {
                        checked.delete(root.id);
                    }
                });
            });

            groups.append(h('div', { class: 'perm-group' },
                h('header', {},
                    h('label', { style: 'display:flex;align-items:center;gap:8px;cursor:pointer' }, box,
                        h('span', { text: root.name })),
                    h('span', { style: 'font-weight:400;color:var(--text-muted);font-size:11.5px', text: `${childBoxes.length} 项子权限` })),
                list));
        });
        body.append(groups);

        modal({
            title: `配置权限 · ${role.roleName}`,
            body,
            width: 'wide',
            footer: [
                { label: '取消' },
                {
                    label: '保存权限',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        const ids = Array.from(checked);
                        let ok = false;
                        await guard(button, async () => {
                            await api.put(`/api/system/roles/${role.id}/permissions`, { permissionIds: ids });
                            toast(`已保存 ${ids.length} 项权限`, 'success');
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

    async function remove(role) {
        const ok = await confirmDialog({
            title: '删除角色',
            message: `确认删除角色 <b>${esc(role.roleName)}</b>（${esc(role.roleCode)}）？<br>${role.userCount > 0
                ? `<span style="color:var(--danger)">该角色下仍有 ${role.userCount} 个用户，需先调整用户角色。</span>`
                : '删除后角色与权限的关联会一并清理。'}`,
            confirmText: '确认删除',
            danger: true,
        });
        if (!ok) {
            return;
        }
        try {
            await api.del(`/api/system/roles/${role.id}`);
            toast('角色已删除', 'success');
            load();
        } catch (error) {
            toastError(error);
        }
    }

    await load();
}
