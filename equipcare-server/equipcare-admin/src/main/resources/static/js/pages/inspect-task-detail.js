/**
 * 巡检任务详情。
 *
 * 展示设备摘要（含任务二维码）、逐项检查标准与已填结果、本次产生的异常记录。
 * 照片通过带令牌的 /api/files/{id} 取二进制再转 objectURL，不把令牌拼在图片地址上。
 */

import { api, auth } from '../api.js';
import {
    ANOMALY_LEVEL, ANOMALY_STATUS, EQUIPMENT_STATUS, INSPECT_STATUS, ITEM_RESULT, ITEM_TYPE,
    canExecute, esc, formatDate, formatSize, h, statusTag,
} from '../ui.js';

export async function render(container, ctx) {
    const id = Number(ctx.params.id);
    const mayExecute = auth.can('inspect:task:execute');

    let detail;
    try {
        detail = await api.get(`/api/inspection/tasks/${id}`);
    } catch (error) {
        container.append(h('div', { class: 'card' },
            h('div', { class: 'card-body' },
                h('h2', { text: '任务加载失败', style: 'font-size:16px;margin-bottom:8px' }),
                h('p', { text: error.message, style: 'color:var(--danger);font-size:13px' }),
                h('button', {
                    class: 'btn', type: 'button', text: '返回任务列表',
                    onclick: () => ctx.navigate('/inspect/task'),
                }))));
        return;
    }

    ctx.setTitle(`任务 ${detail.taskCode}`);

    const equipment = detail.equipment || {};
    const qrHost = h('div', { class: 'qr-box' }, h('div', { class: 'hint', text: '二维码加载中…' }));

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: detail.equipmentName || `任务 ${detail.taskCode}` }),
            h('p', { text: `${detail.taskCode} · 计划日 ${formatDate(detail.planDate)} · 执行人 ${detail.assigneeName || '未指派'}` })),
        h('div', { class: 'page-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '← 返回列表',
                onclick: () => ctx.navigate('/inspect/task'),
            }),
            mayExecute && canExecute(detail.status) ? h('button', {
                class: 'btn btn-primary', type: 'button', text: '去执行 →',
                onclick: () => ctx.navigate(`/inspect/task/${id}/execute`),
            }) : null));

    const kv = (label, value) => h('div', { class: 'kv' },
        h('span', { class: 'kv-label', text: label }),
        h('span', { class: 'kv-value', html: value }));

    const overview = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '任务概况' }),
            h('span', { html: statusTag(INSPECT_STATUS, detail.status) })),
        h('div', { class: 'detail-hero' },
            h('div', { class: 'hero-main' },
                h('div', { class: 'kv-grid' },
                    kv('任务号', `<span class="mono">${esc(detail.taskCode)}</span>`),
                    kv('计划日', formatDate(detail.planDate)),
                    kv('执行人', esc(detail.assigneeName || '未指派')),
                    kv('任务时限', detail.slaDueTime ? formatDate(detail.slaDueTime, true) : '—'),
                    kv('来源', detail.source === 'MANUAL' ? '手工发起' : `计划生成${detail.planName ? '（' + esc(detail.planName) + '）' : ''}`),
                    kv('巡检模板', esc(detail.templateName || '—')),
                    kv('检查项', `${detail.finishedItemCount ?? 0} / ${detail.itemCount ?? 0} 已填`),
                    kv('异常项', `${detail.abnormalCount ?? 0} 项`
                        + (detail.skippedCount > 0 ? `，跳过 ${detail.skippedCount} 项` : '')),
                    kv('开始时间', detail.startTime ? formatDate(detail.startTime, true) : '未开始'),
                    kv('提交时间', detail.submitTime ? formatDate(detail.submitTime, true) : '未提交'),
                    detail.remark ? kv('备注', esc(detail.remark)) : null),
            ),
            h('div', { class: 'hero-sub' },
                h('div', { class: 'hero-tags' },
                    h('span', { class: 'tag tag-gray', text: equipment.assetNo || '—' }),
                    equipment.categoryName ? h('span', { class: 'tag tag-gray', text: equipment.categoryName }) : null),
                qrHost,
                h('div', { class: 'hint', text: '现场扫码可核验到岗，执行时也可通过扫码进入' }))));

    /* ---------------------------- 检查项 ---------------------------- */

    function itemRow(item, index) {
        const type = ITEM_TYPE[item.itemType] || { label: item.itemType };
        const range = item.itemType === 'NUMBER'
            ? `${item.minValue ?? '−∞'} ~ ${item.maxValue ?? '+∞'} ${item.unit || ''}`
            : item.itemType === 'ENUM'
                ? parseEnum(item.enumOptions).join(' / ')
                : '—';

        return h('tr', { class: item.result === 'ABNORMAL' ? 'row-abnormal' : '' },
            h('td', { class: 'mono', style: 'text-align:center', text: String(index + 1) }),
            h('td', {},
                h('div', { text: item.itemName }),
                h('div', { class: 'cell-sub' },
                    `${type.label}${item.required === 1 ? ' · 必检' : ''}`
                    + (item.photoRequired === 1 ? ' · 异常需留证' : ''))),
            h('td', {},
                h('div', { class: 'cell-sub', text: item.standardRef || '—' }),
                item.checkMethod ? h('div', { class: 'cell-sub', text: item.checkMethod }) : null,
                h('div', { class: 'cell-sub', text: range })),
            h('td', { html: item.result ? statusTag(ITEM_RESULT, item.result) : '<span style="color:var(--text-muted)">未执行</span>' }),
            h('td', {}, esc(item.itemValue || '—')),
            h('td', { style: 'max-width:220px' },
                esc(item.remark || item.skipReason || '—'),
                (item.attachmentIds ? h('div', { class: 'photo-row', 'data-files': item.attachmentIds }) : null)));
    }

    const itemsCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '检查项与填报结果' }),
            h('span', { class: 'hint', text: '正常 / 异常 / 跳过 三态，必检项不可跳过' })),
        h('div', { class: 'table-wrap' },
            h('table', { class: 'data' },
                h('thead', {}, h('tr', {},
                    h('th', { style: 'width:46px', text: '#' }),
                    h('th', { text: '检查项' }),
                    h('th', { text: '标准依据' }),
                    h('th', { style: 'width:84px', text: '结果' }),
                    h('th', { style: 'width:130px', text: '实测值' }),
                    h('th', { text: '说明 / 现场照片' }))),
                h('tbody', {}, ...(detail.items || []).map(itemRow)))));

    /* ---------------------------- 异常记录 ---------------------------- */

    const anomalyList = detail.anomalies || [];
    const anomalyCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '本次异常' }),
            h('span', { class: 'hint', text: anomalyList.length ? `${anomalyList.length} 条` : '没有异常' })),
        h('div', { class: 'card-body' },
            anomalyList.length
                ? h('div', { class: 'anomaly-list' }, ...anomalyList.map((anomaly) => h('div', { class: 'anomaly-item' },
                    h('div', { class: 'anomaly-head' },
                        h('span', { class: 'mono', text: anomaly.anomalyNo }),
                        h('span', { html: statusTag(ANOMALY_LEVEL, anomaly.level) }),
                        h('span', { html: statusTag(ANOMALY_STATUS, anomaly.status) }),
                        anomaly.workOrderNo
                            ? h('span', { class: 'hint', text: `工单 ${anomaly.workOrderNo}` })
                            : null),
                    h('div', { class: 'anomaly-body' },
                        h('div', { text: `${anomaly.itemName}：${anomaly.itemValue || '—'}` }),
                        h('div', { class: 'cell-sub', text: anomaly.description || '' }),
                        anomaly.attachments && anomaly.attachments.length
                            ? h('div', { class: 'photo-row' },
                                ...anomaly.attachments.map((file) => photoThumb(file)))
                            : h('div', { class: 'cell-sub', text: '无现场照片' })),
                    h('div', { class: 'anomaly-foot' },
                        h('button', {
                            class: 'btn btn-sm btn-ghost', type: 'button', text: '查看异常记录',
                            onclick: () => ctx.navigate('/inspect/anomaly'),
                        })))))
                : h('div', { class: 'hint', text: '本次巡检没有判定为异常的检查项。' })));

    container.append(head, overview, itemsCard, anomalyCard);

    /* ---------------------------- 二维码 ---------------------------- */

    try {
        const url = await api.objectUrl(`/api/inspection/tasks/${id}/equipment-qrcode.png`, { size: 260, margin: 1 });
        qrHost.innerHTML = '';
        qrHost.append(h('img', { src: url, alt: '设备二维码' }));
    } catch (error) {
        qrHost.textContent = '二维码加载失败';
    }

    // 逐项补渲染现场照片缩略图
    container.querySelectorAll('.photo-row[data-files]').forEach(async (row) => {
        const ids = String(row.dataset.files).split(',').filter(Boolean);
        row.removeAttribute('data-files');
        for (const fileId of ids) {
            try {
                const url = await api.objectUrl(`/api/files/${fileId}`);
                row.append(h('img', { class: 'photo-thumb', src: url, alt: '现场照片' }));
            } catch (error) {
                // 单个图片失败不影响其它证据展示
            }
        }
    });
}

/* ------------------------------ 小工具 ------------------------------ */

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

function photoThumb(file) {
    const img = h('img', { class: 'photo-thumb', alt: file.fileName || '现场照片', title: file.fileName || '' });
    api.objectUrl(`/api/files/${file.id}`)
        .then((url) => { img.src = url; })
        .catch(() => { img.replaceWith(h('span', { class: 'hint', text: `照片 ${file.fileName || ''} 加载失败` })); });
    return h('div', { class: 'photo-cell' }, img,
        h('span', { class: 'cell-sub', text: formatSize(file.fileSize) }));
}
