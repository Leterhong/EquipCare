/**
 * 巡检异常记录。
 *
 * 异常单是巡检与维修之间的桥梁：转工单时只建立关联（工单记 anomaly_id + 任务 id），
 * 设备、检查项、实测值、现场照片都由异常单联查，不做数据复制，
 * 这样巡检记录后续被补正时，工单侧看到的始终是最新值。
 */

import { api, auth } from '../api.js';
import {
    ANOMALY_LEVEL, ANOMALY_STATUS, confirmDialog, debounce, esc, formatDate, formatSize, guard, h,
    modal, renderPagination, renderTable, statusTag, toast, toastError,
} from '../ui.js';

/** 异常等级 → 工单优先级，和后端 AnomalyLevel 的映射保持一致 */
const LEVEL_TO_WORK_ORDER = { CRITICAL: 'P1', HIGH: 'P2', MEDIUM: 'P3', LOW: 'P4' };

const WORK_ORDER_LEVELS = [
    { value: 'P1', label: 'P1 紧急' },
    { value: 'P2', label: 'P2 严重' },
    { value: 'P3', label: 'P3 一般' },
    { value: 'P4', label: 'P4 轻微' },
];

export async function render(container, ctx) {
    const canConvert = auth.can('inspect:anomaly:convert');
    const canClose = auth.can('inspect:anomaly:close');
    const query = { pageNum: 1, pageSize: 10, status: '', level: '', equipmentId: '' };

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '巡检异常' }),
            h('p', { text: '现场判定为异常的检查项会自动生成异常单。转维修工单后由工单流程闭环，未转单的可直接关闭。' })),
        h('div', { class: 'page-actions' }));

    const statusSelect = h('select', { class: 'select', 'aria-label': '处理状态' },
        h('option', { value: '', text: '全部状态' }),
        ...Object.entries(ANOMALY_STATUS).map(([code, item]) => h('option', { value: code, text: item.label })));
    const levelSelect = h('select', { class: 'select', 'aria-label': '异常等级' },
        h('option', { value: '', text: '全部等级' }),
        ...Object.entries(ANOMALY_LEVEL).map(([code, item]) => h('option', { value: code, text: item.label })));

    const toolbar = h('div', { class: 'toolbar' },
        h('div', { class: 'field' }, h('label', { text: '处理状态' }), statusSelect),
        h('div', { class: 'field' }, h('label', { text: '异常等级' }), levelSelect),
        h('div', { class: 'spacer' }),
        h('button', { class: 'btn', type: 'button', text: '重置', onclick: () => resetQuery() }),
        h('button', { class: 'btn btn-primary', type: 'button', text: '查询', onclick: () => load(1) }));

    const summary = h('span', { class: 'hint', text: '' });
    const tableHost = h('div', {});
    const pagerHost = h('div', {});

    container.append(head, h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '异常清单' }), summary),
        toolbar, tableHost, pagerHost));

    [statusSelect, levelSelect].forEach((select) => select.addEventListener('change', () => load(1)));

    function resetQuery() {
        statusSelect.value = '';
        levelSelect.value = '';
        load(1);
    }

    async function load(pageNum = query.pageNum) {
        query.pageNum = pageNum;
        query.status = statusSelect.value;
        query.level = levelSelect.value;

        tableHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:58%"></div></div>';
        try {
            const data = await api.get('/api/inspection/anomalies', query);
            const rows = data.records || data.list || [];
            summary.textContent = `共 ${data.total} 条异常`;

            if (!rows.length) {
                tableHost.innerHTML = '<div class="table-empty">还没有异常记录。巡检里判定为异常的检查项会出现在这里</div>';
                pagerHost.innerHTML = '';
                return;
            }

            tableHost.innerHTML = '';
            const list = h('div', { class: 'anomaly-list' });
            rows.forEach((row) => list.append(card(row)));
            tableHost.append(list);

            pagerHost.innerHTML = '';
            pagerHost.append(renderPagination(data, { onChange: (page) => load(page) }));
        } catch (error) {
            tableHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function card(row) {
        const photoHost = h('div', { class: 'photo-row' });
        if (row.attachments && row.attachments.length) {
            row.attachments.forEach((file) => photoHost.append(thumb(file)));
        }

        const actions = [];
        if (row.workOrderId) {
            actions.push(h('span', {
                class: 'tag tag-blue',
                text: `工单 ${row.workOrderNo || row.workOrderId}`,
            }));
        } else {
            if (canConvert && row.status === 'OPEN') {
                actions.push(h('button', {
                    class: 'btn btn-sm btn-primary', type: 'button', text: '转维修工单',
                    onclick: () => convert(row),
                }));
            }
            if (canClose && row.status === 'OPEN') {
                actions.push(h('button', {
                    class: 'btn btn-sm', type: 'button', text: '关闭异常',
                    onclick: () => close(row),
                }));
            }
        }
        if (row.taskId) {
            actions.push(h('button', {
                class: 'btn btn-sm btn-ghost', type: 'button', text: '巡检记录',
                onclick: () => ctx.navigate(`/inspect/task/${row.taskId}`),
            }));
        }

        return h('div', { class: 'anomaly-item' },
            h('div', { class: 'anomaly-head' },
                h('span', { class: 'mono', text: row.anomalyNo }),
                h('span', { html: statusTag(ANOMALY_LEVEL, row.level) }),
                h('span', { html: statusTag(ANOMALY_STATUS, row.status) }),
                h('span', { class: 'hint', text: `${row.equipmentName || ''} ${row.assetNo ? '· ' + row.assetNo : ''}` }),
                h('span', { class: 'grow' }),
                h('span', { class: 'hint', text: formatDate(row.reportTime, true) })),
            h('div', { class: 'anomaly-body' },
                h('div', { class: 'anomaly-item-main' },
                    h('strong', { text: row.itemName || '现场异常' }),
                    h('span', { class: 'anomaly-value', text: row.itemValue ? `实测：${row.itemValue}` : '' })),
                h('div', { class: 'cell-sub', text: row.description || '' }),
                h('div', { class: 'cell-sub' },
                    `上报人：${row.reporterName || '—'}`
                    + (row.taskCode ? ` · 任务 ${row.taskCode}` : '')
                    + (row.closeTime ? ` · 关闭于 ${formatDate(row.closeTime, true)}` : '')),
                photoHost),
            h('div', { class: 'anomaly-foot' }, ...actions));
    }

    function thumb(file) {
        const img = h('img', { class: 'photo-thumb', alt: file.fileName || '现场照片', title: file.fileName || '' });
        api.objectUrl(`/api/files/${file.id}`)
            .then((url) => { img.src = url; })
            .catch(() => { img.replaceWith(h('span', { class: 'hint', text: '照片加载失败' })); });
        return h('div', { class: 'photo-cell' }, img,
            h('span', { class: 'cell-sub', text: formatSize(file.fileSize) }));
    }

    /* ---------------------------- 转工单 ---------------------------- */

    function convert(row) {
        const levelSelect2 = h('select', { class: 'select' },
            ...WORK_ORDER_LEVELS.map((level) => h('option', {
                value: level.value,
                text: level.label,
                selected: level.value === (LEVEL_TO_WORK_ORDER[row.level] || 'P3'),
            })));
        const titleInput = h('input', {
            class: 'input', type: 'text', maxlength: '100',
            value: `巡检异常：${row.itemName || '现场异常'}`,
        });
        const remarkInput = h('textarea', {
            class: 'input', rows: '3', placeholder: '补充说明，会追加到工单故障描述之后',
        });

        const body = h('div', {},
            h('div', { class: 'alert alert-info' },
                `${row.equipmentName || ''} · ${row.itemName || ''}${row.itemValue ? '：' + row.itemValue : ''}`),
            h('div', { class: 'form-grid' },
                h('div', { class: 'field grow' }, h('label', { text: '工单标题' }), titleInput),
                h('div', { class: 'field' }, h('label', { text: '优先级' }), levelSelect2),
                h('div', { class: 'field grow' }, h('label', { text: '补充说明' }), remarkInput)),
            h('div', { class: 'hint', text: '工单只记录关联关系；设备、检查项、实测值与现场照片由异常单实时联查，不会复制成两套数据。' }));

        const dialog = modal({
            title: '转维修工单',
            body,
            footer: [
                { label: '取消' },
                {
                    label: '创建工单',
                    type: 'primary',
                    close: false,
                    onClick: async ({ button }) => {
                        await guard(button, async () => {
                            try {
                                const workOrderId = await api.post(`/api/inspection/anomalies/${row.id}/work-order`, {
                                    level: levelSelect2.value,
                                    title: titleInput.value.trim() || null,
                                    remark: remarkInput.value.trim() || null,
                                });
                                dialog.close();
                                toast(`已创建维修工单 #${workOrderId}`, 'success', 3200);
                                load();
                            } catch (error) {
                                toastError(error);
                            }
                        });
                        return false;
                    },
                },
            ],
        });
    }

    /* ---------------------------- 关闭异常 ---------------------------- */

    async function close(row) {
        const remarkInput = h('input', { class: 'input', type: 'text', placeholder: '关闭原因，例如现场已处理、误报' });
        const dialog = modal({
            title: '关闭异常',
            body: h('div', {},
                h('div', { class: 'alert alert-warn', text: '关闭后不能再转维修工单，请确认现场已处理或确认属于误判。' }),
                h('div', { class: 'field' }, h('label', { text: '关闭原因' }), remarkInput)),
            footer: [
                { label: '取消' },
                {
                    label: '确认关闭',
                    type: 'primary',
                    close: false,
                    onClick: async ({ button }) => {
                        if (!remarkInput.value.trim()) {
                            toast('请填写关闭原因', 'error');
                            return false;
                        }
                        await guard(button, async () => {
                            try {
                                await api.post(`/api/inspection/anomalies/${row.id}/close?remark=`
                                    + encodeURIComponent(remarkInput.value.trim()));
                                dialog.close();
                                toast('异常已关闭', 'success');
                                load();
                            } catch (error) {
                                toastError(error);
                            }
                        });
                        return false;
                    },
                },
            ],
        });
    }

    await load(1);
}
