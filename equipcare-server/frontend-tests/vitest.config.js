import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'node:url';

// 直接以生产静态资源目录为被测对象（../src/main/resources/static/js），
// 不复制源码，保证测试的是真实交付代码。
export default defineConfig({
    resolve: {
        alias: {
            '@static': fileURLToPath(new URL('../equipcare-admin/src/main/resources/static/js', import.meta.url)),
        },
    },
    test: {
        environment: 'jsdom',
        include: ['tests/**/*.test.js'],
        // jsdom 无 canvas：ECharts 相关页面模块不做渲染级测试
        env: { EQUIPCARE_TEST: '1' },
    },
});
