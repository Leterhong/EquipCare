import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

/**
 * 新版前端（Vue 3）构建配置。
 *
 * 新旧共存策略：
 *  - dev：`npm run dev` 起 5173 端口，/api 代理到本机 8080 后端（同源无 CORS 问题）；
 *  - prod：`npm run build` 产物为纯静态 dist/，可由 Nginx 挂在 /web/ 子路径
 *    （base 已设为相对路径）或独立域名；旧原生前端（static/）继续从后端服务，互不影响。
 */
export default defineConfig({
  base: './',
  plugins: [
    vue(),
    // Element Plus 按需引入：组件/指令/样式按使用注入（主包 2.4MB → 预计 1MB 内）
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/upload': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        // vendor 拆分：框架/组件库独立成块，业务更新时不破坏浏览器缓存
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          axios: ['axios'],
        },
      },
    },
  },
});
