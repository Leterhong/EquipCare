/**
 * 维修过程记录页（维修人员主战场）。
 *
 * 顺序刻意做成「开始维修 → 记录过程 → 申请备件 → 提交验收」：
 *  - 没开始维修就不让填记录（否则工单没有维修时长，验收无从判断工期）；
 *  - 提交验收前置条件与后端一致（完工说明 + 维修后照片 + 无待审批备件 + 维修总结）。
 * 前端先挡一次给即时反馈，最终以服务端校验为准。
 */

import { api, auth } from '../api.js';
import {
    PART_ISSUE_STATUS, REPAIR_RECORD_TYPE, dueText, esc, formatDate, formatMoney, h, statusTag, toast, toastError,
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

    ctx.setTitle(`维修记录 · ${detail.orderNo}`);

    const me = auth.user || {};
    const isAssignee = !!detail.assigneeId && (detail.assigneeId === me.userId || detail.assigneeId === me.id);
    const isAdmin = auth.hasRole('ADMIN') || auth.hasRole('EQUIP_ADMIN');
    if (!isAssignee && !isAdmin) {
        container.append(failure('只有本单维修人（或管理员）可以填写维修记录。', () => ctx.navigate(`/workorder/${id}`)));
        return;
    }
    if (detail.status !== 'IN_PROGRESS') {
        container.append(failure(
            `工单当前是「${detail.statusLabel || detail.status}」，只有维修中的工单可以记录过程。`,
            () => ctx.navigate(`/workorder/${id}`),
        ));
        return;
    }

    const reload = () => ctx.navigate(`/workorder/${id}/repair`);

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '维修过程记录' }),
            h('p', {}, h('span', { class: 'mono', text: detail.orderNo }), ' · ',
                h('span', { text: detail.title }), ' ',
                h('span', { html: woStatus(detail.status) }))),
        h('div', { class: 'page-actions' },
            h('button', { class: 'btn', type: 'button', text: '工单详情', onclick: () => ctx.navigate(`/workorder/${id}`) }),
            h('button', { class: 'btn', type: 'button', text: '完整时间线', onclick: () => ctx.navigate(`/workorder/${id}/timeline`) })));

    const overview = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '当前工单' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'kv-grid' },
                kv('设备', esc(detail.equipmentName || '—')),
                kv('故障等级', esc(detail.level)),
                kv('优先级', esc(detail.priorityLabel || '—')),
                kv('报修人', esc(detail.reporterName || '—')),
                kv('修复时限', dueText(detail.resolveDueTime)),
                kv('是否已开始维修', detail.repairStarted ? '已开始' : '未开始'),
                kv('接单时间', detail.acceptTime ? formatDate(detail.acceptTime, true) : '—'),
                kv('开始维修时间', detail.startRepairTime ? formatDate(detail.startRepairTime, true) : '—'))));

    container.append(head, overview);

    /* --------------------------- 一、开始维修 --------------------------- */

    if (!detail.repairStarted) {
        const reasonInput = h('textarea', { class: 'input', rows: '2', placeholder: '维修原因：为什么会坏，如「机头轴承磨损导致转子偏心」' });
        const diagInput = h('textarea', { class: 'input', rows: '3', placeholder: '故障诊断：怎么判断的，如「听针定位轴承位，振动值 8.6mm/s」' });
        const beforePhotos = photoPicker({ label: '维修前照片', max: 6 });
        const startButton = h('button', { class: 'btn btn-primary', type: 'button', text: '开始维修' });

        startButton.addEventListener('click', async () => {
            if (reasonInput.value.trim().length < 5) {
                toast('维修原因不少于 5 个字', 'error');
                return;
            }
            if (diagInput.value.trim().length < 5) {
                toast('故障诊断不少于 5 个字', 'error');
                return;
            }
            startButton.disabled = true;
            try {
                await api.post(`/api/workorder/${id}/start-repair`, {
                    reason: reasonInput.value.trim(),
                    diagnosis: diagInput.value.trim(),
                    beforeImageIds: beforePhotos.ids() || null,
                });
                toast('已开始维修，计时开始', 'success');
                reload();
            } catch (error) {
                toastError(error);
                startButton.disabled = false;
            }
        });

        container.append(h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: '① 开始维修' }),
                h('span', { class: 'hint', text: '填写维修原因与故障诊断后开始计时；同一张工单只能开始一次' })),
            h('div', { class: 'card-body' },
                h('div', { class: 'form-grid' },
                    h('div', { class: 'field grow' }, h('label', { text: '维修原因' }), reasonInput),
                    h('div', { class: 'field grow' }, h('label', { text: '故障诊断' }), diagInput),
                    beforePhotos.node),
                h('div', { style: 'margin-top:14px' }, startButton))));
        return;
    }

    /* --------------------------- 二、过程记录 --------------------------- */

    const recordTypeSelect = h('select', { class: 'input' },
        h('option', { value: 'PROCESS', text: '维修过程' }),
        h('option', { value: 'DIAGNOSIS', text: '故障诊断' }),
        h('option', { value: 'FINISH', text: '完工说明' }),
        h('option', { value: 'OTHER', text: '其他记录' }));
    const contentInput = h('textarea', { class: 'input', rows: '3', placeholder: '做了什么、换了什么、测了什么数据' });
    const costInput = h('input', { class: 'input', type: 'number', min: '0', placeholder: '本次耗时（分钟）' });
    const beforePhotos = photoPicker({ label: '本次维修前照片', max: 4 });
    const afterPhotos = photoPicker({ label: '本次维修后照片（完工说明必传）', max: 4 });
    const addButton = h('button', { class: 'btn btn-primary', type: 'button', text: '保存记录' });

    addButton.addEventListener('click', async () => {
        if (contentInput.value.trim().length < 2) {
            toast('请填写记录内容', 'error');
            return;
        }
        if (recordTypeSelect.value === 'FINISH' && afterPhotos.count() === 0) {
            toast('完工说明必须上传至少 1 张维修后照片', 'error');
            return;
        }
        addButton.disabled = true;
        try {
            await api.post(`/api/workorder/${id}/records`, {
                recordType: recordTypeSelect.value,
                content: contentInput.value.trim(),
                costMinutes: costInput.value ? Number(costInput.value) : null,
                beforeImageIds: beforePhotos.ids() || null,
                afterImageIds: afterPhotos.ids() || null,
            });
            toast('记录已保存', 'success');
            reload();
        } catch (error) {
            toastError(error);
            addButton.disabled = false;
        }
    });

    const recordList = h('div', { class: 'stack' });
    if ((detail.repairRecords || []).length === 0) {
        recordList.append(h('p', { class: 'hint', text: '还没有过程记录' }));
    } else {
        detail.repairRecords.forEach((record) => {
            recordList.append(h('div', { class: 'stack-row' },
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
        });
    }

    container.append(h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '② 过程记录' })),
        h('div', { class: 'card-body' }, recordList,
            h('div', { class: 'form-grid', style: 'margin-top:16px' },
                h('div', { class: 'field' }, h('label', { text: '记录类型' }), recordTypeSelect),
                h('div', { class: 'field' }, h('label', { text: '耗时（分钟）' }), costInput),
                h('div', { class: 'field grow' }, h('label', { text: '内容' }), contentInput),
                beforePhotos.node,
                afterPhotos.node),
            h('div', { style: 'margin-top:12px' }, addButton))));

    /* --------------------------- 三、备件申请 --------------------------- */

    const partSelect = h('select', { class: 'input' }, h('option', { value: '', text: '加载中…' }));
    const qtyInput = h('input', { class: 'input', type: 'number', min: '1', value: '1' });
    const partRemark = h('input', { class: 'input', placeholder: '用途说明（可选）' });
    const applyButton = h('button', { class: 'btn', type: 'button', text: '提交备件申请' });

    try {
        const parts = await api.get('/api/inventory/parts', { pageNum: 1, pageSize: 100 });
        partSelect.innerHTML = '';
        partSelect.append(h('option', { value: '', text: '请选择备件' }));
        (parts.records || []).forEach((part) => {
            partSelect.append(h('option', {
                value: String(part.id),
                text: `${part.partName}（${part.partNo}）库存 ${part.currentStock ?? 0}${part.unit || ''}`,
            }));
        });
    } catch (error) {
        partSelect.innerHTML = '';
        partSelect.append(h('option', { value: '', text: '备件加载失败' }));
    }

    applyButton.addEventListener('click', async () => {
        if (!partSelect.value) {
            toast('请选择备件', 'error');
            return;
        }
        applyButton.disabled = true;
        try {
            await api.post('/api/inventory/applies', {
                workOrderId: id,
                partId: Number(partSelect.value),
                quantity: Number(qtyInput.value || 1),
                remark: partRemark.value.trim() || null,
            });
            toast('备件申请已提交，待库管出库', 'success');
            reload();
        } catch (error) {
            toastError(error);
            applyButton.disabled = false;
        }
    });

    const partList = h('div', { class: 'stack' });
    if ((detail.parts || []).length === 0) {
        partList.append(h('p', { class: 'hint', text: '本单尚未领用备件' }));
    } else {
        detail.parts.forEach((part) => {
            partList.append(h('div', { class: 'stack-row' },
                h('div', { class: 'stack-head' },
                    h('span', { text: `${part.partName} × ${part.quantity}${part.unit || ''}` }),
                    h('span', { html: statusTag(PART_ISSUE_STATUS, part.issueStatus) }),
                    h('span', { class: 'cell-sub', text: formatMoney(part.amount) }),
                    h('span', { class: 'cell-sub', text: `申请人 ${part.applyUserName || '—'}` }))));
        });
    }

    container.append(h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '③ 备件申请' }),
            h('span', { class: 'hint', text: '存在待审批申请时不能提交验收' })),
        h('div', { class: 'card-body' }, partList,
            h('div', { class: 'form-grid', style: 'margin-top:16px' },
                h('div', { class: 'field' }, h('label', { text: '备件' }), partSelect),
                h('div', { class: 'field' }, h('label', { text: '数量' }), qtyInput),
                h('div', { class: 'field grow' }, h('label', { text: '备注' }), partRemark)),
            h('div', { style: 'margin-top:12px' }, applyButton))));

    /* --------------------------- 四、提交验收 --------------------------- */

    const summaryInput = h('textarea', { class: 'input', rows: '3', placeholder: '维修总结：故障原因、处理措施、复测结果' });
    const laborInput = h('input', { class: 'input', type: 'number', step: '0.5', min: '0', placeholder: '工时（小时）' });
    const submitButton = h('button', { class: 'btn btn-primary', type: 'button', text: '提交验收' });

    const applied = (detail.parts || []).filter((part) => part.issueStatus === 'APPLIED').length;
    const hasFinish = (detail.repairRecords || []).some((record) => record.recordType === 'FINISH');

    submitButton.addEventListener('click', async () => {
        if (summaryInput.value.trim().length < 5) {
            toast('请填写维修总结', 'error');
            return;
        }
        submitButton.disabled = true;
        try {
            await api.post(`/api/workorder/${id}/submit-verify`, {
                repairSummary: summaryInput.value.trim(),
                laborHours: laborInput.value ? Number(laborInput.value) : null,
            });
            toast('已提交验收，等待验收人处理', 'success');
            ctx.navigate(`/workorder/${id}`);
        } catch (error) {
            toastError(error);
            submitButton.disabled = false;
        }
    });

    const blockers = [];
    if (!hasFinish) {
        blockers.push('缺少「完工说明」记录');
    }
    if (applied > 0) {
        blockers.push(`有 ${applied} 条备件申请待审批`);
    }

    container.append(h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '④ 提交验收' })),
        h('div', { class: 'card-body' },
            blockers.length
                ? h('div', { class: 'hint', text: `提交前还需处理：${blockers.join('；')}` })
                : h('div', { class: 'hint', text: '前置条件已满足，可提交验收。' }),
            h('div', { class: 'form-grid', style: 'margin-top:12px' },
                h('div', { class: 'field grow' }, h('label', { text: '维修总结' }), summaryInput),
                h('div', { class: 'field' }, h('label', { text: '工时（小时）' }), laborInput)),
            h('div', { style: 'margin-top:12px' }, submitButton))));
}

function failure(message, onBack) {
    return h('div', { class: 'card' },
        h('div', { class: 'card-body' },
            h('p', { text: message }),
            h('button', { class: 'btn', type: 'button', text: '返回', onclick: onBack })));
}
