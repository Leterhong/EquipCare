import { expect, test } from '@playwright/test';

const USERNAME = process.env.E2E_USERNAME || 'e2eadmin';
const PASSWORD = process.env.E2E_PASSWORD || 'E2eAdmin_2026!';

/**
 * 巡检执行 e2e（第九批新增页面覆盖）：
 * API 建模板（NUMBER+TEXT 两项）→ 手动建任务 → UI 执行（数值自动判定/正常项/提交）
 * → 断言提交成功回到任务列表。
 */
test.describe('巡检执行', () => {
  let taskId: number;

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/login');
    await page.getByPlaceholder('用户名').fill(USERNAME);
    await page.getByPlaceholder('密码').fill(PASSWORD);
    await page.getByRole('button', { name: /登 录/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15000 });

    taskId = await page.evaluate(async () => {
      const token = localStorage.getItem('ec_access_token');
      const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
      // 设备分类（模板需要 categoryId）
      const eq = await fetch('/api/asset/equipment/276', { headers }).then((r) => r.json());
      const categoryId = eq.data.categoryId as number;
      // 建模板：1 个 NUMBER 项（0~100，正常范围）+ 1 个 TEXT 项
      const tpl = await fetch('/api/inspection/templates', {
        method: 'POST', headers,
        body: JSON.stringify({
          templateCode: 'E2E-TPL-' + Date.now(),
          templateName: 'e2e 执行验证模板',
          categoryId,
          description: 'e2e inspect execute verification',
          items: [
            {
              itemName: 'e2e 电压检测', itemType: 'NUMBER', unit: 'V',
              minValue: 0, maxValue: 100, required: 1, autoWorkOrder: 0,
            },
            {
              itemName: 'e2e 外观检查', itemType: 'TEXT', normalValue: '无异常',
              required: 1, autoWorkOrder: 0,
            },
          ],
        }),
      }).then((r) => r.json());
      if (tpl.code !== 200) throw new Error('建模板失败: ' + tpl.message);
      const templateId = tpl.data as number;
      // 手动建任务
      const task = await fetch('/api/inspection/tasks', {
        method: 'POST', headers,
        body: JSON.stringify({ equipmentId: 276, templateId, remark: 'e2e execute verification' }),
      }).then((r) => r.json());
      if (task.code !== 200) throw new Error('建任务失败: ' + task.message);
      return task.data as number;
    });
    expect(taskId).toBeGreaterThan(0);
  });

  test('执行巡检：数值自动判定 + 全项判定 + 提交', async ({ page }) => {
    await page.goto(`/#/inspect-task/${taskId}/execute`);
    // 设备身份核对区可见
    await expect(page.getByText('设备二维码')).toBeVisible({ timeout: 10000 });
    // 判定进度：未判定 2
    await expect(page.getByText(/未判定 2/)).toBeVisible();

    // 第 1 项：NUMBER 填实测值 50 → 自动判定为正常
    const numberInput = page.getByPlaceholder(/填写实测数值/);
    await numberInput.fill('50');
    await expect(page.getByText(/已判定 1/)).toBeVisible();

    // 第 2 项：TEXT 项选正常
    const radioGroups = page.getByRole('radiogroup');
    await radioGroups.nth(1).getByText('✓ 正常').click();
    await expect(page.getByText(/已判定 2/)).toBeVisible();

    // 总备注 + 提交
    await page.locator('textarea[maxlength="500"]').fill('e2e 巡检执行验证');
    await page.getByRole('button', { name: /提交巡检结果/ }).click();
    // 提交成功 → 回到任务列表
    await expect(page).toHaveURL(/#\/inspect-task/, { timeout: 10000 });
  });

  test('数值超标准自动标异常且未填说明阻止提交', async ({ page }) => {
    await page.goto(`/#/inspect-task/${taskId}/execute`);
    await expect(page.getByText(/未判定 2/)).toBeVisible({ timeout: 10000 });
    // 超范围值 → 自动判定异常
    await page.getByPlaceholder(/填写实测数值/).fill('250');
    await expect(page.getByText(/判定：/).first()).toBeVisible();
    await expect(page.getByText(/已判定 1/)).toBeVisible();
    // 不填异常说明直接提交 → 被校验拦下（仍在执行页）
    await page.locator('textarea[maxlength="500"]').fill('e2e');
    await page.getByRole('button', { name: /提交巡检结果/ }).click();
    await expect(page.getByText(/判定异常，请填写异常说明/)).toBeVisible({ timeout: 5000 });
  });
});
