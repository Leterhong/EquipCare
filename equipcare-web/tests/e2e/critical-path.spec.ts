import { expect, test } from '@playwright/test';

const USERNAME = process.env.E2E_USERNAME || 'e2eadmin';
const PASSWORD = process.env.E2E_PASSWORD || 'E2eAdmin_2026!';

test.describe('关键路径冒烟', () => {
  test('登录 → 数据驾驶舱', async ({ page }) => {
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(USERNAME);
    await page.getByPlaceholder('密码').fill(PASSWORD);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });
    await expect(page.getByText('数据驾驶舱')).toBeVisible();
    await expect(page.getByText('设备总数')).toBeVisible();
  });

  test('已登录直达设备台账并看到表格', async ({ page }) => {
    // 先登录建立会话
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(USERNAME);
    await page.getByPlaceholder('密码').fill(PASSWORD);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });
    // 台账
    await page.goto('/#/equipment');
    await expect(page.getByText('设备台账').first()).toBeVisible();
    await expect(page.getByRole('button', { name: '查询' })).toBeVisible();
  });

  test('消息中心可用且可切未读', async ({ page }) => {
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(USERNAME);
    await page.getByPlaceholder('密码').fill(PASSWORD);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });
    await page.goto('/#/messages');
    await expect(page.getByRole('button', { name: /全部已读/ })).toBeVisible();
    await page.getByRole('radio', { name: /未读/ }).click();
    await expect(page.getByRole('button', { name: /全部已读/ })).toBeVisible();
  });
});
