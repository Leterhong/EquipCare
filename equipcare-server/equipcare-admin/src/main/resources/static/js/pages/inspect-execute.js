/**
 * 巡检执行页。
 *
 * 现场填报的核心约束（前端先挡一次，服务端会再校验一遍，以服务端为准）：
 *  1. 每个检查项必须给出「正常 / 异常 / 跳过」之一；
 *  2. 必检项不允许跳过；
 *  3. 判为异常必须写清原因；要求留证的项还必须上传现场照片；
 *  4. 数值项填的实测值超出标准区间时，不允许标成正常。
 */

import { api, auth } from '../api.js';
import {
    ITEM_RESULT, ITEM_TYPE, INSPECT_STATUS, canExecute, esc, formatDate, h, statusTag, toast, toastError,
} from '../ui.js';

export async function render(container, ctx) {
    const id = Number(ctx.params.id);
    const mayExecute = auth.can('inspect:task:execute');

    let detail;
    try {
        detail = await api.get(`/api/inspection/tasks/${id}`);
    } catch (error) {
        container.append(failure(error.message, () => ctx.navigate('/inspect/task')));
        return;
    }

    ctx.setTitle(`执行巡检 · ${detail.taskCode}`);

    if (!mayExecute) {
        container.append(failure('当前账号没有「执行巡检」权限，无法填报。', () => ctx.navigate(`/inspect/task/${id}`)));
        return;
    }
    if (!canExecute(detail.status)) {
        container.append(failure(
            `该任务当前状态为「${INSPECT_STATUS[detail.status]?.label || detail.status}」，不能再填报。`,
            () => ctx.navigate(`/inspect/task/${id}`),
        ));
        return;
    }

    /* ------------------------------ 状态 ------------------------------ */

    const rows = (detail.items || []).map((item) => ({
        item,
        result: item.result || null,
        itemValue: item.itemValue || '',
        remark: item.remark || '',
        skipReason: item.skipReason || '',
        attachments: [],
    }));

    const progressText = h('span', { class: 'hint', text: '' });
    const progressBar = h('div', { class: 'progress' }, h('div', { class: 'progress-fill' }));

    function refreshProgress() {
        const done = rows.filter((row) => row.result).length;
        const abnormal = rows.filter((row) => row.result === 'ABNORMAL').length;
        progressText.textContent = `${done} / ${rows.length} 已判定`
            + (abnormal ? ` · ${abnormal} 项异常` : '');
        progressBar.querySelector('.progress-fill').style.width = `${rows.length ? (done / rows.length) * 100 : 0}%`;
    }

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: detail.equipmentName || '巡检执行' }),
            h('p', { text: `${detail.taskCode} · 计划日 ${formatDate(detail.planDate)} · ${detail.equipment?.assetNo || ''}` })),
        h('div', { class: 'page-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '← 任务详情',
                onclick: () => ctx.navigate(`/inspect/task/${id}`),
            })));

    const summary = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '填报进度' }),
            h('span', { html: statusTag(INSPECT_STATUS, detail.status) })),
        h('div', { class: 'card-body' },
            h('div', { style: 'display:flex;align-items:center;gap:14px;margin-bottom:8px' }, progressBar, progressText),
            h('div', { class: 'hint', text: '逐项给出判定；判为异常时填写原因，要求留证的项必须附现场照片。必检项不能跳过。' })));

    /* --------------------------- 开始巡检 --------------------------- */

    const startCard = h('div', { class: 'card', style: detail.status === 'IN_PROGRESS' ? 'display:none' : '' },
        h('div', { class: 'card-body' },
            h('div', { class: 'hint', text: '开始后会记录到岗时间，用于核对是否按时执行。' }),
            h('button', {
                class: 'btn btn-primary', type: 'button', text: '开始巡检',
                style: 'margin-top:12px',
                onclick: async (event) => {
                    const button = event.currentTarget;
                    button.disabled = true;
                    try {
                        await api.post(`/api/inspection/tasks/${id}/start`, {});
                        toast('已开始巡检，逐项填报后提交', 'success');
                        startCard.style.display = 'none';
                        execSection.style.display = '';
                    } catch (error) {
                        toastError(error);
                        button.disabled = false;
                    }
                },
            })));

    /* --------------------------- 检查项卡片 --------------------------- */

    const execSection = h('div', { style: detail.status === 'IN_PROGRESS' ? '' : 'display:none' });
    const cardsHost = h('div', { class: 'exec-list' });

    /** 卡片元素索引：按 rows 下标保存，用于完成态样式与定位滚动 */
    const cardEls = [];
    rows.forEach((row, index) => {
        const card = execCard(row, index);
        cardEls.push(card);
        cardsHost.append(card);
    });

    /** 定位第一个未判定项（或指定下标），移动端下也可用 */
    function focusItem(index) {
        const target = cardEls[index];
        if (!target) {
            return;
        }
        // jsdom / 旧内核没有 scrollIntoView，特性检测后仅做高亮
        if (typeof target.scrollIntoView === 'function') {
            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        target.classList.add('flash');
        setTimeout(() => target.classList.remove('flash'), 1200);
    }

    function firstUndoneIndex() {
        return rows.findIndex((row) => !row.result);
    }

    const locateBtn = h('button', {
        class: 'btn btn-sm', type: 'button', text: '定位下一项未判定',
        onclick: () => {
            const index = firstUndoneIndex();
            if (index < 0) {
                toast('所有检查项都已判定，可以提交了', 'success');
                return;
            }
            focusItem(index);
        },
    });

    const remarkInput = h('textarea', {
        class: 'input', rows: '2', placeholder: '本次巡检的整体说明（可选）',
    });
    remarkInput.value = detail.remark || '';

    const submitBar = h('div', { class: 'exec-submit' },
        h('div', { class: 'field grow' }, h('label', { text: '整体说明' }), remarkInput),
        h('button', {
            class: 'btn btn-primary', type: 'button', text: '提交巡检结果',
            onclick: (event) => submit(event.currentTarget),
        }));

    execSection.append(h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '检查项' }),
            h('div', { class: 'grow' }),
            locateBtn,
            h('span', { class: 'hint', text: `共 ${rows.length} 项`, style: 'margin-left:10px' })),
        cardsHost), submitBar);

    container.append(head, summary, startCard, execSection);
    refreshProgress();

    /* ------------------------------ 卡片 ------------------------------ */

    function execCard(row, index) {
        const item = row.item;
        const type = ITEM_TYPE[item.itemType] || { label: item.itemType };

        const valueHost = h('div', { class: 'exec-value' });
        const extraHost = h('div', { class: 'exec-extra', style: 'display:none' });
        const resultHost = h('div', { class: 'exec-actions' });

        const buttons = ['NORMAL', 'ABNORMAL', 'SKIPPED'].map((code) => {
            const disabled = code === 'SKIPPED' && item.required === 1;
            const button = h('button', {
                class: `btn btn-sm exec-btn${code === 'ABNORMAL' ? ' danger' : ''}`,
                type: 'button',
                text: ITEM_RESULT[code].label,
                disabled: disabled || undefined,
                title: disabled ? '必检项不能跳过' : '',
                onclick: () => {
                    row.result = code;
                    buttons.forEach((btn) => btn.button.classList.remove('active'));
                    button.classList.add('active');
                    renderValue();
                    renderExtra();
                    refreshProgress();
                    paintCardState();
                },
            });
            const wrap = { button, code };
            resultHost.append(button);
            return wrap;
        });

        /** 卡片完成态视觉：已判定绿序号，异常红左条 */
        function paintCardState() {
            card.classList.toggle('done', Boolean(row.result));
            card.classList.toggle('abnormal', row.result === 'ABNORMAL');
        }

        if (row.result) {
            const active = buttons.find((btn) => btn.code === row.result);
            if (active) {
                active.button.classList.add('active');
            }
        }

        function renderValue() {
            valueHost.innerHTML = '';
            if (row.result === 'SKIPPED') {
                valueHost.append(h('span', { class: 'hint', text: '已跳过，无需填写实测值' }));
                return;
            }
            if (item.itemType === 'BOOLEAN') {
                const yes = String(item.normalValue) === '1';
                const select = h('select', { class: 'select' },
                    h('option', { value: '', text: '请选择' }),
                    h('option', { value: '1', text: '是（正常）' }),
                    h('option', { value: '0', text: '否（异常）' }));
                if (row.itemValue === '1' || row.itemValue === '0') {
                    select.value = row.itemValue;
                }
                select.addEventListener('change', () => { row.itemValue = select.value; });
                valueHost.append(h('div', { class: 'field' },
                    h('label', { text: `实测（正常值：${yes ? '是' : '否'}）` }), select));
                return;
            }
            if (item.itemType === 'ENUM') {
                const options = parseEnum(item.enumOptions);
                const select = h('select', { class: 'select' },
                    h('option', { value: '', text: '请选择' }),
                    ...options.map((option) => h('option', {
                        value: option, text: option, selected: row.itemValue === option,
                    })));
                select.addEventListener('change', () => { row.itemValue = select.value; });
                valueHost.append(h('div', { class: 'field' }, h('label', { text: '选择检查结果' }), select));
                return;
            }
            if (item.itemType === 'NUMBER') {
                const input = h('input', {
                    class: 'input', type: 'number', step: 'any', value: row.itemValue,
                    placeholder: `标准区间 ${item.minValue ?? '−∞'} ~ ${item.maxValue ?? '+∞'}`,
                });
                input.addEventListener('input', () => { row.itemValue = input.value; });
                valueHost.append(h('div', { class: 'field' },
                    h('label', { text: `实测值${item.unit ? '（' + item.unit + '）' : ''}` }), input));
                return;
            }
            const input = h('textarea', { class: 'input', rows: '2', placeholder: '填写检查情况' });
            input.value = row.itemValue;
            input.addEventListener('input', () => { row.itemValue = input.value; });
            valueHost.append(h('div', { class: 'field' }, h('label', { text: '检查情况' }), input));
        }

        function renderExtra() {
            extraHost.innerHTML = '';
            if (row.result === 'ABNORMAL') {
                extraHost.style.display = '';
                const remark = h('textarea', { class: 'input', rows: '2', placeholder: '异常现象、可能原因、现场处置' });
                remark.value = row.remark;
                remark.addEventListener('input', () => { row.remark = remark.value; });
                extraHost.append(h('div', { class: 'field' },
                    h('label', { text: `异常说明（必填${item.photoRequired === 1 ? '，且必须上传现场照片' : ''}）` }), remark));
                extraHost.append(photoField(row, item));
                return;
            }
            if (row.result === 'SKIPPED') {
                extraHost.style.display = '';
                const reason = h('input', {
                    class: 'input', type: 'text', value: row.skipReason, placeholder: '跳过原因，例如设备停机、区域封闭',
                });
                reason.addEventListener('input', () => { row.skipReason = reason.value; });
                extraHost.append(h('div', { class: 'field' }, h('label', { text: '跳过原因（必填）' }), reason));
                return;
            }
            extraHost.style.display = 'none';
        }

        renderValue();
        renderExtra();

        const card = h('div', { class: 'exec-item' },
            h('div', { class: 'exec-head' },
                h('span', { class: 'exec-seq', text: String(index + 1) }),
                h('div', { class: 'exec-title' },
                    h('strong', { text: item.itemName }),
                    h('div', { class: 'exec-tags' },
                        h('span', { class: 'tag tag-gray plain', text: type.label }),
                        item.required === 1 ? h('span', { class: 'tag tag-blue', text: '必检' }) : null,
                        item.photoRequired === 1 ? h('span', { class: 'tag tag-amber', text: '需留证' }) : null)),
                resultHost),
            h('div', { class: 'exec-meta' },
                item.standardRef ? h('span', { text: `标准：${item.standardRef}` }) : null,
                item.checkMethod ? h('span', { text: `方法：${item.checkMethod}` }) : null,
                item.itemType === 'NUMBER'
                    ? h('span', { text: `区间：${item.minValue ?? '−∞'} ~ ${item.maxValue ?? '+∞'} ${item.unit || ''}` })
                    : null,
                item.abnormalRule ? h('span', { class: 'exec-rule', text: `判定：${item.abnormalRule}` }) : null),
            valueHost,
            extraHost);
        if (row.result) {
            paintCardState();
        }
        return card;
    }

    /* ---------------------------- 照片上传 ---------------------------- */

    function photoField(row, item) {
        const list = h('div', { class: 'photo-row' });
        const fileInput = h('input', { type: 'file', accept: 'image/*', multiple: true, style: 'display:none' });

        fileInput.addEventListener('change', async () => {
            const files = Array.from(fileInput.files || []);
            fileInput.value = '';
            for (const file of files) {
                if (file.size > 10 * 1024 * 1024) {
                    toast(`${file.name} 超过 10MB，未上传`, 'error');
                    continue;
                }
                try {
                    const uploaded = await api.upload(file, { bizType: 'INSPECT' });
                    row.attachments.push({ id: uploaded.fileId, name: uploaded.fileName });
                    paint();
                } catch (error) {
                    toastError(error);
                }
            }
        });

        function paint() {
            list.innerHTML = '';
            row.attachments.forEach((file, index) => {
                const img = h('img', { class: 'photo-thumb', alt: file.name });
                api.objectUrl(`/api/files/${file.id}`)
                    .then((url) => { img.src = url; })
                    .catch(() => { img.alt = '照片加载失败'; });
                list.append(h('div', { class: 'photo-cell' }, img,
                    h('button', {
                        class: 'icon-btn danger', type: 'button', title: '移除', 'aria-label': '移除',
                        onclick: () => {
                            row.attachments.splice(index, 1);
                            paint();
                        },
                    }, '×')));
            });
            list.append(h('button', {
                class: 'btn btn-sm', type: 'button', text: '＋ 上传现场照片',
                onclick: () => fileInput.click(),
            }));
        }

        paint();
        return h('div', { class: 'field' }, list, fileInput);
    }

    /* ------------------------------ 提交 ------------------------------ */

    async function submit(button) {
        const payloadItems = [];
        for (let index = 0; index < rows.length; index += 1) {
            const row = rows[index];
            const item = row.item;
            const label = `第 ${index + 1} 项「${item.itemName}」`;

            let invalid = null;
            if (!row.result) {
                invalid = `${label} 还没有判定`;
            } else if (row.result === 'SKIPPED' && item.required === 1) {
                invalid = `${label} 是必检项，不能跳过`;
            } else if (row.result === 'SKIPPED' && !row.skipReason.trim()) {
                invalid = `${label} 跳过时需要填写原因`;
            } else if (row.result === 'ABNORMAL' && !row.remark.trim()) {
                invalid = `${label} 判为异常，必须填写异常说明`;
            } else if (row.result === 'ABNORMAL' && item.photoRequired === 1 && !row.attachments.length) {
                invalid = `${label} 要求上传现场照片作为证据`;
            } else if (row.result === 'NORMAL' && ['BOOLEAN', 'ENUM', 'NUMBER'].includes(item.itemType)
                && (row.itemValue === '' || row.itemValue === null || row.itemValue === undefined)) {
                invalid = `${label} 请先选择或填写实测值再判为正常`;
            } else if (row.result === 'NORMAL' && item.itemType === 'NUMBER' && row.itemValue !== '') {
                const value = Number(row.itemValue);
                const minOk = item.minValue === null || item.minValue === undefined || value >= Number(item.minValue);
                const maxOk = item.maxValue === null || item.maxValue === undefined || value <= Number(item.maxValue);
                if (Number.isFinite(value) && (!minOk || !maxOk)) {
                    invalid = `${label} 实测值超出标准区间，不能判为正常`;
                }
            }

            if (invalid) {
                toast(invalid, 'error');
                focusItem(index);
                return;
            }

            payloadItems.push({
                templateItemId: item.templateItemId,
                result: row.result,
                itemValue: row.result === 'SKIPPED' ? null : (row.itemValue || null),
                remark: row.result === 'ABNORMAL' ? row.remark.trim() : null,
                skipReason: row.result === 'SKIPPED' ? row.skipReason.trim() : null,
                attachmentIds: row.attachments.length ? row.attachments.map((file) => file.id).join(',') : null,
            });
        }

        button.disabled = true;
        try {
            const abnormalCount = await api.post(`/api/inspection/tasks/${id}/submit`, {
                items: payloadItems,
                remark: remarkInput.value.trim() || null,
            });
            // 接口返回的是本次判为异常的项数，异常单已同步生成
            toast(abnormalCount > 0
                ? `提交成功，${abnormalCount} 项异常已生成异常单`
                : '提交成功，本次没有异常', 'success');
            ctx.navigate(`/inspect/task/${id}`);
        } catch (error) {
            toastError(error);
            button.disabled = false;
        }
    }
}

/* ------------------------------ 小工具 ------------------------------ */

function failure(message, onBack) {
    return h('div', { class: 'card' },
        h('div', { class: 'card-body' },
            h('h2', { text: '无法填报', style: 'font-size:16px;margin-bottom:8px' }),
            h('p', { text: message, style: 'color:var(--text-secondary);font-size:13px;margin:0 0 14px' }),
            h('button', { class: 'btn btn-primary', type: 'button', text: '返回', onclick: onBack })));
}

function parseEnum(raw) {
    if (!raw) {
        return [];
    }
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [String(raw)];
    } catch (error) {
        return [String(raw)];
    }
}
