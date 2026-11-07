import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.ts'],
    globals: false,
    testTimeout: 30000,
    // Element Plus 按需样式链（theme-chalk css）需内联进 vite 管道处理，
    // 否则 node 原生加载器报 Unknown file extension ".css"
    server: { deps: { inline: [/element-plus/] } },
  },
});
