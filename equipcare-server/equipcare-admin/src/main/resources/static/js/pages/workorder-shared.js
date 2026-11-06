/**
 * 工单中心共用组件。
 *
 * 只放「多个页面都要用」的东西：键值网格、照片上传、原因弹层、派单弹层、状态标签。
 * 各页面自己的业务编排留在各自文件里，避免这里变成一个什么都往里塞的工具箱。
 */

import { api } from '../api.js';
import {
    WORK_ORDER_PRIORITY, WORK_ORDER_STATUS, esc, h, modal, statusTag, toast, toastError,
} from '../ui.js';

/** 键值对格子：value 支持 HTML 片段 */
export function kv(label, valueHtml) {
    return h('div', {},
        h('div', { class: 'kv-label', text: label }),
        h('div', { class: 'kv-value', html: valueHtml === undefined || valueHtml === null || valueHtml === ''
            ? '—' : String(valueHtml) }));
}

export function woStatus(code) {
    return statusTag(WORK_ORDER_STATUS, code);
}

export function priorityTag(code) {
    return statusTag(WORK_ORDER_PRIORITY, code);
}

/** 照片缩略图：按文件 ID 取二进制，转 objectURL 展示 */
export function attachmentThumbs(files, { empty = '无照片' } = {}) {
    const wrap = h('div', { class: 'photo-strip' });
    if (!files || files.length === 0) {
        wrap.append(h('span', { class: 'hint', text: empty }));
        return wrap;
    }
    files.forEach((file) => {
        const img = h('img', { class: 'photo-thumb', alt: file.fileName || '现场照片' });
        api.objectUrl(`/api/files/${file.id}`)
            .then((url) => { img.src = url; })
            .catch(() => { img.alt = '照片加载失败'; });
        wrap.append(h('div', { class: 'photo-cell' }, img));
    });
    return wrap;
}

/**
 * 照片上传器。
 * @returns {{node: HTMLElement, ids: () => string, count: () => number}}
 */
export function photoPicker({ label = '现场照片', max = 6, bizType = 'WORK_ORDER' } = {}) {
    const ids = [];
    const list = h('div', { class: 'photo-strip' });
    const input = h('input', { type: 'file', accept: 'image/*', style: 'display:none' });

    input.addEventListener('change', async () => {
        const file = input.files && input.files[0];
        input.value = '';
        if (!file) {
            return;
        }
        if (ids.length >= max) {
            toast(`最多上传 ${max} 张`, 'error');
            return;
        }
        try {
            const uploaded = await api.upload(file, { bizType });
            ids.push(uploaded.fileId);
            paint();
        } catch (error) {
            toastError(error);
        }
    });

    function paint() {
        list.innerHTML = '';
        ids.forEach((id, index) => {
            const img = h('img', { class: 'photo-thumb', alt: '待提交照片' });
            api.objectUrl(`/api/files/${id}`)
                .then((url) => { img.src = url; })
                .catch(() => { img.alt = '照片加载失败'; });
            list.append(h('div', { class: 'photo-cell' }, img,
                h('button', {
                    class: 'icon-btn danger', type: 'button', title: '移除', 'aria-label': '移除',
                    onclick: () => {
                        ids.splice(index, 1);
                        paint();
                    },
                }, '×')));
        });
        if (ids.length < max) {
            list.append(h('button', {
                class: 'btn btn-sm', type: 'button', text: '＋ 上传照片',
                onclick: () => input.click(),
            }));
        }
    }

    paint();
    const node = h('div', { class: 'field' }, h('label', { text: label }), list, input);
    return { node, ids: () => ids.join(','), count: () => ids.length };
}

/**
 * 带原因的确认弹层（拒接 / 撤回 / 取消 / 验收不通过共用）。
 */
export function reasonModal({
    title, label, minLength = 5, placeholder = '', confirmText = '提交', danger = true, onConfirm,
}) {
    const input = h('textarea', { class: 'input', rows: '3', placeholder: placeholder || `不少于 ${minLength} 个字` });
    const tip = h('div', { class: 'hint', text: `至少 ${minLength} 个字，会写进工单时间线。` });
    modal({
        title,
        body: h('div', {}, h('div', { class: 'field' }, h('label', { text: label }), input), tip),
        footer: [
            { label: '取消' },
            {
                label: confirmText,
                type: danger ? 'danger' : 'primary',
                onClick: async ({ button }) => {
                    const text = input.value.trim();
                    if (text.length < minLength) {
                        toast(`请填写不少于 ${minLength} 个字`, 'error');
                        return false;
                    }
                    button.disabled = true;
                    try {
                        await onConfirm(text);
                        toast('操作成功', 'success');
                    } catch (error) {
                        toastError(error);
                        button.disabled = false;
                        return false;
                    }
                    return true;
                },
            },
        ],
    });
}

/**
 * 派单弹层：维修人 + 优先级 + 完成时限 + 派单说明。
 *
 * @param {object} order 工单
 * @param {Array} assignees 可选维修人
 * @param {Function} onConfirm 收到 payload
 */
export function dispatchModal({ order, assignees, onConfirm, title = '派单', aiSuggest = false }) {
    const assigneeSelect = h('select', { class: 'input' },
        h('option', { value: '', text: '请选择维修人' }),
        ...assignees.map((u) => h('option', {
            value: String(u.id),
            text: `${u.realName || u.username}（在办 ${u.pendingCount ?? 0}）`,
        })));
    const prioritySelect = h('select', { class: 'input' },
        h('option', { value: 'HIGH', text: '高' }),
        h('option', { value: 'NORMAL', text: '中' }),
        h('option', { value: 'LOW', text: '低' }));
    prioritySelect.value = order.priority || 'NORMAL';
    const hoursInput = h('input', { class: 'input', type: 'number', min: '1', max: '720', placeholder: '留空按故障等级默认时限' });
    const remarkInput = h('textarea', { class: 'input', rows: '2', placeholder: '派单说明，会写进派单轨迹（可选）' });

    // AI 推荐区：aiSuggest=true 且接口可用时显示「AI 推荐维修人」按钮，
    // 点击拉取 TOP3 推荐（同类别经验 + 当前负荷），点击候选即回填表单
    const aiBox = h('div', { class: 'ai-suggest-box', hidden: true });
    const aiField = aiSuggest ? h('div', { class: 'field grow' },
        h('button', {
            class: 'btn btn-sm', type: 'button', text: '⚡ AI 推荐维修人',
            onclick: async ({ target }) => {
                target.disabled = true;
                target.textContent = 'AI 分析中…';
                try {
                    const list = await api.post(`/api/ai/dispatch-suggest/${order.id}`);
                    if (!list.length) {
                        toast('AI 未给出推荐（暂无候选维修工）', 'warn');
                    } else {
                        aiBox.hidden = false;
                        aiBox.innerHTML = '';
                        for (const s of list) {
                            aiBox.append(h('div', {
                                class: 'ai-suggest-item',
                                onclick: () => {
                                    assigneeSelect.value = String(s.userId);
                                    toast(`已选择 ${s.realName}，请在上方确认派单参数`, 'success');
                                },
                            },
                            h('div', { class: 'stack-head' },
                                h('b', { text: s.realName || s.username || ('用户' + s.userId) }),
                                h('span', { class: 'cell-sub', text: `同类别修复 ${s.categoryRepaired ?? 0} · 累计 ${s.totalRepaired ?? 0} · 在办 ${s.activeLoad ?? 0}` })),
                            h('div', { class: 'cell-sub', text: s.reason || '' })));
                        }
                    }
                } catch (error) {
                    toastError(error);
                } finally {
                    target.disabled = false;
                    target.textContent = '⚡ AI 推荐维修人';
                }
            },
        }), aiBox) : null;

    modal({
        title: `${title} · ${order.orderNo}`,
        body: h('div', { class: 'form-grid' },
            h('div', { class: 'field' }, h('label', { text: '维修人' }), assigneeSelect),
            aiField,
            h('div', { class: 'field' }, h('label', { text: '维修优先级' }), prioritySelect),
            h('div', { class: 'field' }, h('label', { text: '完成时限（小时）' }), hoursInput),
            h('div', { class: 'field grow' }, h('label', { text: '派单说明' }), remarkInput)),
        footer: [
            { label: '取消' },
            {
                label: '确认派单',
                type: 'primary',
                onClick: async ({ button }) => {
                    if (!assigneeSelect.value) {
                        toast('请选择维修人', 'error');
                        return false;
                    }
                    button.disabled = true;
                    try {
                        await onConfirm({
                            assigneeId: Number(assigneeSelect.value),
                            priority: prioritySelect.value,
                            resolveHours: hoursInput.value ? Number(hoursInput.value) : null,
                            reason: remarkInput.value.trim() || null,
                        });
                        toast('已派单', 'success');
                    } catch (error) {
                        toastError(error);
                        button.disabled = false;
                        return false;
                    }
                    return true;
                },
            },
        ],
    });
}

/** 加载可选维修人（带在办工单数） */
export function loadAssignees() {
    return api.get('/api/workorder/assignees');
}

/** 加载设备下拉数据（首页 100 台，够现场报修用；更多请到设备列表查） */
export async function loadEquipments(keyword = '') {
    const data = await api.get('/api/asset/equipment', { pageNum: 1, pageSize: 100, keyword: keyword || undefined });
    return data.records || [];
}

/** 单个工单详情，统一错误提示 */
export async function loadOrder(id) {
    return api.get(`/api/workorder/${id}`);
}

export function orderLink(order, ctx) {
    return `<a class="link" href="#/workorder/${order.id}">${esc(order.orderNo)}</a>`;
}
