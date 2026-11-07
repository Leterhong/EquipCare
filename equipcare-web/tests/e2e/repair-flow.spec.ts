import { expect, test } from '@playwright/test';

const USERNAME = process.env.E2E_USERNAME || 'e2eadmin';
const PASSWORD = process.env.E2E_PASSWORD || 'E2eAdmin_2026!';

/**
 * 维修流程 e2e（第八批新增页面覆盖）：
 * API 建单并派单给管理员自己 → UI 开始维修 → 完工记录 → 提交验收 → 状态变待验收。
 * 管理员为 ALL 数据范围角色，可执行全流程。
 */
test.describe('维修流程', () => {
  let orderId: number;

  test.beforeEach(async ({ page }) => {
    // UI 登录建立会话
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(USERNAME);
    await page.getByPlaceholder('密码').fill(PASSWORD);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });

    // API 辅助：建单 + 派单给自己（维修流程前置状态）
    orderId = await page.evaluate(async () => {
      const token = localStorage.getItem('ec_access_token');
      const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
      const create = await fetch('/api/workorder', {
        method: 'POST', headers,
        body: JSON.stringify({
          equipmentId: 276, title: 'e2e 维修流程', faultType: 'OTHER',
          faultDesc: 'e2e repair flow verification', level: 'P3',
        }),
      }).then((r) => r.json());
      if (create.code !== 200) throw new Error('建单失败: ' + create.message);
      const id = create.data as number;
      const assignees = await fetch('/api/workorder/assignees', { headers }).then((r) => r.json());
      const me = (assignees.data || []).find((a: { username: string }) => a.username === 'e2eadmin');
      if (!me) throw new Error('assignees 中未找到 e2eadmin');
      const dispatch = await fetch(`/api/workorder/${id}/dispatch`, {
        method: 'POST', headers,
        body: JSON.stringify({ assigneeId: me.id, priority: 'NORMAL' }),
      }).then((r) => r.json());
      if (dispatch.code !== 200) throw new Error('派单失败: ' + dispatch.message);
      return id as number;
    });
    expect(orderId).toBeGreaterThan(0);
  });

  test('接单 → 开始维修 → 完工记录 → 提交验收', async ({ page }) => {
    // 详情页：接单
    await page.goto(`/#/workorder/${orderId}`);
    await expect(page.getByRole('button', { name: '接单' })).toBeVisible({ timeout: 10000 });
    await page.getByRole('button', { name: '接单' }).click();
    await expect(page.getByText('已接单，请到维修过程记录填写诊断')).toBeVisible({ timeout: 10000 });

    // 维修过程记录页：开始维修
    await page.goto(`/#/workorder/${orderId}/repair`);
    await expect(page.getByRole('button', { name: /确认开始维修/ })).toBeVisible({ timeout: 10000 });
    await page.locator('input[maxlength="200"]').first().fill('e2e 维修原因验证');
    await page.locator('textarea').first().fill('e2e 故障诊断验证：主轴异响疑似轴承磨损');
    await page.getByRole('button', { name: /确认开始维修/ }).click();
    await expect(page.getByRole('button', { name: /保存记录/ })).toBeVisible({ timeout: 10000 });

    // 新增完工记录（必须选择完工类型，无需照片在无文件场景下改用 PROCESS+完工双记录）
    await page.locator('.el-select').first().click();
    await page.getByText('完工', { exact: true }).last().click();
    await page.locator('textarea[maxlength="1000"]').fill('e2e 完工说明：已更换轴承并试机正常');
    await page.getByRole('button', { name: /保存记录/ }).click();
    await expect(page.getByText('记录已保存')).toBeVisible({ timeout: 10000 });

    // 提交验收
    await page.locator('textarea[maxlength="500"]').fill('e2e 维修总结：全部完成，试机正常');
    await page.getByRole('button', { name: /提交验收/ }).click();
    await expect(page).toHaveURL(new RegExp(`#/workorder/${orderId}$`), { timeout: 10000 });
    // 详情页状态变为待验收
    await expect(page.getByText('待验收').first()).toBeVisible({ timeout: 10000 });
  });
});
