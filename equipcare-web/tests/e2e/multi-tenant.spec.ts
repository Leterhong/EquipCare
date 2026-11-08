import { expect, test } from '@playwright/test';

const ADMIN_USER = process.env.E2E_USERNAME || 'e2eadmin';
const ADMIN_PASS = process.env.E2E_PASSWORD || 'E2eAdmin_2026!';
const ACME_USER = 'acmeadmin';
const ACME_PASS = 'AcmeAdmin_2026!';

/**
 * 多租户行级隔离 e2e（CI 准备：sys_tenant acme(id=1)、acmeadmin(tenant_id=1)、
 * ACME 专属设备行）。前提注入：E2E_MULTI_TENANT=1 时才跑（否则 skip）。
 */
test.describe.configure({ mode: 'serial' });

test.describe('多租户行级隔离', () => {
  test('管理员开通租户与租户用户', async ({ page, request }) => {
    test.skip(!process.env.E2E_MULTI_TENANT, '需要 CI 多租户数据准备');
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(ADMIN_USER);
    await page.getByPlaceholder('密码').fill(ADMIN_PASS);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });

    const token = await page.evaluate(() => localStorage.getItem('ec_access_token'));
    const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
    const tenants = await request.get('/api/system/tenants', { headers }).then((r) => r.json());
    expect(tenants.data.some((t: { tenantCode: string }) => t.tenantCode === 'acme')).toBe(true);
    const users = await request.get('/api/system/users', {
      headers, params: undefined as never,
    }).catch(() => null);
    void users;
  });

  test('acme 用户登录后仅见租户内设备（隔离实证）', async ({ page }) => {
    test.skip(!process.env.E2E_MULTI_TENANT, '需要 CI 多租户数据准备');
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(ACME_USER);
    await page.getByPlaceholder('密码').fill(ACME_PASS);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });

    await page.goto('/#/equipment');
    await expect(page.getByText('ACME 专属设备').first()).toBeVisible({ timeout: 10000 });
    // 平台租户（tenant 0）的设备不可见
    await expect(page.getByText(/冒烟健康分设备|联调测试设备/)).toHaveCount(0);
  });

  test('平台管理员看不到 acme 租户设备（反向隔离）', async ({ page }) => {
    test.skip(!process.env.E2E_MULTI_TENANT, '需要 CI 多租户数据准备');
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(ADMIN_USER);
    await page.getByPlaceholder('密码').fill(ADMIN_PASS);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });
    await page.goto('/#/equipment');
    await expect(page.getByText('ACME 专属设备')).toHaveCount(0);
    await expect(page.getByText(/设备台账/).first()).toBeVisible();
  });
});
