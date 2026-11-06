/**
 * PWA 引导：Service Worker 注册 + 主题预置（防暗色模式闪白）。
 * 非安全上下文（http://局域网IP 开发环境）静默跳过，不影响任何功能。
 */
(function () {
    'use strict';
    try {
        var saved = localStorage.getItem('ec_theme');
        if (saved === 'dark' || saved === 'light') {
            document.documentElement.setAttribute('data-theme', saved);
        } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            document.documentElement.setAttribute('data-theme', 'dark');
        }
    } catch (e) { /* 隐私模式等 */ }
    if ('serviceWorker' in navigator && window.isSecureContext) {
        window.addEventListener('load', function () {
            navigator.serviceWorker.register('/sw.js').catch(function () { /* 静默降级 */ });
        });
    }
})();
