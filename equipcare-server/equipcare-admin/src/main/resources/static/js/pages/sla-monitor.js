/**
 * SLA 预警中心：活跃工单实时窗口 + 超时预警记录 + 政策配置。
 *
 * 时限与百分比全部由后端按 sla_policy 计算，前端只展示，不持有任何时限数字。
 */

import { api, auth } from '../api.js';
import { esc, formatDate, guard, h, minutesText, modal, renderPagination, renderTable, SLA_STAGE, SLA_STATUS, statusTag, toast, toastError, WORK_ORDER_LEVEL } from '../ui.js';

export async function render(container, ctx) {
    const canManage = auth.can('sla:policy:manage');

    const activeHost = h('div', {});
    const alertHost = h('div', {});
    const alertPager = h('div', {});
    const policyHost = h('div', {});
    const alertSummary = h('span', { class: 'hint' });

    const stageFilter = h('select', { class: 'input' },
        h('option', { value: '', text: '全部阶段' }),
        Object.entries(SLA_STAGE).map(([code, item]) => h('option', { value: code, text: item.label })));
    const levelFilter = h('select', { class: 'input' },
        h('option', { value: '', text: '全部等级' }),
        Object.entries(WORK_ORDER_LEVEL).map(([code, item]) => h('option', { value: code, text: item.label })));

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: 'SLA 预警中心' }),
                h('p', { text: '待派单 / 待接单 / 维修中 / 待验收四阶段超时监控。同一工单同阶段每天只通知一次；时限在「政策配置」中按等级调整。' })),
            h('div', { class: 'page-actions' },
                h('button', { class: 'btn btn-ghost', type: 'button', text: '立即扫描', onclick: () => manualScan() }),
                canManage ? h('button', { class: 'btn btn-primary', type: 'button', text: '政策配置', onclick: () => openPolicyManager() }) : null)),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: '进行中工单 SLA 窗口' })),
            activeHost),
        h('div', { class: 'card' },
            h('div', { class: 'card-head' },
                h('h2', { text: '超时预警记录' }), alertSummary,
                h('div', { class: 'toolbar' }, stageFilter, levelFilter,
                    h('button', { class: 'btn btn-sm btn-ghost', type: 'button', text: '刷新', onclick: () => loadAlerts(1) }))),
            alertHost,
            h('div', { class: 'card-foot' }, alertPager)));

    await loadActive();
    await loadAlerts(1);

    async function loadActive() {
        activeHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        try {
            const orders = await api.get('/api/sla/active-orders');
            activeHost.innerHTML = '';
            if (!orders.length) {
                activeHost.append(h('div', { class: 'table-empty', text: '当前没有进行中的工单' }));
                return;
            }
            activeHost.innerHTML = renderTable({
                columns: [
                    { title: '工单', render: r => `<span class="mono">${esc(r.orderNo)}</span>` },
                    { title: '标题', render: r => esc(r.title || '—') },
                    { title: '设备', render: r => esc(r.equipmentName || '—') },
                    { title: '等级', render: r => esc(r.levelLabel || r.level) },
                    { title: '阶段', render: r => esc(r.stageLabel || r.stage) },
                    { title: 'SLA', render: r => statusTag(SLA_STATUS, r.slaStatus, r.slaStatus) },
                    { title: '窗口进度', render: r => {
                        const percent = Math.min(100, Number(r.percent) || 0);
                        const over = Number(r.percent) > 100;
                        const color = over ? 'var(--danger)' : percent >= 80 ? 'var(--warning)' : 'var(--success)';
                        return `<div style="min-width:140px"><div style="background:var(--border);border-radius:4px;height:8px;overflow:hidden"><div style="width:${percent}%;height:100%;background:${color}"></div></div><span class="cell-sub">${Number(r.percent)}% · ${minutesText(r.elapsedMinutes)} / ${minutesText(r.totalMinutes)}</span></div>`;
                    } },
                    { title: '截止', render: r => formatDate(r.dueTime, true) },
                ],
                rows: orders,
                empty: '当前没有进行中的工单',
            });
        } catch (error) {
            activeHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    let alertMeta = null;

    async function loadAlerts(pageNum) {
        alertHost.innerHTML = '<div class="card-body"><div class="skeleton" style="width:60%"></div></div>';
        const params = new URLSearchParams({ pageNum: String(pageNum || 1), pageSize: '10' });
        if (stageFilter.value) params.set('stage', stageFilter.value);
        if (levelFilter.value) params.set('level', levelFilter.value);
        try {
            const page = await api.get('/api/sla/alerts?' + params.toString());
            alertMeta = { pageNum: page.pageNum, pageSize: page.pageSize, total: page.total };
            alertSummary.textContent = `共 ${page.total} 条预警`;
            renderAlertRows(page.records || []);
            alertPager.innerHTML = '';
            alertPager.append(renderPagination(alertMeta, { onChange: loadAlerts }));
        } catch (error) {
            alertHost.innerHTML = `<div class="table-empty" style="color:var(--danger)">${esc(error.message)}</div>`;
        }
    }

    function renderAlertRows(records) {
        alertHost.innerHTML = '';
        alertHost.innerHTML = renderTable({
            columns: [
                { title: '工单', render: r => `<span class="mono">${esc(r.orderNo || `#${r.workOrderId}`)}</span>` },
                { title: '标题', render: r => esc(r.title || '—') },
                { title: '设备', render: r => esc(r.equipmentName || '—') },
                { title: '超时阶段', render: r => esc(r.stageLabel || r.stage) },
                { title: '等级', render: r => esc(r.levelLabel || r.orderLevel) },
                { title: '超时时长', render: r => `<strong style="color:var(--danger)">${minutesText(r.overdueMinutes)}</strong>` },
                { title: '当日提醒', render: r => `${r.alertCount} 次` },
                { title: '最近提醒', render: r => formatDate(r.lastAlertTime, true) },
                { title: '操作', render: r => `<button class="btn btn-sm btn-ghost" data-id="${r.workOrderId}">查看工单</button>` },
            ],
            rows: records,
            empty: '暂无超时预警 —— 一切正常',
        });
        alertHost.querySelectorAll('button[data-id]').forEach(btn =>
            btn.addEventListener('click', () => ctx.navigate(`/workorder/${btn.dataset.id}`)));
    }

    stageFilter.addEventListener('change', () => loadAlerts(1));
    levelFilter.addEventListener('change', () => loadAlerts(1));

    async function manualScan() {
        try {
            const notified = await api.post('/api/sla/alerts/scan');
            toast(notified > 0 ? `扫描完成，新增超时工单 ${notified} 张` : '扫描完成，无新增超时');
            await Promise.all([loadActive(), loadAlerts(1)]);
        } catch (error) {
            toastError(error);
        }
    }

    // ------------------------------------------------------------------
    // 政策配置
    // ------------------------------------------------------------------
    async function openPolicyManager() {
        let policies = [];
        try {
            policies = await api.get('/api/sla/policies');
        } catch (error) {
            toastError(error);
            return;
        }
        const body = h('div', { class: 'stack' },
            policies.map(p => h('div', { class: 'stack-row' },
                h('div', { class: 'stack-head' },
                    h('strong', { text: `${p.level} ${p.levelLabel}` }),
                    h('span', { class: 'cell-sub', text: p.policyName || '' }),
                    p.enabled ? h('span', { class: 'tag tag-success', text: '启用' }) : h('span', { class: 'tag tag-muted', text: '停用' })),
                h('div', { class: 'cell-sub' },
                    `响应 ${minutesText(p.responseMinutes)} · 修复 ${minutesText(p.resolveMinutes)} · 验收 ${minutesText(p.verifyMinutes)} · 预警线 ${p.warnPercent}% · 升级通知 ${p.escalateRoleCode || '—'}`),
                h('div', { style: 'margin-top:6px' },
                    h('button', { class: 'btn btn-sm btn-ghost', type: 'button', text: '编辑', onclick: () => openPolicyForm(p) }))),
            ),
            h('div', { class: 'hint', text: '时限调整立即对后续建单 / 派单 / 提交验收生效；已生成的工单时限列不追溯。' }));
        modal({ title: 'SLA 政策配置', body, width: '640px' });
    }

    function openPolicyForm(p) {
        const body = h('form', { class: 'form' },
            h('div', { class: 'hint', text: `${p.level}（${p.levelLabel}）· ${p.policyName || ''}` }),
            field('政策名称', h('input', { class: 'input', name: 'policyName', value: p.policyName || '', maxlength: '60' })),
            field('响应时限（分钟）*', h('input', { class: 'input', type: 'number', name: 'responseMinutes', min: '1', value: p.responseMinutes })),
            field('修复时限（分钟）*', h('input', { class: 'input', type: 'number', name: 'resolveMinutes', min: '1', value: p.resolveMinutes })),
            field('验收时限（分钟）*', h('input', { class: 'input', type: 'number', name: 'verifyMinutes', min: '1', value: p.verifyMinutes })),
            field('预警阈值（%）*', h('input', { class: 'input', type: 'number', name: 'warnPercent', min: '1', max: '100', value: p.warnPercent })),
            field('超时升级通知角色', h('input', { class: 'input', name: 'escalateRoleCode', value: p.escalateRoleCode || '', placeholder: '如 MANAGER / EQUIP_ADMIN', maxlength: '50' })),
            field('启用', h('select', { class: 'input', name: 'enabled' },
                `<option value="true" ${p.enabled ? 'selected' : ''}>启用</option><option value="false" ${p.enabled ? '' : 'selected'}>停用</option>`)),
            field('说明', h('input', { class: 'input', name: 'remark', value: p.remark || '', maxlength: '300' })));
        const dialog = modal({
            title: `编辑 SLA 政策：${p.level}`,
            body,
            footer: [
                h('button', { class: 'btn btn-ghost', type: 'button', text: '取消', onclick: () => dialog.close() }),
                h('button', { class: 'btn btn-primary', type: 'button', text: '保存' }),
            ],
        });
        dialog.footer.querySelector('.btn-primary').addEventListener('click', guard(async () => {
            const data = {
                policyName: body.querySelector('[name=policyName]').value,
                responseMinutes: Number(body.querySelector('[name=responseMinutes]').value),
                resolveMinutes: Number(body.querySelector('[name=resolveMinutes]').value),
                verifyMinutes: Number(body.querySelector('[name=verifyMinutes]').value),
                warnPercent: Number(body.querySelector('[name=warnPercent]').value),
                escalateRoleCode: body.querySelector('[name=escalateRoleCode]').value,
                enabled: body.querySelector('[name=enabled]').value === 'true',
                remark: body.querySelector('[name=remark]').value,
            };
            await api.put(`/api/sla/policies/${p.level}`, data);
            toast('政策已更新');
            dialog.close();
            await loadActive();
        }));
    }
}

function field(label, control) {
    return h('div', { class: 'field' }, h('label', { text: label }), control);
}
