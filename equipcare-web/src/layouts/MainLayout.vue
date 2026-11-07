<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore, type MenuNode } from '../stores/auth';
import { http } from '../api/http';

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

/** 已迁移到 Vue 的路由前缀（与 router/index.ts 保持一致） */
const MIGRATED = ['/dashboard', '/equipment', '/workorder', '/inspect-task', '/spare-part'];

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

onMounted(async () => {
  if (!menus.value.length) {
    try {
      menus.value = await http.get<never, MenuNode[]>('/api/auth/menus');
    } catch {
      menus.value = [];
    }
  }
});

function logout() {
  http.post('/api/auth/logout', {}).catch(() => {});
  auth.clear();
  router.push('/login');
}
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
.main { background: #f5f7fa; }
</style>
