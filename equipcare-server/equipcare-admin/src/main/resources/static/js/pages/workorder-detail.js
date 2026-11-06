/**
 * 工单详情页。
 *
 * 这一页是「看清一张工单」的地方：状态、人、时限、故障现场、维修过程、验收、备件、评价。
 * 所有改状态的动作按钮都由后端返回的 allowedActions 决定，前端不自己推断谁能点什么，
 * 避免出现「按钮能点但接口 403」的落差。
 */

import { api, auth } from '../api.js';
import {
    REPAIR_RECORD_TYPE, WO_SOURCE, dueText, esc, formatDate, formatMoney, h, statusTag, toast, toastError,
} from '../ui.js';
import {
    attachmentThumbs, dispatchModal, kv, loadAssignees, loadOrder, priorityTag, reasonModal, woStatus,
} from './workorder-shared.js';
import { aiCard, aiEnabled, openDiagnosisModal } from './ai-copilot.js';

export async function render(container, ctx) {
    const id = Number(ctx.params.id);
    let detail;
    try {
        detail = await loadOrder(id);
    } catch (error) {
        container.append(h('div', { class: 'card' }, h('div', { class: 'card-body' }, h('p', { text: error.message }))));
        return;
    }

    ctx.setTitle(`工单 ${detail.orderNo}`);

    const canRepair = auth.can('wo:workorder:repair');
    const canVerify = auth.can('wo:workorder:verify');
    const canEvaluate = auth.can('wo:workorder:evaluate');
    const actions = detail.allowedActions || [];

    async function run(promise, message) {
        try {
            await promise;
            toast(message, 'success');
            ctx.navigate(`/workorder/${id}`);
        } catch (error) {
            toastError(error);
        }
    }

    /* ----------------------------- 操作按钮 ----------------------------- */

    const actionBar = h('div', { class: 'page-actions' });
    if (actions.includes('DISPATCH')) {
        actionBar.append(h('button', {
            class: 'btn btn-primary', type: 'button',
            text: detail.status === 'REJECTED' ? '重新派单' : '派单',
            onclick: async () => {
                const assignees = await loadAssignees();
                dispatchModal({
                    order: detail,
                    assignees,
                    aiSuggest: await aiEnabled(),
                    title: detail.status === 'REJECTED' ? '重新派单' : '派单',
                    onConfirm: (payload) => run(api.post(`/api/workorder/${id}/dispatch`, payload), '已派单'),
                });
            },
        }));
    }
    if (actions.includes('ACCEPT')) {
        actionBar.append(h('button', {
            class: 'btn btn-primary', type: 'button', text: '接单',
            onclick: () => run(api.post(`/api/workorder/${id}/accept`, {}), '已接单，请到维修过程记录填写诊断'),
        }));
    }
    if (actions.includes('REJECT_DISPATCH')) {
        actionBar.append(h('button', {
            class: 'btn', type: 'button', text: '拒接',
            onclick: () => reasonModal({
                title: '拒接工单',
                label: '拒接原因',
                minLength: 5,
                onConfirm: (reason) => run(api.post(`/api/workorder/${id}/reject`, { reason }), '已拒接'),
            }),
        }));
    }
    if (actions.includes('TRANSFER') || actions.includes('FORCE_TRANSFER')) {
        actionBar.append(h('button', {
            class: 'btn', type: 'button', text: actions.includes('TRANSFER') ? '转派' : '强制转派',
            onclick: async () => {
                const assignees = await loadAssignees();
                dispatchModal({
                    order: detail,
                    assignees,
                    aiSuggest: await aiEnabled(),
                    title: '转派',
                    onConfirm: (payload) => run(
                        api.post(`/api/workorder/${id}/${actions.includes('TRANSFER') ? 'transfer' : 'force-transfer'}`, payload),
                        '已转派',
                    ),
                });
            },
        }));
    }
    if (actions.includes('REVOKE')) {
        actionBar.append(h('button', {
            class: 'btn', type: 'button', text: '撤回派单',
            onclick: () => reasonModal({
                title: '撤回派单',
                label: '撤回原因',
                minLength: 5,
                onConfirm: (reason) => run(api.post(`/api/workorder/${id}/revoke`, { reason }), '已撤回'),
            }),
        }));
    }
    if (actions.includes('CANCEL')) {
        actionBar.append(h('button', {
            class: 'btn btn-danger', type: 'button', text: '取消工单',
            onclick: () => reasonModal({
                title: '取消工单',
                label: '取消原因',
                minLength: 5,
                onConfirm: (reason) => run(api.post(`/api/workorder/${id}/cancel`, { reason }), '已取消'),
            }),
        }));
    }
    if (detail.status === 'IN_PROGRESS' && detail.assigneeId && canRepair) {
        actionBar.append(h('button', {
            class: 'btn', type: 'button', text: detail.repairStarted ? '维修过程记录' : '开始维修',
            onclick: () => ctx.navigate(`/workorder/${id}/repair`),
        }));
    }
    if (detail.status === 'PENDING_REVIEW' && canVerify) {
        actionBar.append(h('button', {
            class: 'btn btn-primary', type: 'button', text: '去验收',
            onclick: () => ctx.navigate(`/workorder/${id}/verify`),
        }));
    }
    if (detail.status === 'COMPLETED' && canEvaluate && !detail.evaluated) {
        actionBar.append(h('button', {
            class: 'btn', type: 'button', text: '评价本次服务',
            onclick: () => ctx.navigate(`/workorder/${id}/evaluate`),
        }));
    }

    /* ------------------------------- 概览 ------------------------------- */

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', {}, h('span', { text: detail.title }), ' ',
                h('span', { html: woStatus(detail.status) })),
            h('p', {}, h('span', { class: 'mono', text: detail.orderNo }), ' · ',
                h('span', { html: statusTag(WO_SOURCE, detail.source) }),
                ` · 报修于 ${formatDate(detail.reportTime, true)}`)),
        h('div', { class: 'page-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '← 返回列表',
                onclick: () => ctx.navigate('/workorder'),
            }),
            actionBar));

    const overview = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '工单概况' }),
            h('div', { class: 'chip-row' },
                h('span', { html: priorityTag(detail.priority) }),
                h('span', { class: 'hint', text: `修复时限：${dueText(detail.resolveDueTime)}` }))),
        h('div', { class: 'card-body' },
            h('div', { class: 'kv-grid' },
                kv('设备', esc(detail.equipmentName || '—')),
                kv('资产编号', `<span class="mono">${esc(detail.assetNo || '—')}</span>`),
                kv('故障等级', statusTag({
                    P1: { label: 'P1 紧急', tag: 'tag-red' },
                    P2: { label: 'P2 高', tag: 'tag-amber' },
                    P3: { label: 'P3 中', tag: 'tag-blue' },
                    P4: { label: 'P4 低', tag: 'tag-gray' },
                }, detail.level)),
                kv('报修人', esc(detail.reporterName || '—')),
                kv('派单人', esc(detail.dispatcherName || '—')),
                kv('派单时间', detail.dispatchTime ? formatDate(detail.dispatchTime, true) : '—'),
                kv('维修人', esc(detail.assigneeName || '未派单')),
                kv('接单时间', detail.acceptTime ? formatDate(detail.acceptTime, true) : '未接单'),
                kv('开始维修', detail.startRepairTime ? formatDate(detail.startRepairTime, true) : '未开始'),
                kv('提交验收', detail.submitVerifyTime ? formatDate(detail.submitVerifyTime, true) : '—'),
                kv('验收人', esc(detail.verifyUserName || '—')),
                kv('验收结果', detail.verifyResult === 'PASS' ? '通过'
                    : detail.verifyResult === 'REJECT' ? '不通过' : '—'),
                kv('维修工时', detail.laborHours != null ? `${detail.laborHours} 小时` : '—'),
                kv('备件成本', formatMoney(detail.spareCost)),
                kv('驳回次数', String(detail.rejectCount || 0)),
                kv('拒接次数', String(detail.rejectDispatchCount || 0)),
                kv('完成时间', detail.actualFinishTime ? formatDate(detail.actualFinishTime, true) : '—'),
                kv('关闭时间', detail.closeTime ? formatDate(detail.closeTime, true) : '—'))));

    /* ----------------------------- 故障现场 ----------------------------- */

    const faultCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '故障描述' })),
        h('div', { class: 'card-body' },
            h('p', { class: 'fault-text', text: detail.faultDesc || '—' }),
            h('div', { class: 'hint', text: '现场照片' }),
            attachmentThumbs(detail.faultImages, { empty: '报修时未上传照片' })));

    /* ----------------------------- 派单轨迹 ----------------------------- */

    const dispatchCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '派单历史' })),
        h('div', { class: 'card-body' },
            (detail.dispatchRecords || []).length === 0
                ? h('p', { class: 'hint', text: '尚未派单' })
                : h('div', { class: 'stack' }, ...(detail.dispatchRecords || []).map((record) => h('div', { class: 'stack-row' },
                    h('div', {},
                        h('div', { text: `${dispatchTypeLabel(record.dispatchType)} · ${record.toUserName || '—'}` }),
                        h('div', { class: 'cell-sub', text: `${formatDate(record.dispatchTime, true)} · 派单人 ${record.dispatcherName || '—'}` }),
                        record.remark ? h('div', { class: 'cell-sub', text: `说明：${record.remark}` }) : null,
                        record.rejectFlag === 1
                            ? h('div', { class: 'cell-sub', text: `拒接原因：${record.rejectReason || '—'}` })
                            : null))))));

    /* ----------------------------- 维修记录 ----------------------------- */

    const repairCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '维修过程记录' }),
            h('button', {
                class: 'btn btn-sm', type: 'button', text: '查看完整时间线',
                onclick: () => ctx.navigate(`/workorder/${id}/timeline`),
            })),
        h('div', { class: 'card-body' },
            (detail.repairRecords || []).length === 0
                ? h('p', { class: 'hint', text: '还没有维修记录' })
                : h('div', { class: 'stack' }, ...(detail.repairRecords || []).map((record) => h('div', { class: 'stack-row' },
                    h('div', { class: 'stack-head' },
                        h('span', { html: statusTag(REPAIR_RECORD_TYPE, record.recordType) }),
                        h('span', { class: 'cell-sub', text: `${formatDate(record.createTime, true)} · ${record.operatorName || '—'}` }),
                        record.costMinutes ? h('span', { class: 'cell-sub', text: `耗时 ${record.costMinutes} 分钟` }) : null),
                    h('div', { class: 'fault-text', text: record.content }),
                    (record.beforeImages || []).length ? h('div', {},
                        h('div', { class: 'hint', text: '维修前' }),
                        attachmentThumbs(record.beforeImages)) : null,
                    (record.afterImages || []).length ? h('div', {},
                        h('div', { class: 'hint', text: '维修后' }),
                        attachmentThumbs(record.afterImages)) : null)))));

    /* ----------------------------- 验收记录 ----------------------------- */

    const verifyCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '验收记录' })),
        h('div', { class: 'card-body' },
            (detail.verifyRecords || []).length === 0
                ? h('p', { class: 'hint', text: '尚未验收' })
                : h('div', { class: 'stack' }, ...(detail.verifyRecords || []).map((record) => h('div', { class: 'stack-row' },
                    h('div', { class: 'stack-head' },
                        h('span', {
                            class: `tag ${record.verifyResult === 'PASS' ? 'tag-green' : 'tag-red'}`,
                            text: record.verifyResult === 'PASS' ? '验收通过' : '验收不通过',
                        }),
                        h('span', { class: 'cell-sub', text: `${formatDate(record.verifyTime, true)} · ${record.verifyUserName || '—'}` }),
                        record.qualityScore ? h('span', { class: 'cell-sub', text: `质量评分 ${record.qualityScore}` }) : null),
                    h('div', { class: 'fault-text', text: record.remark || '—' }))))));

    /* ----------------------------- 备件用料 ----------------------------- */

    const partCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '备件使用' })),
        h('div', { class: 'card-body' },
            (detail.parts || []).length === 0
                ? h('p', { class: 'hint', text: '本单未领用备件' })
                : h('div', { class: 'stack' }, ...(detail.parts || []).map((part) => h('div', { class: 'stack-row' },
                    h('div', { class: 'stack-head' },
                        h('span', { text: `${part.partName} × ${part.quantity}${part.unit || ''}` }),
                        h('span', { class: 'cell-sub', text: part.issueStatusLabel || part.issueStatus }),
                        h('span', { class: 'cell-sub', text: `金额 ${formatMoney(part.amount)}` }),
                        h('span', { class: 'cell-sub', text: `申请人 ${part.applyUserName || '—'}` })))))));

    /* ----------------------------- 服务评价 ----------------------------- */

    const evaluationCard = detail.evaluation ? h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '服务评价' }),
            h('span', { class: 'hint', text: `平均 ${detail.evaluation.averageScore} 分` })),
        h('div', { class: 'card-body' },
            h('div', { class: 'kv-grid' },
                kv('服务态度', `${detail.evaluation.attitudeScore} 分`),
                kv('维修质量', `${detail.evaluation.qualityScore} 分`),
                kv('响应速度', `${detail.evaluation.speedScore} 分`),
                kv('评价人', esc(detail.evaluation.evaluatorName || '—')),
                kv('评价时间', formatDate(detail.evaluation.createTime, true))),
            h('p', { class: 'fault-text', text: detail.evaluation.content || '（未填写文字评价）' }))) : null;

    /* ----------------------------- 巡检来源 ----------------------------- */

    const sourceCard = detail.sourceInspection ? h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '巡检来源' }),
            h('span', { class: 'hint', text: '以下为巡检异常原单，实时联查，不在工单里存副本' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'kv-grid' },
                kv('异常单号', esc(detail.sourceInspection.anomalyNo || '—')),
                kv('检查项', esc(detail.sourceInspection.itemName || '—')),
                kv('检查值', esc(detail.sourceInspection.itemValue || '—')),
                kv('巡检任务', esc(detail.sourceInspection.taskCode || '—')),
                kv('异常描述', esc(detail.sourceInspection.anomalyDescription || '—'))),
            attachmentThumbs(detail.sourceInspection.attachments, { empty: '巡检未留证' }),
            (detail.mergedInspections || []).length ? h('div', {},
                h('div', { class: 'hint', text: `同设备同故障合并进本单的其它异常（${detail.mergedInspections.length} 条）` }),
                ...(detail.mergedInspections || []).map((item) => h('div', { class: 'stack-row' },
                    h('div', { text: `${item.anomalyNo || ''} · ${item.itemName || ''}` }),
                    h('div', { class: 'cell-sub', text: item.anomalyDescription || '' })))) : null)) : null;

    /* 概况 + 故障现场双栏，窄屏自动叠放 */
    const topGrid = h('div', { class: 'detail-two-col' }, overview, faultCard);

    // AI 副驾：启用时在概览下方插入诊断/报告卡片；未启用返回 null，页面照常
    const copilotCard = await aiEnabled() ? aiCard(detail) : null;
    if (copilotCard) {
        actionBar.append(h('button', {
            class: 'btn', type: 'button', text: '⚡ AI 诊断',
            onclick: () => openDiagnosisModal(detail),
        }));
    }

    container.append(head, topGrid);
    if (copilotCard) {
        container.append(copilotCard);
    }
    container.append(dispatchCard, repairCard, verifyCard, partCard);
    if (evaluationCard) {
        container.append(evaluationCard);
    }
    if (sourceCard) {
        container.append(sourceCard);
    }
}

function dispatchTypeLabel(code) {
    return {
        ASSIGN: '首次派单',
        REDISPATCH: '重新派单',
        TRANSFER: '转派',
        REASSIGN: '强制转派',
        REVOKE: '撤回',
    }[code] || code;
}
