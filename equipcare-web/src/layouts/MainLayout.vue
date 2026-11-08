<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore, type MenuNode } from '../stores/auth';
import { http } from '../api/http';
import { resetRealtime, startRealtime, stopRealtime, useRealtime } from '../composables/useRealtime';
import { ElNotification } from 'element-plus';
import { messageRoute, msgTypeLabel } from '../composables/messageRoute';

/**
 * 布局壳：服务端菜单（/api/auth/menus）按树渲染 el-menu；
 * 仅映射已迁移到 Vue 的路由，其余菜单项（旧页面）整组折叠展示
 * "旧版入口"，跳回原生前端，保证功能不缺。
 */
const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const menus = ref<MenuNode[]>([]);
const collapsed = ref(false);
const realtime = useRealtime();

/** 已迁移到 Vue 的路由前缀（与 router/index.ts 保持一致） */
const MIGRATED = ['/dashboard', '/equipment', '/workorder', '/inspect-task', '/inspect-anomaly', '/inspect-plan', '/inspect-template', '/inspect-template-new', '/inspect-template-edit', '/spare-part', '/spare-stock', '/health', '/messages', '/users', '/roles', '/audit-logs', '/workorder-new', '/tenants', '/workbench', '/categories', '/equipment/location', '/sla/monitor', '/profile'];

const visibleMenus = computed(() => {
  const walk = (nodes: MenuNode[]): MenuNode[] =>
    nodes
      .map((n) => {
        const children = n.children ? walk(n.children) : [];
        const selfMigrated = !!n.path && MIGRATED.some((p) => n.path!.startsWith(p));
        if (!selfMigrated && children.length === 0) return null;
        return { ...n, children };
      })
      .filter(Boolean) as MenuNode[];
  return walk(menus.value);
});

const legacyLink = computed(() => {
  // 旧原生前端入口：dev 环境即后端 8080；生产按部署路径调整
  return `${location.protocol}//${location.hostname}:8080/app.html`;
});

/* ---------------- 全局搜索（聚合设备/工单/备件，对齐旧版） ---------------- */
const searchKeyword = ref('');
const searchVisible = ref(false);
const searchBusy = ref(false);
const searchResults = ref<{ group: string; items: { label: string; sub: string; path: string }[] }[]>([]);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

interface SearchItem { label: string; sub: string; path: string }
interface SearchGroup { group: string; items: SearchItem[] }

function onSearchInput() {
  searchVisible.value = searchKeyword.value.trim().length > 0;
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    if (searchKeyword.value.trim()) void runSearch();
  }, 350);
}

function onSearchEnter() {
  if (searchTimer) clearTimeout(searchTimer);
  if (searchKeyword.value.trim()) void runSearch();
}

async function runSearch() {
  const text = searchKeyword.value.trim();
  if (!text) return;
  searchBusy.value = true;
  const groups: SearchGroup[] = [];
  const tasks: Promise<unknown>[] = [];
  if (auth.can('asset:equipment:view')) {
    tasks.push(http.get<never, { records: { id: number; equipmentName?: string; assetNo?: string }[] }>(
      '/api/asset/equipment', { params: { pageNum: 1, pageSize: 5, keyword: text } },
    ).then((page) => groups.push({
      group: '设备',
      items: (page.records || []).map((r) => ({
        label: r.equipmentName || '未命名设备', sub: r.assetNo || '', path: `/equipment/${r.id}`,
      })),
    })));
  }
  if (auth.can('wo:workorder:view')) {
    tasks.push(http.get<never, { records: { id: number; title?: string; orderNo?: string }[] }>(
      '/api/workorder', { params: { pageNum: 1, pageSize: 5, keyword: text } },
    ).then((page) => groups.push({
      group: '维修工单',
      items: (page.records || []).map((r) => ({
        label: r.title || `工单 ${r.orderNo || r.id}`, sub: r.orderNo || '', path: `/workorder/${r.id}`,
      })),
    })));
  }
  if (auth.can('spare:part:view')) {
    tasks.push(http.get<never, { records: { partName?: string; partNo?: string }[] }>(
      '/api/inventory/parts', { params: { pageNum: 1, pageSize: 5, keyword: text } },
    ).then((page) => groups.push({
      group: '备件',
      items: (page.records || []).map((r) => ({
        label: r.partName || '未命名备件', sub: r.partNo || '', path: '/spare-part',
      })),
    })));
  }
  const settled = await Promise.allSettled(tasks);
  for (const s of settled) {
    if (s.status === 'rejected') {
      // 单域失败（无权限/网络）不阻塞其他域
    }
  }
  searchResults.value = groups.filter((g) => g.items.length);
  searchBusy.value = false;
}

function goSearchHit(path: string) {
  router.push(path);
}

/* ---------------- 铃铛：最近未读 + 桌面通知 + 标签角标 ---------------- */
const bellVisible = ref(false);
const recentUnread = ref<{ id: number; title?: string; msgType?: string; createTime?: string }[]>([]);

async function loadRecentUnread() {
  try {
    const page = await http.get('/api/system/messages', {
      params: { pageNum: 1, pageSize: 5, readFlag: 0 },
    }) as { records?: { id: number; title?: string; msgType?: string; createTime?: string }[] };
    recentUnread.value = page.records || [];
  } catch {
    recentUnread.value = [];
  }
}

function bellClick() {
  bellVisible.value = !bellVisible.value;
  if (bellVisible.value) void loadRecentUnread();
}

function openBellMessage(m: { id: number; bizType?: string; bizId?: number }) {
  http.patch('/api/system/messages/' + m.id + '/read').catch(() => {});
  realtime.state.unread = Math.max(0, realtime.state.unread - 1);
  const path = messageRoute(m.bizType, m.bizId);
  if (path) router.push(path);
  else void loadRecentUnread();
}

watch(() => realtime.state.unread, (n) => {
  document.title = n > 0 ? '(' + n + ') 巡维云 EquipCare' : '巡维云 EquipCare';
});

function logout() {
  http.post('/api/auth/logout', {}).catch(() => {});
  stopRealtime();
  resetRealtime();
  auth.clear();
  router.push('/login');
}

/** 新消息到达：铃铛计数即时刷新（SSE message:new 事件） */
function onRealtime(e: CustomEvent<{ type: string; data?: Record<string, unknown> }>) {
  if (e.detail?.type === 'message:new') {
    void realtime.refreshUnread();
    const d = (e.detail.data || {}) as { title?: string };
    ElNotification({
      title: '新消息',
      message: d.title || '您有一条新消息',
      type: 'info',
      duration: 4500,
      onClick: () => router.push('/messages'),
    });
    window.dispatchEvent(new CustomEvent('ec:vue-message-new'));
  }
}

onMounted(() => {
  void http.get<never, MenuNode[]>('/api/auth/menus').then((tree) => {
    menus.value = tree || [];
  }).catch(() => {
    menus.value = [];
  });
  startRealtime();
  window.addEventListener('ec:vue-sse', onRealtime as EventListener);
});
</script>

<template>
  <el-container class="shell">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="brand" @click="router.push('/dashboard')">
        <span class="mark">EC</span>
        <b v-if="!collapsed">巡维云 <em>EquipCare</em></b>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        router
        background-color="#0d1628"
        text-color="#9fb0c8"
        active-text-color="#4ade9f"
      >
        <template v-for="node in visibleMenus" :key="node.id">
          <el-sub-menu v-if="node.children && node.children.length" :index="String(node.id)">
            <template #title>
              <span>{{ node.name }}</span>
            </template>
            <el-menu-item
              v-for="child in node.children"
              :key="child.id"
              :index="child.path || String(child.id)"
            >
              {{ child.name }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="node.path || String(node.id)">
            {{ node.name }}
          </el-menu-item>
        </template>
      </el-menu>
      <div class="legacy">
        <a :href="legacyLink" target="_blank" rel="noopener">↗ 完整功能（旧版）</a>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <el-button text @click="collapsed = !collapsed">{{ collapsed ? '☰' : '⟨' }}</el-button>
        <b class="title">{{ route.meta.title || '巡维云' }}</b>
        <div class="spacer" />
        <el-popover :visible="searchVisible" placement="bottom-end" :width="380">
          <template #reference>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索设备 / 工单 / 备件"
              style="width: 220px"
              clearable
              @input="onSearchInput"
              @focus="onSearchInput"
              @keyup.enter="onSearchEnter"
            />
          </template>
          <div v-if="searchBusy" style="text-align:center;color:#94a3b8">搜索中…</div>
          <div v-else-if="!searchResults.length" style="text-align:center;color:#94a3b8;padding:8px">无匹配结果</div>
          <div v-else>
            <div v-for="group in searchResults" :key="group.group" style="margin-bottom:8px">
              <div style="font-size:12px;color:#94a3b8;margin-bottom:4px">{{ group.group }}</div>
              <div
                v-for="item in group.items"
                :key="item.path + item.label"
                class="search-hit"
                @click="goSearchHit(item.path); searchVisible = false"
              >
                {{ item.label }} <span class="hit-sub">{{ item.sub }}</span>
              </div>
            </div>
          </div>
        </el-popover>
        <el-popover placement="bottom-end" :width="340">
          <template #reference>
            <el-badge :value="realtime.state.unread" :hidden="!realtime.state.unread" :max="99">
              <el-button text @click="bellClick">🔔</el-button>
            </el-badge>
          </template>
          <div style="max-height: 320px; overflow-y: auto">
            <div v-if="!recentUnread.length" style="text-align:center;color:#94a3b8;padding:12px">暂无未读消息</div>
            <div v-for="m in recentUnread" :key="m.id" class="bell-item" @click="openBellMessage(m)">
              <div style="font-weight:600">{{ m.title }}</div>
              <div class="bell-sub">{{ msgTypeLabel(m.msgType) }} · {{ (m.createTime || '').replace('T', ' ').slice(0, 16) }}</div>
            </div>
          </div>
          <div style="text-align:center; padding: 8px 0 0; border-top: 1px solid var(--el-border-color-lighter)">
            <el-button link type="primary" @click="router.push('/messages')">查看全部消息</el-button>
          </div>
        </el-popover>
        <el-dropdown>
          <span class="user">
            <el-avatar :size="30" style="background: #16845b">{{ auth.realName.slice(0, 1) }}</el-avatar>
            {{ auth.realName }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.shell { min-height: 100vh; }
.aside {
  background: #0d1628;
  transition: width 0.25s;
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 14px;
  cursor: pointer;
  color: #e6edf7;
}
.brand em { font-style: normal; color: #4ade9f; }
.mark {
  width: 30px; height: 30px; border-radius: 8px;
  display: grid; place-items: center;
  font-size: 11px; font-weight: 800; color: #fff;
  background: linear-gradient(140deg, #1d9c6b, #10653f);
  flex: none;
}
.aside .el-menu { border-right: none; flex: 1; }
.legacy { padding: 12px 14px; font-size: 12.5px; border-top: 1px solid #1e3050; }
.legacy a { color: #6b7f9c; }
.legacy a:hover { color: #4ade9f; }
.topbar {
  display: flex; align-items: center; gap: 14px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fff;
}
.title { font-size: 15.5px; color: #263445; }
.spacer { flex: 1; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #263445; }
.search-hit { padding: 6px 8px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.search-hit:hover { background: #f5f7fa; }
.hit-sub { color: #94a3b8; font-size: 12px; margin-left: 6px; font-family: ui-monospace, Menlo, Consolas, monospace; }
.title-row-right { display: flex; align-items: center; gap: 10px; }
.main { background: #f5f7fa; }
</style>
