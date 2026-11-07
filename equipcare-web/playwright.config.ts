import { defineConfig } from '@playwright/test';

/**
 * Vue 端 e2e（关键路径冒烟）：登录 → 驾驶舱 → 设备台账 → 消息中心。
 * dev server 由 CI 步骤先起（vite 5173，/api 代理 8080 后端）。
 */
export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30000,
  retries: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173',
    screenshot: 'only-on-failure',
  },
  reporter: [['list'], ['html', { open: 'never' }]],
});
