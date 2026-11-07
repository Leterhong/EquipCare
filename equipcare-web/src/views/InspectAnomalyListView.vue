<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 巡检异常列表（Vue 第四批迁移）：状态筛选 / 分页。
 * 转工单、关闭等操作仍在旧版前端，逐步迁移。
 */
interface AnomalyRow {
  id: number;
  anomalyNo: string;
  itemName?: string;
  equipmentName?: string;
  taskCode?: string;
  level?: string;
  status: string;
  description?: string;
  reportTime?: string;
}

const STATUS: Record<string, { label: string; tag: 'warning' | 'primary' | 'success' }> = {
  OPEN: { label: '待处理', tag: 'warning' },
  WORK_ORDER: { label: '已转工单', tag: 'primary' },
  CLOSED: { label: '已关闭', tag: 'success' },
};
const LEVEL: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' };

const rows = ref<AnomalyRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: AnomalyRow[]; total: number }>(
      '/api/inspection/anomalies',
      { params: { ...query, keyword: query.keyword || undefined, status: query.status || undefined } },
    );
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
      <el-input
        v-model="query.keyword"
        placeholder="异常单号 / 设备 / 检查项"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
        <el-option v-for="(meta, code) in STATUS" :key="code" :label="meta.label" :value="code" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="anomalyNo" label="异常单号" width="150">
        <template #default="{ row }"><span class="mono">{{ row.anomalyNo }}</span></template>
      </el-table-column>
      <el-table-column prop="itemName" label="检查项" min-width="140" show-overflow-tooltip />
      <el-table-column prop="equipmentName" label="设备" min-width="150" show-overflow-tooltip />
      <el-table-column label="等级" width="80" align="center">
        <template #default="{ row }">{{ LEVEL[row.level] || row.level || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.tag || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '—' }}</template>
      </el-table-column>
      <el-table-column label="上报时间" width="150">
        <template #default="{ row }">{{ (row.reportTime || '').replace('T', ' ').slice(0, 16) || '—' }}</template>
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
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
