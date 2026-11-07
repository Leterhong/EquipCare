<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 审计日志（Vue 第八批，只读）。数据来自 AOP 审计切面（framework），
 * 接口 /api/system/audit-logs，权限 system:audit:view。
 */
interface AuditRow {
  id: number;
  traceId?: string;
  username?: string;
  module?: string;
  action?: string;
  requestMethod?: string;
  requestUri?: string;
  resultStatus?: string;
  errorMsg?: string;
  ip?: string;
  costMs?: number;
  createTime?: string;
}

const rows = ref<AuditRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 15, keyword: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: AuditRow[]; total: number }>('/api/system/audit-logs', {
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

const resultTag = (s?: string) => (s === 'SUCCESS' ? 'success' : s === 'ERROR' ? 'danger' : 'info');

onMounted(load);
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="模块 / 动作 / 用户 / traceId" clearable
        style="width: 260px" @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <span class="hint">审计日志为只插入表，不可修改删除（数据库触发器兜底）</span>
    </div>

    <el-table v-loading="loading" :data="rows" stripe size="small">
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 19) }}</template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="100" />
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column prop="action" label="动作" width="130" show-overflow-tooltip />
      <el-table-column label="请求" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="mono">{{ row.requestMethod }} {{ row.requestUri }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="resultTag(row.resultStatus)" size="small">{{ row.resultStatus || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column label="耗时" width="80" align="right">
        <template #default="{ row }">{{ row.costMs != null ? row.costMs + 'ms' : '—' }}</template>
      </el-table-column>
      <el-table-column label="traceId" width="140">
        <template #default="{ row }">
          <span class="mono" :title="row.traceId">{{ (row.traceId || '').slice(0, 8) }}</span>
        </template>
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
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
