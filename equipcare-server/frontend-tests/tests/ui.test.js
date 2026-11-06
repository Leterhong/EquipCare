/**
 * UI 工具（ui.js）单元测试：XSS 转义、空数据表格、分页、格式化、错误提示。
 */
import { describe, it, expect } from 'vitest';
import {
    esc, h, renderTable, renderPagination, formatDate, formatMoney, toastError,
} from '@static/ui.js';
import { ApiError } from '@static/api.js';
import { toastText } from './helpers.js';

describe('esc() HTML 转义（防 XSS）', () => {
    it('转义全部危险字符', () => {
        expect(esc('<script>alert("x")</script>'))
            .toBe('&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;');
    });

    it('null / undefined 返回空串', () => {
        expect(esc(null)).toBe('');
        expect(esc(undefined)).toBe('');
    });

    it('单引号也被转义', () => {
        expect(esc("it's")).toBe('it&#39;s');
    });
});

describe('h() DOM 构造', () => {
    it('text 属性使用 textContent（不解析 HTML）', () => {
        const node = h('div', { text: '<b>bold</b>' });
        expect(node.textContent).toBe('<b>bold</b>');
        expect(node.querySelector('b')).toBeNull();
    });

    it('事件属性绑定监听器，false/null 属性被忽略', () => {
        let clicked = false;
        const node = h('button', { type: 'button', onclick: () => { clicked = true; }, disabled: false, title: null });
        node.click();
        expect(clicked).toBe(true);
        expect(node.hasAttribute('disabled')).toBe(false);
        expect(node.hasAttribute('title')).toBe(false);
    });
});

describe('renderTable() 空数据与列渲染', () => {
    it('rows 为空时显示「暂无数据」空态', () => {
        const html = renderTable({ columns: [{ key: 'a', title: 'A' }], rows: [] });
        expect(html).toContain('table-empty');
        expect(html).toContain('暂无数据');
        expect(html).not.toContain('<td');
    });

    it('rows 为 null/undefined 同样返回空态', () => {
        expect(renderTable({ columns: [], rows: null })).toContain('暂无数据');
    });

    it('有数据时渲染表头与单元格，单元格值经 esc 转义', () => {
        const html = renderTable({
            columns: [{ key: 'name', title: '<X>' }],
            rows: [{ name: '<img src=x onerror=1>' }],
        });
        expect(html).toContain('<th>&lt;X&gt;</th>');
        expect(html).toContain('&lt;img src=x onerror=1&gt;');
        expect(html).not.toContain('<img src=x');
    });

    it('自定义 render 列优先于字段取值', () => {
        const html = renderTable({
            columns: [
                { key: 'status', title: '状态' },
                { key: 'x', title: '自定义', render: (row) => `<em>${row.status}</em>` },
            ],
            rows: [{ status: 'COMPLETED' }],
        });
        expect(html).toContain('<em>COMPLETED</em>');
    });
});

describe('renderPagination() 分页', () => {
    it('只有一页时上一页/下一页都禁用', () => {
        const wrap = renderPagination({ total: 5, pageNum: 1, pageSize: 10 }, {});
        const btns = wrap.querySelectorAll('button');
        expect(btns[0].disabled).toBe(true);        // 上一页
        expect(btns[btns.length - 1].disabled).toBe(true); // 下一页
        expect(wrap.textContent).toContain('共 5 条 · 第 1/1 页');
    });

    it('点击页码回调页号', () => {
        const seen = [];
        const wrap = renderPagination({ total: 55, pageNum: 2, pageSize: 10 }, { onChange: (p) => seen.push(p) });
        const page3 = Array.from(wrap.querySelectorAll('button')).find((b) => b.textContent === '3');
        page3.click();
        expect(seen).toEqual([3]);
    });
});

describe('格式化', () => {
    it('formatDate 空值显示破折号', () => {
        expect(formatDate(null)).toBe('—');
        expect(formatDate('2026-10-31T08:30:00')).toBe('2026-10-31');
        expect(formatDate('2026-10-31T08:30:00', true)).toBe('2026-10-31 08:30');
    });

    it('formatMoney 输出人民币两位小数', () => {
        expect(formatMoney(1234.5)).toMatch(/1,234\.50/);
        expect(formatMoney(null)).toBe('—');
    });
});

describe('toastError() 错误提示', () => {
    it('ApiError 提示其 message', () => {
        toastError(new ApiError(422, '库存不足'));
        expect(toastText()).toBe('库存不足');
    });

    it('普通异常/空错误给兜底文案「操作失败」', () => {
        toastError(new Error('boom'));
        expect(toastText()).toBe('boom');
        toastError(undefined);
        expect(toastText()).toBe('操作失败');
    });
});
