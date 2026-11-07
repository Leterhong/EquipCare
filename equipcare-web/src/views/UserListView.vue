<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 用户管理（Vue 第八批，只读列表 + 状态信息）。
 * 新建/编辑/重置密码等管理操作在旧版前端，逐步迁移。
 * 权限：system:user:view。
 */
interface UserRow {
  id: number;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  deptName?: string;
  status: number;
  lastLoginTime?: string;
  roleNames?: string[];
}

const rows = ref<UserRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: UserRow[]; total: number }>('/api/system/users', {
      params: { ...query, keyword: query.keyword || undefined },
    });
    rows.value = page.records || [];
    total.value = page.total || 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  load();
}

onMounted(load);
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="用户名 / 姓名" clearable style="width: 220px"
        @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <span class="hint">新建/编辑/重置密码请使用旧版管理页</span>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="username" label="用户名" width="130">
        <template #default="{ row }"><span class="mono">{{ row.username }}</span></template>
      </el-table-column>
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="r in row.roleNames || []" :key="r" size="small" style="margin-right:4px">{{ r }}</el-tag>
          <span v-if="!(row.roleNames || []).length">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="deptName" label="部门" width="130">
        <template #default="{ row }">{{ row.deptName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130">
        <template #default="{ row }">{{ row.phone || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="150">
        <template #default="{ row }">{{ (row.lastLoginTime || '').replace('T', ' ').slice(0, 16) || '—' }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      class="pager"
      background
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      @current-change="load"
    />
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; align-items: center; }
.hint { color: #94a3b8; font-size: 12px; margin-left: auto; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
