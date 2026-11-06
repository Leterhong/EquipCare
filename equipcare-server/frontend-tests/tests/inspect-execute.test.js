/**
 * 巡检执行页（inspect-execute.js）DOM 级测试：巡检结果提交校验。
 * 以真实页面模块 + jsdom 渲染，验证前端先挡一次的填报规则（服务端仍独立校验）：
 *  1. 每项必须判定；2. 必检项不能跳过；3. 跳过需填原因；4. 异常需填说明；
 *  5. 数值超出区间不能判正常；6. 合法提交发出正确载荷。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render } from '@static/pages/inspect-execute.js';
import { ok, fail, routeFetch, loginAs, logoutAll, toastText, flush } from './helpers.js';

const TASK_ID = 55;

function taskDetail(items, extra = {}) {
    return {
        id: TASK_ID,
        taskCode: 'T20261031-055',
        status: 'IN_PROGRESS',
        equipmentName: '空压机 A-01',
        planDate: '2026-10-31',
        remark: '',
        items,
        ...extra,
    };
}

function clickByText(root, selector, text) {
    const btn = Array.from(root.querySelectorAll(selector)).find((b) => b.textContent === text);
    if (!btn) throw new Error(`找不到按钮：${text}`);
    return btn;
}

async function setup(items, postHandler) {
    const routes = [
        ['GET', `/api/inspection/tasks/${TASK_ID}`, ok(taskDetail(items))],
        ['POST', `/api/inspection/tasks/${TASK_ID}/start`, ok(true)],
        ['POST', `/api/inspection/tasks/${TASK_ID}/submit`, postHandler || ok(0)],
    ];
    const fetchMock = vi.fn(routeFetch(routes));
    vi.stubGlobal('fetch', fetchMock);
    loginAs(['inspect:task:execute']);

    const container = document.createElement('div');
    document.body.append(container);
    const ctx = {
        params: { id: String(TASK_ID) },
        setTitle: () => {},
        navigate: () => {},
    };
    await render(container, ctx);
    await flush();
    return { container, fetchMock };
}

beforeEach(() => {
    logoutAll();
    // 注意：不清空 document.body —— ui.js 的 toastStack 是模块级缓存，
    // 清空 body 会导致后续 toast 挂到游离节点上而读不到。
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('巡检结果提交（前端校验规则）', () => {
    it('必检项的「跳过」按钮被禁用', async () => {
        const { container } = await setup([
            { templateItemId: 1, itemName: '油位', itemType: 'TEXT', required: 1 },
        ]);
        const skipBtn = clickByText(container, '.exec-btn', '跳过');
        expect(skipBtn.disabled).toBe(true);
        expect(skipBtn.title).toContain('必检项');
    });

    it('未判定就提交被拦截，不发提交请求', async () => {
        const { container, fetchMock } = await setup([
            { templateItemId: 1, itemName: '油位', itemType: 'TEXT', required: 0 },
        ]);
        clickByText(container, '.exec-submit button', '提交巡检结果').click();
        await flush();
        expect(toastText()).toContain('还没有判定');
        const submitCalls = fetchMock.mock.calls.filter(([u, i]) => (i.method || 'GET') === 'POST'
            && String(u).includes('/submit'));
        expect(submitCalls.length).toBe(0);
    });

    it('跳过未填原因被拦截', async () => {
        const { container } = await setup([
            { templateItemId: 2, itemName: '皮带张力', itemType: 'TEXT', required: 0 },
        ]);
        clickByText(container, '.exec-btn', '跳过').click();
        await flush();
        clickByText(container, '.exec-submit button', '提交巡检结果').click();
        await flush();
        expect(toastText()).toContain('跳过时需要填写原因');
    });

    it('判异常未填说明被拦截', async () => {
        const { container } = await setup([
            { templateItemId: 3, itemName: '轴承温度', itemType: 'TEXT', required: 0 },
        ]);
        clickByText(container, '.exec-btn', '异常').click();
        await flush();
        clickByText(container, '.exec-submit button', '提交巡检结果').click();
        await flush();
        expect(toastText()).toContain('必须填写异常说明');
    });

    it('数值超出标准区间不允许判为正常', async () => {
        const { container } = await setup([
            { templateItemId: 4, itemName: '排气压力', itemType: 'NUMBER', required: 0, minValue: 0.6, maxValue: 0.8, unit: 'MPa' },
        ]);
        // 填一个超上限的实测值
        const input = container.querySelector('.exec-value input[type=number]');
        input.value = '1.2';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        clickByText(container, '.exec-btn', '正常').click();
        await flush();
        clickByText(container, '.exec-submit button', '提交巡检结果').click();
        await flush();
        expect(toastText()).toContain('超出标准区间');
    });

    it('合法填报发出提交请求，载荷含逐项结果与跳过原因', async () => {
        let posted = null;
        const { container } = await setup(
            [
                { templateItemId: 5, itemName: '油位', itemType: 'TEXT', required: 0 },
                { templateItemId: 6, itemName: '区域封闭', itemType: 'TEXT', required: 0 },
            ],
            (url, init) => {
                posted = JSON.parse(init.body);
                return ok(0);
            },
        );
        // 第 1 项判正常
        clickByText(container, '.exec-btn', '正常').click();
        // 第 2 项跳过并填原因
        const skipBtns = Array.from(container.querySelectorAll('.exec-btn')).filter((b) => b.textContent === '跳过');
        skipBtns[skipBtns.length - 1].click();
        await flush();
        const reason = container.querySelectorAll('.exec-extra input')[0];
        reason.value = '区域动火作业封闭';
        reason.dispatchEvent(new Event('input', { bubbles: true }));

        clickByText(container, '.exec-submit button', '提交巡检结果').click();
        await flush();
        expect(posted).not.toBeNull();
        expect(posted.items).toHaveLength(2);
        expect(posted.items[0]).toMatchObject({ templateItemId: 5, result: 'NORMAL', skipReason: null });
        expect(posted.items[1]).toMatchObject({ templateItemId: 6, result: 'SKIPPED', skipReason: '区域动火作业封闭' });
        expect(toastText()).toContain('提交成功');
    });

    it('提交接口返回异常项数时提示「已生成异常单」', async () => {
        const { container } = await setup(
            [{ templateItemId: 7, itemName: '油位', itemType: 'TEXT', required: 0 }],
            () => ok(1),
        );
        clickByText(container, '.exec-btn', '异常').click();
        await flush();
        const remark = container.querySelector('.exec-extra textarea');
        remark.value = '油位低于下限，已补油';
        remark.dispatchEvent(new Event('input', { bubbles: true }));
        clickByText(container, '.exec-submit button', '提交巡检结果').click();
        await flush();
        expect(toastText()).toContain('1 项异常已生成异常单');
    });

    it('没有执行权限时显示拒绝文案，不渲染填报表单', async () => {
        logoutAll();
        loginAs(['inspect:task:view']); // 缺 inspect:task:execute
        const fetchMock = vi.fn(routeFetch([['GET', `/api/inspection/tasks/${TASK_ID}`, ok(taskDetail([]))]]));
        vi.stubGlobal('fetch', fetchMock);
        const container = document.createElement('div');
        document.body.append(container);
        await render(container, { params: { id: String(TASK_ID) }, setTitle: () => {}, navigate: () => {} });
        await flush();
        expect(container.textContent).toContain('没有「执行巡检」权限');
        expect(container.querySelector('.exec-submit')).toBeNull();
    });

    it('任务详情接口失败时显示失败卡片而非白屏', async () => {
        logoutAll();
        loginAs(['inspect:task:execute']);
        vi.stubGlobal('fetch', vi.fn(routeFetch([['GET', `/api/inspection/tasks/${TASK_ID}`, fail(500, '服务器开小差')]])));
        const container = document.createElement('div');
        document.body.append(container);
        await render(container, { params: { id: String(TASK_ID) }, setTitle: () => {}, navigate: () => {} });
        await flush();
        expect(container.textContent).toContain('无法填报');
        expect(container.textContent).toContain('服务器开小差');
    });
});
