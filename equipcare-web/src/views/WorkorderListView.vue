<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 维修工单列表（Vue 版首批迁移）：分页 + 关键字 + 状态筛选。
 * 与旧前端同源接口 /api/workorder，字段完全一致。
 */
interface WorkOrderRow {
  id: number;
  orderNo: string;
  title: string;
  status: string;
  level: string;
  equipmentName?: string;
  assigneeName?: string;
  reportTime?: string;
  slaStatus?: string;
}

const STATUS: Record<string, { label: string; tag: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  PENDING_ASSIGN: { label: '待派单', tag: 'warning' },
  PENDING_ACCEPT: { label: '待接单', tag: 'warning' },
  IN_PROGRESS: { label: '维修中', tag: 'primary' },
  PENDING_REVIEW: { label: '待验收', tag: 'primary' },
  COMPLETED: { label: '已完成', tag: 'success' },
  REJECTED: { label: '已驳回', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'info' },
};
const LEVEL: Record<string, string> = { P1: 'P1 紧急', P2: 'P2 高', P3: 'P3 中', P4: 'P4 低' };

const rows = ref<WorkOrderRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: WorkOrderRow[]; total: number }>(
      '/api/workorder',
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
        placeholder="工单号 / 标题 / 设备"
        clearable
        style="width: 240px"
        @keyup.enter="search"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <el-option
          v-for="(meta, code) in STATUS"
          :key="code"
          :label="meta.label"
          :value="code"
        />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="工单号" width="160">
        <template #default="{ row }">
          <span class="mono">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.tag || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="等级" width="90">
        <template #default="{ row }">{{ LEVEL[row.level] || row.level }}</template>
      </el-table-column>
      <el-table-column prop="equipmentName" label="设备" min-width="140" show-overflow-tooltip />
      <el-table-column prop="assigneeName" label="维修人" width="100">
        <template #default="{ row }">{{ row.assigneeName || '未派单' }}</template>
      </el-table-column>
      <el-table-column label="报修时间" width="160">
        <template #default="{ row }">{{ (row.reportTime || '').replace('T', ' ').slice(0, 16) }}</template>
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
