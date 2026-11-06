/**
 * 备件 / 库存页面共用组件。
 */

import { api } from '../api.js';
import { esc, h, statusTag, STOCK_TXN_TYPE } from '../ui.js';

/** 加载备件分类（下拉与分类管理共用） */
export async function loadCategories() {
    try {
        return await api.get('/api/inventory/categories');
    } catch (error) {
        return [];
    }
}

/** 分类下拉选项 HTML */
export function categoryOptions(categories, selectedId) {
    return ['<option value="">全部分类</option>']
        .concat(categories.map(c =>
            `<option value="${c.id}" ${String(c.id) === String(selectedId || '') ? 'selected' : ''}>${esc(c.categoryName)}（${c.partCount}）</option>`))
        .join('');
}

/** 备件下拉选项 HTML（入库 / 出库 / 申领用） */
export function partOptions(parts, selectedId) {
    return ['<option value="">请选择备件</option>']
        .concat(parts.map(p => {
            const warn = p.belowSafety ? ' ⚠️' : '';
            return `<option value="${p.id}" ${String(p.id) === String(selectedId || '') ? 'selected' : ''}>${esc(p.partNo)} ${esc(p.partName)}（库存 ${p.currentStock} ${esc(p.unit)}）${warn}</option>`;
        }))
        .join('');
}

/** 全量备件（下拉用，带库存） */
export async function loadParts() {
    const page = await api.get('/api/inventory/parts?pageNum=1&pageSize=100&status=1');
    return page.records || [];
}

/** 流水类型标签 */
export function txnTypeTag(code) {
    return statusTag(STOCK_TXN_TYPE, code, code);
}

/** 「库存 x / 安全 y」单元格，低于安全库存标红 */
export function stockCell(part) {
    const below = Number(part.currentStock) < Number(part.safetyStock);
    // 低于安全库存用浅橙（warning）提示，红色留给真正缺货等更强信号
    return `<span class="${below ? 'stock-below' : ''}">${part.currentStock}</span> / ${part.safetyStock} ${esc(part.unit)}${below ? ' <span class="tag tag-amber">低于安全库存</span>' : ''}`;
}

/**
 * 备件选择器：select + 选中后回显库存。
 */
export function partPicker(parts, { onChange } = {}) {
    const select = h('select', { class: 'input' });
    select.innerHTML = partOptions(parts);
    const stockHint = h('div', { class: 'hint', text: ' ' });
    select.addEventListener('change', () => {
        const part = parts.find(p => String(p.id) === select.value);
        stockHint.textContent = part
            ? `当前库存 ${part.currentStock} ${part.unit}，安全库存 ${part.safetyStock}，单价 ¥${part.unitPrice}`
            : ' ';
        if (onChange) onChange(part);
    });
    return { node: h('div', {}, select, stockHint), select };
}

/**
 * 页面工具栏链接（入库 / 出库 / 流水 / 预警互跳），需 ctx.navigate。
 */
export function navLinks(ctx, current) {
    const links = [
        ['/spare/stock', '库存管理'],
        ['/spare/stock/in', '入库'],
        ['/spare/stock/out', '出库'],
        ['/spare/txn', '库存流水'],
        ['/spare/alert', '库存预警'],
    ];
    return h('div', { class: 'chip-row' },
        links.map(([path, label]) => h('button', {
            class: `chip ${path === current ? 'chip-active' : ''}`,
            type: 'button',
            text: label,
            onclick: () => ctx.navigate(path),
        })));
}
