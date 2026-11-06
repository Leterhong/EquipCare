/**
 * 维修验收页。
 *
 * 验收人在这里一次看全：维修记录、前后照片、备件用料、工时与维修总结。
 * 「不通过」必须写清原因（≥10 字），退回后工单回到维修中，且不重置 SLA —— 这些规则后端
 * 也会校验，前端只负责把话说在前面。
 */

import { api } from '../api.js';
import {
    PART_ISSUE_STATUS, REPAIR_RECORD_TYPE, esc, formatDate, formatMoney, h, statusTag, toast, toastError,
} from '../ui.js';
import { attachmentThumbs, kv, photoPicker, woStatus } from './workorder-shared.js';

export async function render(container, ctx) {
    const id = Number(ctx.params.id);
    let detail;
    try {
        detail = await api.get(`/api/workorder/${id}`);
    } catch (error) {
        container.append(failure(error.message, () => ctx.navigate('/workorder')));
        return;
    }

    ctx.setTitle(`验收 · ${detail.orderNo}`);

    if (detail.status !== 'PENDING_REVIEW') {
        container.append(failure(
            `工单当前是「${detail.statusLabel || detail.status}」，只有待验收的工单可以验收。`,
            () => ctx.navigate(`/workorder/${id}`),
        ));
        return;
    }

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '维修验收' }),
            h('p', {}, h('span', { class: 'mono', text: detail.orderNo }), ' · ',
                h('span', { text: detail.title }), ' ',
                h('span', { html: woStatus(detail.status) }))),
        h('div', { class: 'page-actions' },
            h('button', { class: 'btn', type: 'button', text: '工单详情', onclick: () => ctx.navigate(`/workorder/${id}`) })));

    /* ----------------------------- 维修结果 ----------------------------- */

    const records = detail.repairRecords || [];
    const recordNodes = records.length === 0
        ? [h('p', { class: 'hint', text: '没有维修记录' })]
        : records.map((record) => h('div', { class: 'stack-row' },
            h('div', { class: 'stack-head' },
                h('span', { html: statusTag(REPAIR_RECORD_TYPE, record.recordType) }),
                h('span', { class: 'cell-sub', text: `${formatDate(record.createTime, true)} · ${record.operatorName || '—'}` }),
                record.costMinutes ? h('span', { class: 'cell-sub', text: `耗时 ${record.costMinutes} 分钟` }) : null),
            h('div', { class: 'fault-text', text: record.content }),
            (record.beforeImages || []).length
                ? h('div', {}, h('div', { class: 'hint', text: '维修前' }), attachmentThumbs(record.beforeImages))
                : null,
            (record.afterImages || []).length
                ? h('div', {}, h('div', { class: 'hint', text: '维修后' }), attachmentThumbs(record.afterImages))
                : null));

    const summaryCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '维修结果' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'kv-grid' },
                kv('设备', esc(detail.equipmentName || '—')),
                kv('维修人', esc(detail.assigneeName || '—')),
                kv('开始维修', detail.startRepairTime ? formatDate(detail.startRepairTime, true) : '—'),
                kv('提交验收', detail.submitVerifyTime ? formatDate(detail.submitVerifyTime, true) : '—'),
                kv('维修工时', detail.laborHours != null ? `${detail.laborHours} 小时` : '—'),
                kv('驳回次数', String(detail.rejectCount || 0))),
            h('div', { class: 'hint', text: '维修总结' }),
            h('p', { class: 'fault-text', text: detail.repairSummary || '—' }),
            h('div', { class: 'stack', style: 'margin-top:12px' }, ...recordNodes)));

    /* ----------------------------- 备件用料 ----------------------------- */

    const parts = detail.parts || [];
    const partNodes = parts.length === 0
        ? [h('p', { class: 'hint', text: '本单未领用备件' })]
        : parts.map((part) => h('div', { class: 'stack-row' },
            h('div', { class: 'stack-head' },
                h('span', { text: `${part.partName} × ${part.quantity}${part.unit || ''}` }),
                h('span', { html: statusTag(PART_ISSUE_STATUS, part.issueStatus) }),
                h('span', { class: 'cell-sub', text: `金额 ${formatMoney(part.amount)}` }),
                h('span', { class: 'cell-sub', text: `申请 ${part.applyUserName || '—'}` }),
                part.issueTime ? h('span', { class: 'cell-sub', text: `出库 ${formatDate(part.issueTime, true)}` }) : null)));

    const partCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '备件用料' })),
        h('div', { class: 'card-body' }, h('div', { class: 'stack' }, ...partNodes)));

    /* ------------------------------- 验收 ------------------------------- */

    const scoreSelect = h('select', { class: 'input' },
        ...[5, 4, 3, 2, 1].map((score) => h('option', { value: String(score), text: `${score} 分` })));
    const remarkInput = h('textarea', { class: 'input', rows: '3', placeholder: '验收意见：现场复测数据、是否整改到位' });
    const photos = photoPicker({ label: '验收照片', max: 4 });

    const passButton = h('button', { class: 'btn btn-primary', type: 'button', text: '验收通过' });
    const rejectButton = h('button', { class: 'btn btn-danger', type: 'button', text: '验收不通过' });

    passButton.addEventListener('click', async () => {
        passButton.disabled = true;
        try {
            await api.post(`/api/workorder/${id}/verify-pass`, {
                qualityScore: Number(scoreSelect.value),
                remark: remarkInput.value.trim() || null,
                attachmentIds: photos.ids() || null,
            });
            toast('验收通过，工单已完成', 'success');
            ctx.navigate(`/workorder/${id}`);
        } catch (error) {
            toastError(error);
            passButton.disabled = false;
        }
    });

    rejectButton.addEventListener('click', async () => {
        const remark = remarkInput.value.trim();
        if (remark.length < 10) {
            toast('验收不通过需填写不少于 10 个字的原因', 'error');
            return;
        }
        rejectButton.disabled = true;
        try {
            await api.post(`/api/workorder/${id}/verify-reject`, {
                remark,
                attachmentIds: photos.ids() || null,
            });
            toast('已退回维修中，请维修人整改后重新提交', 'success');
            ctx.navigate(`/workorder/${id}`);
        } catch (error) {
            toastError(error);
            rejectButton.disabled = false;
        }
    });

    const verifyCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '验收意见' }),
            h('span', { class: 'hint', text: '通过需给出质量评分；不通过需写清原因（≥10 字）' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'form-grid' },
                h('div', { class: 'field' }, h('label', { text: '质量评分' }), scoreSelect),
                h('div', { class: 'field grow' }, h('label', { text: '验收意见' }), remarkInput),
                photos.node),
            h('div', { class: 'chip-row', style: 'margin-top:14px' }, passButton, rejectButton),
            h('p', { class: 'hint', text: '注意：验收不通过后工单回到「维修中」，SLA 修复时限不会重置。' })));

    container.append(head, summaryCard, partCard, verifyCard);
}

function failure(message, onBack) {
    return h('div', { class: 'card' },
        h('div', { class: 'card-body' },
            h('p', { text: message }),
            h('button', { class: 'btn', type: 'button', text: '返回', onclick: onBack })));
}
