/**
 * 设备分类管理：树形展示 + 新增 / 编辑 / 删除。
 *
 * 类别编码同时是资产编号前缀（如 KT-202610-0001），因此编码规则为 2-10 位大写字母或数字。
 */

import { api, auth } from '../api.js';
import { confirmDialog, esc, guard, h, modal, toast, toastError } from '../ui.js';

export async function render(container, ctx) {
    const canManage = auth.can('asset:category:manage');
    const tableHost = h('div', {});
    const summary = h('span', { class: 'hint' });
    let tree = [];

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '设备分类' }),
                h('p', { text: '分类决定资产编号前缀与默认巡检模板；被设备引用的分类不可删除。' })),
            h('div', { class: 'page-actions' },
                canManage ? h('button', {
                    class: 'btn btn-primary', type: 'button', text: '＋ 新增分类',
                    onclick: () => openForm(null, null),
                }) : null)),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: '分类树' }), summary),
            tableHost),
    );

    async function load() {
        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:50%"></div></div>';
        try {
            tree = await api.get('/api/asset/categories/tree');
            renderTree();
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function renderTree() {
        const flat = flatten(tree);
        summary.textContent = `共 ${flat.length} 个分类`;

        const rows = flat.map(({ node, level }) => {
            const actions = [];
            if (canManage && level < 3) {
                actions.push(`<button class="btn btn-sm btn-ghost" data-action="add" data-id="${node.id}">新增子级</button>`);
            }
            if (canManage) {
                actions.push(`<button class="btn btn-sm btn-ghost" data-action="edit" data-id="${node.id}">编辑</button>`);
                if (node.equipmentCount === 0) {
                    actions.push(`<button class="btn btn-sm btn-danger" data-action="delete" data-id="${node.id}">删除</button>`);
                }
            }
            const indent = level > 0 ? `<span style="color:var(--border-strong);margin-right:6px">${'└'.padStart(level * 2, '　')}</span>` : '';
            return `
                <tr>
                    <td><span class="mono">${esc(node.categoryCode)}</span></td>
                    <td>${indent}${esc(node.categoryName)}</td>
                    <td style="text-align:right">${node.equipmentCount ?? 0}</td>
                    <td>${node.maintainCycleDays ? `${node.maintainCycleDays} 天` : '—'}</td>
                    <td>${node.status === 1 ? '<span class="tag tag-green">启用</span>' : '<span class="tag tag-gray">停用</span>'}</td>
                    <td style="text-align:right">${actions.join(' ') || '<span style="color:var(--text-muted)">只读</span>'}</td>
                </tr>`;
        }).join('');

        tableHost.innerHTML = `<div class="table-wrap"><table class="data"><thead><tr>
            <th style="width:140px">分类编码</th><th>分类名称</th><th style="width:100px;text-align:right">设备数</th>
            <th style="width:110px">保养周期</th><th style="width:90px">状态</th><th style="width:250px;text-align:right">操作</th>
        </tr></thead><tbody>${rows || '<tr><td colspan="6" class="table-empty">暂无分类</td></tr>'}</tbody></table></div>`;

        tableHost.querySelectorAll('button[data-action]').forEach((button) => {
            const node = flat.find((item) => String(item.node.id) === button.dataset.id).node;
            button.addEventListener('click', () => {
                const action = button.dataset.action;
                if (action === 'add') {
                    openForm(null, node.id);
                } else if (action === 'edit') {
                    openForm(node, null);
                } else {
                    remove(node);
                }
            });
        });
    }

    function flatten(nodes, level = 0, result = []) {
        (nodes || []).forEach((node) => {
            result.push({ node, level });
            flatten(node.children, level + 1, result);
        });
        return result;
    }

    function openForm(node, parentId) {
        const isEdit = Boolean(node);
        const parentLabel = isEdit
            ? (findName(tree, node.parentId) || '顶级分类')
            : (parentId ? findName(tree, parentId) : '顶级分类');

        const code = h('input', { class: 'input', maxlength: 10, placeholder: '如 KT、ELEC，2-10 位大写字母/数字' });
        code.value = node ? node.categoryCode : '';
        const name = h('input', { class: 'input', maxlength: 60 });
        name.value = node ? node.categoryName : '';
        const cycle = h('input', { class: 'input', type: 'number', min: '1', max: '3650', placeholder: '如 90' });
        cycle.value = node && node.maintainCycleDays ? node.maintainCycleDays : '';
        const sort = h('input', { class: 'input', type: 'number', min: '0' });
        sort.value = node ? node.sort : 99;
        const status = h('select', { class: 'select' },
            h('option', { value: '1', text: '启用' }),
            h('option', { value: '0', text: '停用' }));
        status.value = node ? String(node.status) : '1';

        const body = h('form', { novalidate: true },
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { text: '上级分类' }),
                    h('input', { class: 'input', value: parentLabel, readonly: true })),
                h('div', { class: 'field' }, h('label', { class: 'required', text: '分类编码' }), code,
                    h('span', { class: 'help', text: '创建后不建议修改，资产编号已按该前缀生成' })),
                h('div', { class: 'field' }, h('label', { class: 'required', text: '分类名称' }), name),
                h('div', { class: 'field' }, h('label', { text: '保养周期（天）' }), cycle),
                h('div', { class: 'field' }, h('label', { text: '排序号' }), sort),
                h('div', { class: 'field' }, h('label', { text: '状态' }), status),
            ));

        modal({
            title: isEdit ? `编辑分类 · ${node.categoryName}` : (parentId ? '新增子分类' : '新增分类'),
            body,
            footer: [
                { label: '取消' },
                {
                    label: isEdit ? '保存' : '创建',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        if (!/^[A-Z][A-Z0-9]{1,9}$/.test(code.value.trim().toUpperCase())) {
                            toast('分类编码需为 2-10 位大写字母或数字，且以字母开头', 'error');
                            return false;
                        }
                        if (!name.value.trim()) {
                            toast('请填写分类名称', 'error');
                            return false;
                        }
                        const payload = {
                            categoryCode: code.value.trim().toUpperCase(),
                            categoryName: name.value.trim(),
                            parentId: isEdit ? node.parentId : (parentId || 0),
                            maintainCycleDays: cycle.value === '' ? null : Number(cycle.value),
                            sort: sort.value === '' ? 99 : Number(sort.value),
                            status: Number(status.value),
                            defaultTemplateId: node ? node.defaultTemplateId : null,
                        };
                        let ok = false;
                        await guard(button, async () => {
                            if (isEdit) {
                                await api.put(`/api/asset/categories/${node.id}`, payload);
                            } else {
                                await api.post('/api/asset/categories', payload);
                            }
                            toast(isEdit ? '分类已更新' : '分类已创建', 'success');
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

    async function remove(node) {
        const ok = await confirmDialog({
            title: '删除分类',
            message: `确认删除分类 <b>${esc(node.categoryName)}</b>？<br>存在子分类或已被设备引用时，后端会拒绝删除。`,
            confirmText: '确认删除',
            danger: true,
        });
        if (!ok) {
            return;
        }
        try {
            await api.del(`/api/asset/categories/${node.id}`);
            toast('分类已删除', 'success');
            load();
        } catch (error) {
            toastError(error);
        }
    }

    function findName(nodes, id) {
        for (const node of nodes || []) {
            if (node.id === id) {
                return node.categoryName;
            }
            const found = findName(node.children, id);
            if (found) {
                return found;
            }
        }
        return null;
    }

    await load();
}
