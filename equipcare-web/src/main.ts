import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { ElLoading } from 'element-plus';
import App from './App.vue';
import { router } from './router';
// Element Plus 组件按需引入（vite 插件自动注入组件与样式）；
// 函数式组件（消息/确认框/通知）与 v-loading 指令需要显式补样式/注册：
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';
import 'element-plus/es/components/notification/style/css';
import 'element-plus/es/components/loading/style/css';

createApp(App).use(createPinia()).use(router).directive('loading', ElLoading.directive).mount('#app');
