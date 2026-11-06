/**
 * 设备详情：档案、图片附件、巡检记录、维修历史、状态变更。
 *
 * 图片与附件接口需要 Bearer 令牌，因此统一用 fetch 取 blob 再挂到 img.src，
 * 不把令牌拼进 URL。
 */

import { api, auth } from '../api.js';
import {
    EQUIPMENT_STATUS, INSPECT_STATUS, WORK_ORDER_STATUS, confirmDialog, esc, formatDate,
    formatMoney, formatSize, guard, h, statusTag, toast, toastError, modal,
} from '../ui.js';

export async function render(container, ctx) {
    const id = ctx.params.id;
    const detail = await api.get(`/api/asset/equipment/${id}`);
    ctx.setTitle(`设备 · ${detail.equipmentName}`);

    const canUpdate = auth.can('asset:equipment:update');
    const canStatus = auth.can('asset:equipment:status');
    const canQr = auth.can('asset:equipment:qrcode');
    const canDelete = auth.can('asset:equipment:delete');

    /* ------------------------------ 头部 ------------------------------ */
    const cover = h('div', { class: 'hero-cover', text: '暂无图片' });
    if (detail.coverUrl) {
        loadImage(cover, `/api/files/${findCoverFileId(detail)}`);
    }

    const actions = h('div', { class: 'page-actions' },
        canStatus ? h('button', {
            class: 'btn', type: 'button', text: '变更状态',
            onclick: () => openStatusDialog(),
        }) : null,
        canQr ? h('button', {
            class: 'btn', type: 'button', text: '二维码',
            onclick: () => ctx.navigate(`/equipment/${id}/qrcode`),
        }) : null,
        h('button', {
            class: 'btn', type: 'button', text: '打印档案',
            onclick: () => ctx.navigate(`/equipment/${id}/print`),
        }),
        canUpdate ? h('button', {
            class: 'btn btn-primary', type: 'button', text: '编辑',
            onclick: () => ctx.navigate(`/equipment/${id}/edit`),
        }) : null,
    );

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: detail.equipmentName }),
            h('p', { text: `资产编号 ${detail.assetNo} · 建立于 ${formatDate(detail.createTime, true)}` })),
        actions,
    );

    const hero = h('div', { class: 'detail-hero' },
        cover,
        h('div', { class: 'hero-main' },
            h('h2', { text: detail.equipmentName }),
            h('div', { class: 'hero-sub', text: `${detail.categoryName || '未分类'} · ${detail.locationPath || '未设置位置'}` }),
            h('div', { class: 'hero-tags' },
                h('span', { class: 'tag ' + (EQUIPMENT_STATUS[detail.status] || {}).tag, text: (EQUIPMENT_STATUS[detail.status] || {}).label || detail.status }),
                h('span', { class: 'tag tag-blue plain', text: `健康分 ${detail.healthScore ?? '—'}` }),
                h('span', { class: 'tag tag-gray plain', text: `责任人 ${detail.responsibleUserName || '未指定'}` }),
                h('span', { class: 'tag tag-gray plain', text: `历史工单 ${detail.repairCount || 0} 单` }),
            )),
        h('div', { style: 'margin-left:auto;text-align:right;min-width:180px' },
            h('div', { class: 'kv', style: 'grid-template-columns:auto auto;gap:6px 10px;justify-content:end' },
                h('span', { text: '最近巡检' }), h('b', { text: formatDate(detail.lastInspectTime, true) }),
                h('span', { text: '下次巡检' }), h('b', { text: formatDate(detail.nextInspectTime, true) }),
                h('span', { text: '保修截止' }), h('b', { text: formatDate(detail.warrantyEndDate) }),
            )),
    );

    /* ------------------------------ 标签页 ------------------------------ */
    const tabs = [
        { key: 'profile', label: '基本信息' },
        { key: 'files', label: `图片与附件 (${(detail.files || []).length})` },
        { key: 'inspect', label: `巡检记录 (${(detail.recentInspections || []).length})` },
        { key: 'repair', label: `维修记录 (${detail.repairCount || 0})` },
        { key: 'parts', label: '备件消耗' },
        { key: 'lifecycle', label: '生命周期' },
    ];
    const tabBar = h('div', { class: 'section-tabs', role: 'tablist' });
    const panel = h('div', { class: 'section-body' });

    const card = h('div', { class: 'card' }, hero, tabBar, panel);
    container.append(head, card);

    tabs.forEach((tab) => {
        tabBar.append(h('button', {
            type: 'button',
            class: 'tab-btn',
            'aria-selected': tab.key === 'profile' ? 'true' : 'false',
            text: tab.label,
            onclick: () => {
                tabBar.querySelectorAll('button').forEach((btn) => {
                    btn.classList.remove('active');
                    btn.setAttribute('aria-selected', 'false');
                });
                const current = tabBar.querySelectorAll('button')[tabs.indexOf(tab)];
                current.classList.add('active');
                current.setAttribute('aria-selected', 'true');
                switchTab(tab.key);
            },
        }));
    });
    tabBar.firstChild.classList.add('active');

    function switchTab(key) {
        panel.innerHTML = '';
        if (key === 'profile') {
            panel.append(profileView(detail));
        } else if (key === 'files') {
            panel.append(filesView());
        } else if (key === 'inspect') {
            panel.append(inspectView(detail.recentInspections || []));
        } else if (key === 'parts') {
            panel.append(partsView(detail.repairHistory || []));
        } else if (key === 'lifecycle') {
            panel.append(lifecycleView(detail));
        } else {
            panel.append(repairView(detail.repairHistory || []));
        }
    }

    switchTab('profile');

    /* ------------------------------ 档案信息 ------------------------------ */
    function profileView(data) {
        const rows = [
            ['资产编号', data.assetNo],
            ['设备名称', data.equipmentName],
            ['设备分类', data.categoryName],
            ['所在位置', data.locationPath],
            ['归属部门', data.deptName],
            ['设备责任人', data.responsibleUserName],
            ['品牌 / 型号', [data.brand, data.model].filter(Boolean).join(' / ')],
            ['规格参数', data.spec],
            ['出厂序列号', data.serialNo],
            ['制造商', data.manufacturer],
            ['供应商', data.supplier],
            ['采购日期', formatDate(data.purchaseDate)],
            ['安装日期', formatDate(data.installDate)],
            ['保修截止', formatDate(data.warrantyEndDate)],
            ['设备原值', formatMoney(data.originalValue)],
            ['当前状态', (EQUIPMENT_STATUS[data.status] || {}).label || data.status],
            ['健康评分', data.healthScore],
            ['最近巡检', formatDate(data.lastInspectTime, true)],
            ['下次巡检', formatDate(data.nextInspectTime, true)],
            ['备注', data.remark],
        ];
        const grid = h('div', { class: 'grid-2', style: 'gap:0 32px' });
        rows.forEach(([label, value]) => {
            grid.append(h('div', { style: 'display:flex;gap:12px;padding:9px 0;border-bottom:1px solid #eef1f6' },
                h('span', { style: 'flex:0 0 96px;color:var(--text-muted);font-size:12.5px', text: label }),
                h('span', { style: 'flex:1;font-size:13px;word-break:break-all', text: value === null || value === undefined || value === '' ? '—' : String(value) })));
        });
        return grid;
    }

    /* ------------------------------ 图片附件 ------------------------------ */
    function filesView() {
        const wrap = h('div', {});
        const canManage = canUpdate;

        const toolbar = h('div', { style: 'display:flex;gap:10px;align-items:center;margin-bottom:14px' },
            canManage ? uploadButton('上传图片', 'IMAGE') : null,
            canManage ? uploadButton('上传附件', 'ATTACHMENT') : null,
            h('span', { class: 'hint', style: 'color:var(--text-muted);font-size:12px', text: '支持 jpg/png/gif/webp、pdf/docx/xlsx，单文件 ≤ 10MB；首张图片作为封面' }),
        );
        wrap.append(toolbar);

        const files = detail.files || [];
        if (!files.length) {
            wrap.append(h('div', { class: 'empty', text: '尚未上传图片或附件' }));
            return wrap;
        }

        const grid = h('div', { class: 'file-grid' });
        files.forEach((file) => {
            const isImage = (file.contentType || '').startsWith('image/');
            const thumb = h('div', { class: 'thumb', text: isImage ? '加载中…' : '附件' });
            if (isImage) {
                loadImage(thumb, `/api/files/${file.fileId}`);
            }
            const cardNode = h('div', { class: 'file-card' },
                thumb,
                h('div', { class: 'meta' },
                    h('b', { title: file.fileName, text: file.fileName }),
                    h('span', { text: `${formatSize(file.fileSize)} · ${file.fileType === 'IMAGE' ? '图片' : '附件'}` })),
                h('div', { class: 'file-actions' },
                    h('button', {
                        class: 'btn btn-sm btn-ghost', type: 'button', text: '下载',
                        onclick: () => download(file),
                    }),
                    canManage ? h('button', {
                        class: 'btn btn-sm btn-danger', type: 'button', text: '移除',
                        onclick: () => removeFile(file),
                    }) : null),
            );
            grid.append(cardNode);
        });
        wrap.append(grid);
        return wrap;
    }

    function uploadButton(label, fileType) {
        const picker = h('input', {
            type: 'file',
            style: 'display:none',
            accept: fileType === 'IMAGE' ? 'image/*' : '.pdf,.doc,.docx,.xls,.xlsx,.zip',
            onchange: async (event) => {
                const file = event.target.files[0];
                event.target.value = '';
                if (!file) {
                    return;
                }
                if (file.size > 10 * 1024 * 1024) {
                    toast('文件超过 10MB 限制', 'error');
                    return;
                }
                await guard(button, async () => {
                    const uploaded = await api.upload(file, { bizType: 'EQUIPMENT', bizId: id });
                    await api.post(`/api/asset/equipment/${id}/files`, {
                        fileId: uploaded.fileId,
                        fileType,
                    });
                    toast('上传成功', 'success');
                    await refresh({ keepTab: 'files' });
                });
            },
        });
        const button = h('button', {
            class: 'btn btn-sm', type: 'button', text: label,
            onclick: () => picker.click(),
        });
        return h('span', {}, button, picker);
    }

    async function removeFile(file) {
        const ok = await confirmDialog({
            title: '移除文件',
            message: `确认从设备上移除 <b>${esc(file.fileName)}</b>？<br>仅解除关联，不会删除已上传的物理文件。`,
            confirmText: '移除',
            danger: true,
        });
        if (!ok) {
            return;
        }
        try {
            await api.del(`/api/asset/equipment/${id}/files/${file.fileId}`);
            toast('已移除', 'success');
            await refresh({ keepTab: 'files' });
        } catch (error) {
            toastError(error);
        }
    }

    async function download(file) {
        try {
            const url = await api.objectUrl(`/api/files/${file.fileId}`);
            const link = h('a', { href: url, download: file.fileName });
            document.body.append(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 4000);
        } catch (error) {
            toastError(error);
        }
    }

    /* ------------------------------ 巡检记录 ------------------------------ */
    function inspectView(list) {
        if (!list.length) {
            return h('div', { class: 'empty', text: '暂无巡检记录' });
        }
        const table = h('div', { class: 'table-wrap' });
        const rows = list.map((item) => `
            <tr>
                <td class="mono">${esc(item.taskCode)}</td>
                <td>${formatDate(item.planDate)}</td>
                <td>${statusTag(INSPECT_STATUS, item.status)}</td>
                <td>${item.result === 'ABNORMAL' ? '<span class="tag tag-red">异常</span>' : item.result === 'NORMAL' ? '<span class="tag tag-green">正常</span>' : '—'}</td>
                <td>${item.abnormalCount > 0 ? `<b style="color:var(--danger)">${item.abnormalCount}</b>` : '0'}</td>
                <td>${esc(item.assigneeName || '—')}</td>
                <td>${formatDate(item.submitTime, true)}</td>
                <td>${item.overdueFlag === 1 ? '<span class="tag tag-red">逾期</span>' : '—'}</td>
            </tr>`).join('');
        table.innerHTML = `<table class="data"><thead><tr>
            <th>任务编号</th><th>计划日期</th><th>状态</th><th>结果</th><th>异常项</th><th>执行人</th><th>提交时间</th><th>逾期</th>
        </tr></thead><tbody>${rows}</tbody></table>`;
        return table;
    }

    /* ------------------------------ 维修历史 ------------------------------ */
    function repairView(list) {
        if (!list.length) {
            return h('div', { class: 'empty', text: '该设备暂无维修工单' });
        }
        const table = h('div', { class: 'table-wrap' });
        const rows = list.map((item) => `
            <tr>
                <td class="mono">${esc(item.orderNo)}</td>
                <td>${esc(item.title)}</td>
                <td>${statusTag(WORK_ORDER_STATUS, item.status)}</td>
                <td>${esc(item.level || '—')}</td>
                <td>${formatDate(item.reportTime, true)}</td>
                <td>${esc(item.assigneeName || '—')}</td>
                <td>${item.laborHours ?? '—'}</td>
                <td>${item.verifyResult === 'PASS' ? '<span class="tag tag-green">通过</span>' : item.verifyResult === 'REJECT' ? '<span class="tag tag-red">驳回</span>' : '—'}</td>
                <td>${formatMoney(item.spareCost)}</td>
            </tr>`).join('');
        table.innerHTML = `<table class="data"><thead><tr>
            <th>工单号</th><th>标题</th><th>状态</th><th>等级</th><th>报修时间</th><th>维修人</th><th>工时(h)</th><th>验收</th><th>备件成本</th>
        </tr></thead><tbody>${rows}</tbody></table>`;
        return table;
    }

    /* ------------------------------ 备件消耗（按维修工单聚合，真实数据） ------------------------------ */
    function partsView(list) {
        const withParts = list.filter((item) => Number(item.spareCost || 0) > 0);
        const total = withParts.reduce((sum, item) => sum + Number(item.spareCost || 0), 0);

        const head = h('div', { style: 'display:flex;align-items:baseline;gap:14px;margin-bottom:12px' },
            h('b', { text: `累计备件成本 ${formatMoney(total)}` }),
            h('span', { class: 'hint', text: `来自 ${withParts.length} 张有备件消耗的工单（按工单备件费聚合）` }));
        if (!withParts.length) {
            return h('div', {}, head, h('div', { class: 'empty', text: '暂无备件消耗记录' }));
        }
        const table = h('div', { class: 'table-wrap' });
        const rows = withParts.map((item) => `
            <tr>
                <td class="mono">${esc(item.orderNo)}</td>
                <td>${esc(item.title)}</td>
                <td>${formatDate(item.finishTime, true)}</td>
                <td>${esc(item.assigneeName || '—')}</td>
                <td>${formatMoney(item.spareCost)}</td>
            </tr>`).join('');
        table.innerHTML = `<table class="data"><thead><tr>
            <th>工单号</th><th>标题</th><th>完成时间</th><th>维修人</th><th>备件成本</th>
        </tr></thead><tbody>${rows}</tbody></table>`;
        return h('div', {}, head, table);
    }

    /* ------------------------------ 生命周期（真实字段时间线） ------------------------------ */
    function lifecycleView(data) {
        const events = [
            { time: data.purchaseDate, title: '采购入库', desc: data.originalValue != null ? `设备原值 ${formatMoney(data.originalValue)}` : '' },
            { time: data.installDate, title: '安装调试', desc: data.locationPath ? `安装位置：${data.locationPath}` : '' },
            { time: data.warrantyEndDate, title: '保修截止', desc: data.supplier ? `供应商：${data.supplier}` : '' },
            ...(data.repairHistory || []).slice(0, 10).map((item) => ({
                time: item.reportTime,
                title: `维修工单 · ${(WORK_ORDER_STATUS[item.status] || {}).label || item.status}`,
                desc: `${item.orderNo} ${item.repairSummary || item.title || ''}`,
            })),
            { time: data.lastInspectTime, title: '最近巡检', desc: data.lastInspectTime ? `巡检结果见「巡检记录」页签` : '' },
        ].filter((event) => event.time)
            .sort((a, b) => new Date(a.time) - new Date(b.time));

        if (!events.length) {
            return h('div', { class: 'empty', text: '暂无生命周期事件' });
        }
        const timeline = h('div', { class: 'life-timeline' });
        events.forEach((event) => {
            timeline.append(
                h('div', { class: 'life-dot' }),
                h('div', { class: 'life-item' },
                    h('div', { class: 'life-time', text: formatDate(event.time, true) }),
                    h('b', { text: event.title }),
                    event.desc ? h('span', { class: 'hint', text: event.desc }) : null));
        });
        const wrap = h('div', { class: 'life-wrap' }, timeline);
        // 当前状态与健康分摘要
        wrap.prepend(h('div', { style: 'display:flex;gap:10px;margin-bottom:18px' },
            h('span', { class: 'tag ' + ((EQUIPMENT_STATUS[data.status] || {}).tag || 'tag-gray'), text: `当前状态：${(EQUIPMENT_STATUS[data.status] || {}).label || data.status}` }),
            h('span', { class: 'tag tag-blue plain', text: `健康分 ${data.healthScore ?? '—'}` }),
            h('span', { class: 'tag tag-gray plain', text: `累计维修 ${data.repairCount || 0} 单` })));
        return wrap;
    }

    /* ------------------------------ 状态变更 ------------------------------ */
    function openStatusDialog() {
        const selectNode = h('select', { class: 'select', name: 'status' });
        Object.entries(EQUIPMENT_STATUS).forEach(([code, item]) => {
            if (code === 'REPAIRING') {
                return; // 维修中由工单驱动
            }
            selectNode.append(h('option', { value: code, text: item.label }));
        });
        selectNode.value = detail.status === 'SCRAPPED' ? 'SCRAPPED' : 'IDLE';

        const reason = h('textarea', { class: 'textarea', placeholder: '报废必填；其它状态可选填' });
        const alertBox = h('div', { class: 'alert alert-warn', text: '「维修中」状态由工单流转自动维护，这里不能手工设置；报废后不可恢复。' });
        const disabled = detail.status === 'SCRAPPED' || detail.status === 'REPAIRING';

        const body = h('div', {},
            alertBox,
            h('div', { class: 'field' }, h('label', { class: 'required', text: '目标状态' }), selectNode),
            h('div', { class: 'field' }, h('label', { text: '变更原因' }), reason),
        );
        if (detail.status === 'REPAIRING') {
            body.prepend(h('div', { class: 'alert alert-error', text: '设备维修中，请先完成或取消关联工单后再变更状态。' }));
        }

        modal({
            title: '设备状态变更',
            body,
            footer: [
                { label: '取消' },
                {
                    label: '确认变更',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        const target = selectNode.value;
                        if (target === 'SCRAPPED' && !reason.value.trim()) {
                            toast('报废必须填写原因', 'error');
                            return false;
                        }
                        let done = false;
                        await guard(button, async () => {
                            await api.patch(`/api/asset/equipment/${id}/status`, {
                                status: target,
                                reason: reason.value.trim() || null,
                            });
                            toast('状态已更新', 'success');
                            done = true;
                        });
                        if (done) {
                            await refresh({ keepTab: 'profile' });
                        }
                        return done;
                    },
                },
            ],
        });
        if (disabled) {
            selectNode.disabled = true;
        }
    }

    /* ------------------------------ 刷新 ------------------------------ */
    async function refresh({ keepTab }) {
        const fresh = await api.get(`/api/asset/equipment/${id}`);
        Object.assign(detail, fresh);
        const index = tabs.findIndex((tab) => tab.key === keepTab);
        if (index >= 0) {
            tabBar.querySelectorAll('button')[index].click();
        }
        // 头部状态标签同步
        const tag = hero.querySelector('.hero-tags .tag');
        if (tag) {
            const meta = EQUIPMENT_STATUS[detail.status] || {};
            tag.className = `tag ${meta.tag || 'tag-gray'}`;
            tag.textContent = meta.label || detail.status;
        }
    }

    /* ------------------------------ 删除 ------------------------------ */
    if (canDelete) {
        actions.append(h('button', {
            class: 'btn btn-danger', type: 'button', text: '删除',
            onclick: async () => {
                const ok = await confirmDialog({
                    title: '删除设备',
                    message: `确认删除设备 <b>${esc(detail.assetNo)} ${esc(detail.equipmentName)}</b>？<br>逻辑删除，历史工单与巡检记录仍保留。`,
                    confirmText: '确认删除',
                    danger: true,
                });
                if (!ok) {
                    return;
                }
                try {
                    await api.del(`/api/asset/equipment/${id}`);
                    toast('设备已删除', 'success');
                    ctx.navigate('/equipment');
                } catch (error) {
                    toastError(error);
                }
            },
        }));
    }
}

/* ---------------- 工具 ---------------- */

function findCoverFileId(detail) {
    const image = (detail.files || []).find((file) => file.fileType === 'IMAGE');
    return image ? image.fileId : null;
}

async function loadImage(container, path) {
    try {
        const url = await api.objectUrl(path);
        container.innerHTML = '';
        const img = h('img', { alt: '设备图片', src: url });
        container.append(img);
    } catch (error) {
        container.textContent = '图片加载失败';
    }
}
