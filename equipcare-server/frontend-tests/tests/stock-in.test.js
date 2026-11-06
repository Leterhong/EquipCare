/**
 * 备件入库页（spare-stock-in.js）DOM 级测试：库存操作与表单验证。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render } from '@static/pages/spare-stock-in.js';
import { ok, routeFetch, loginAs, logoutAll, toastText, flush } from './helpers.js';

const PARTS = { records: [
    { id: 11, partNo: 'P-BRG-6204', partName: '深沟球轴承 6204', unit: '个', price: 12.5 },
    { id: 12, partNo: 'P-SEAL-35', partName: '骨架油封 35×52', unit: '个', price: 3.8 },
] };

function route(postHandler) {
    return [
        ['GET', '/api/inventory/parts', ok(PARTS)],
        ['GET', '/api/inventory/stock/txns/page', ok({ records: [], total: 0 })],
        ['POST', '/api/inventory/stock/in', postHandler || ((url, init) => {
            stockIn.posted = JSON.parse(init.body);
            return ok({ txnNo: 'TXN-20261031-0001', quantity: 5, afterStock: 15, amount: 62.5 });
        })],
    ];
}
const stockIn = { posted: null };

async function setup(postHandler) {
    stockIn.posted = null;
    const fetchMock = vi.fn(routeFetch(route(postHandler)));
    vi.stubGlobal('fetch', fetchMock);
    loginAs(['spare:stock:in']);
    const container = document.createElement('div');
    document.body.append(container);
    await render(container, { params: {}, setTitle: () => {}, navigate: () => {} });
    await flush(6);
    return { container, fetchMock };
}

function submit(container) {
    const btn = Array.from(container.querySelectorAll('button')).find((b) => b.textContent === '确认入库');
    btn.click();
}

beforeEach(() => {
    logoutAll();
    // 不清空 document.body：ui.js 的 toastStack 模块级缓存会被游离（见 helpers 说明）
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('备件入库（表单验证与库存操作）', () => {
    it('页面渲染出备件下拉与入库表单', async () => {
        const { container } = await setup();
        const select = container.querySelector('select');
        expect(select).not.toBeNull();
        expect(container.querySelectorAll('select option').length).toBeGreaterThan(2); // 请选择 + 2 备件
        expect(container.textContent).toContain('入库数量');
    });

    it('未选择备件时提交被拦截，不发请求', async () => {
        const { container, fetchMock } = await setup();
        submit(container);
        await flush();
        expect(toastText()).toContain('请选择备件');
        const posts = fetchMock.mock.calls.filter(([u, i]) => (i.method || '') === 'POST');
        expect(posts.length).toBe(0);
    });

    it('入库数量 <= 0 被拦截', async () => {
        const { container } = await setup();
        container.querySelector('select').value = '11';
        const qty = container.querySelector('[name=quantity]');
        qty.value = '0';
        submit(container);
        await flush();
        expect(toastText()).toContain('请输入正确的入库数量');
    });

    it('合法入库发出 POST，载荷含备件与数量；成功后提示流水号', async () => {
        const { container, fetchMock } = await setup();
        container.querySelector('select').value = '11';
        const qty = container.querySelector('[name=quantity]');
        qty.value = '5';
        const price = container.querySelector('[name=unitPrice]');
        price.value = '12.5';
        submit(container);
        await flush(8);
        const posts = fetchMock.mock.calls.filter(([u, i]) => (i.method || '') === 'POST'
            && String(u).includes('/api/inventory/stock/in'));
        expect(posts.length).toBe(1);
        expect(stockIn.posted).toEqual({ partId: 11, quantity: 5, unitPrice: 12.5 });
        expect(toastText()).toContain('入库成功');
        expect(toastText()).toContain('TXN-20261031-0001');
    });

    it('没有入库权限时显示权限空态，不加载备件', async () => {
        logoutAll();
        loginAs(['spare:stock:view']); // 缺 spare:stock:in
        const fetchMock = vi.fn(routeFetch(route()));
        vi.stubGlobal('fetch', fetchMock);
        const container = document.createElement('div');
        document.body.append(container);
        await render(container, { params: {}, setTitle: () => {}, navigate: () => {} });
        await flush(6);
        expect(container.textContent).toContain('没有 spare:stock:in 权限');
        expect(container.querySelector('form')).toBeNull();
    });
});
