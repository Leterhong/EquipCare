/**
 * 巡维云 EquipCare · Service Worker（PWA 基础外壳缓存）
 *
 * 策略：
 *  - /api/**、/upload/**、/actuator/**、/api/sse/**：完全旁路（业务数据永不缓存，
 *    SSE 长连接必须直连，不能进 SW 的 fetch 处理）；
 *  - 静态资源（css/js/assets/manifest）：Stale-While-Revalidate —— 先回缓存保证
 *    弱网/离线可打开，后台异步更新，下次生效；页面壳（index/app/scan）同策略；
 *  - 版本号变更即整体换缓存（activate 清旧）。
 */
const VERSION = 'ec-shell-v1';
const SHELL = ['/', '/index.html', '/app.html', '/m/scan.html', '/manifest.json', '/assets/favicon.svg'];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL).catch(() => {})));
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(caches.keys()
        .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
        .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);
    if (url.origin !== location.origin) {
        return; // 跨域不管
    }
    const p = url.pathname;
    if (p.startsWith('/api/') || p.startsWith('/upload/') || p.startsWith('/actuator/')) {
        return; // 业务数据与长连接：永不缓存
    }
    if (event.request.method !== 'GET') {
        return;
    }
    // Stale-While-Revalidate
    event.respondWith((async () => {
        const cache = await caches.open(VERSION);
        const cached = await cache.match(event.request);
        const refresh = fetch(event.request).then((resp) => {
            if (resp && resp.status === 200 && resp.type === 'basic') {
                cache.put(event.request, resp.clone());
            }
            return resp;
        }).catch(() => null);
        return cached || (await refresh) || new Response('离线且无缓存', { status: 503 });
    })());
});
