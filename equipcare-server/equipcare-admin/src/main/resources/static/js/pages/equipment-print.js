/**
 * 设备档案打印：A4 单页，含二维码、档案信息、最近巡检与维修历史、三方签字栏。
 * 打印样式在 app.css 的 @media print 中定义。
 */

import { api } from '../api.js';
import {
    EQUIPMENT_STATUS, INSPECT_STATUS, WORK_ORDER_STATUS, esc, formatDate, formatMoney, h,
} from '../ui.js';

export async function render(container, ctx) {
    const id = ctx.params.id;
    const data = await api.get(`/api/asset/equipment/${id}`);
    ctx.setTitle(`打印 · ${data.equipmentName}`);

    const toolbar = h('div', { class: 'print-toolbar' },
        h('button', { class: 'btn', type: 'button', text: '返回详情', onclick: () => ctx.navigate(`/equipment/${id}`) }),
        h('div', { style: 'display:flex;gap:10px' },
            h('span', { style: 'color:var(--text-muted);font-size:12.5px;align-self:center', text: 'A4 · 纵向 · 建议边距 14mm' }),
            h('button', { class: 'btn btn-primary', type: 'button', text: '打印 / 另存 PDF', onclick: () => window.print() })),
    );

    const qrImg = h('img', { alt: '设备二维码' });

    const statusMeta = EQUIPMENT_STATUS[data.status] || {};
    const cells = [
        ['资产编号', data.assetNo, 1],
        ['设备名称', data.equipmentName, 1],
        ['设备分类', data.categoryName, 1],
        ['当前状态', statusMeta.label || data.status, 1],
        ['所在位置', data.locationPath, 2],
        ['归属部门', data.deptName, 1],
        ['责任人', data.responsibleUserName, 1],
        ['品牌', data.brand, 1],
        ['型号', data.model, 1],
        ['规格参数', data.spec, 1],
        ['出厂序列号', data.serialNo, 1],
        ['制造商', data.manufacturer, 1],
        ['供应商', data.supplier, 1],
        ['采购日期', formatDate(data.purchaseDate), 1],
        ['安装日期', formatDate(data.installDate), 1],
        ['保修截止', formatDate(data.warrantyEndDate), 1],
        ['设备原值', formatMoney(data.originalValue), 1],
        ['健康评分', data.healthScore === null || data.healthScore === undefined ? '—' : data.healthScore, 1],
        ['最近巡检', formatDate(data.lastInspectTime, true), 1],
        ['下次巡检', formatDate(data.nextInspectTime, true), 1],
        ['备注', data.remark || '—', 4],
    ];

    const grid = h('div', { class: 'print-grid' });
    cells.forEach(([label, value, span]) => {
        grid.append(h('div', { class: `cell${span > 1 ? ` span-${span}` : ''}` },
            h('div', { class: 'k', text: label }),
            h('div', { class: 'v', text: value === null || value === undefined || value === '' ? '—' : String(value) })));
    });

    const inspectRows = (data.recentInspections || []).slice(0, 6).map((item) => `
        <tr>
            <td>${esc(item.taskCode)}</td>
            <td>${formatDate(item.planDate)}</td>
            <td>${(INSPECT_STATUS[item.status] || {}).label || item.status}</td>
            <td>${item.result === 'ABNORMAL' ? '异常' : item.result === 'NORMAL' ? '正常' : '—'}</td>
            <td>${item.abnormalCount ?? 0}</td>
            <td>${esc(item.assigneeName || '—')}</td>
            <td>${formatDate(item.finishTime, true)}</td>
        </tr>`).join('') || '<tr><td colspan="7" style="text-align:center;color:#8a93a3">暂无巡检记录</td></tr>';

    const repairRows = (data.repairHistory || []).slice(0, 6).map((item) => `
        <tr>
            <td>${esc(item.orderNo)}</td>
            <td>${esc(item.title)}</td>
            <td>${(WORK_ORDER_STATUS[item.status] || {}).label || item.status}</td>
            <td>${formatDate(item.reportTime, true)}</td>
            <td>${esc(item.assigneeName || '—')}</td>
            <td>${item.laborHours ?? '—'}</td>
            <td>${item.verifyResult === 'PASS' ? '通过' : item.verifyResult === 'REJECT' ? '驳回' : '—'}</td>
        </tr>`).join('') || '<tr><td colspan="7" style="text-align:center;color:#8a93a3">暂无维修记录</td></tr>';

    const sheet = h('div', { class: 'print-sheet' },
        h('div', { class: 'print-head' },
            h('div', {},
                h('h1', { text: '设备档案卡' }),
                h('div', { class: 'sub', text: '巡维云 EquipCare · 企业设备巡检与维修管理平台' }),
                h('div', { class: 'sub', text: `打印时间：${new Date().toLocaleString('zh-CN')}` })),
            h('div', { class: 'print-qr' }, qrImg, h('div', { text: '扫码查看设备档案' }))),

        grid,

        h('div', { class: 'print-section-title', text: '最近巡检记录' }),
        h('table', { class: 'print-table', html: `<thead><tr>
            <th style="width:22%">任务编号</th><th style="width:12%">计划日期</th><th style="width:12%">状态</th>
            <th style="width:10%">结果</th><th style="width:8%">异常项</th><th style="width:14%">执行人</th><th>完成时间</th>
        </tr></thead><tbody>${inspectRows}</tbody>` }),

        h('div', { class: 'print-section-title', text: '历史维修记录' }),
        h('table', { class: 'print-table', html: `<thead><tr>
            <th style="width:20%">工单号</th><th>标题</th><th style="width:11%">状态</th>
            <th style="width:15%">报修时间</th><th style="width:10%">维修人</th><th style="width:8%">工时</th><th style="width:8%">验收</th>
        </tr></thead><tbody>${repairRows}</tbody>` }),

        h('div', { class: 'print-sign' },
            h('div', { text: '设备责任人签字 / 日期' }),
            h('div', { text: '设备科审核 / 日期' }),
            h('div', { text: '财务 / 归档确认' })),

        h('div', { class: 'print-foot' },
            h('span', { text: `设备 ID：${data.id} · 二维码令牌：${data.qrToken}` }),
            h('span', { text: '本页由系统生成，涂改无效' })),
    );

    container.append(toolbar, sheet);

    try {
        const url = await api.objectUrl(`/api/asset/equipment/${id}/qrcode.png`, { size: 320, margin: 1 });
        qrImg.src = url;
    } catch (error) {
        qrImg.replaceWith(h('div', { style: 'font-size:10px;color:#8a93a3', text: '二维码加载失败' }));
    }
}
