<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 巡检计划列表（Vue 第四批迁移）：分页 + 状态展示。
 * 计划的新建/启停/删除等管理操作仍在旧版前端，逐步迁移。
 */
interface PlanRow {
  id: number;
  planName: string;
  templateName?: string;
  scopeLabel?: string;
  cycleLabel?: string;
  executorNames?: string;
  status: string;
  nextGenerateTime?: string;
}

const STATUS: Record<string, { label: string; tag: 'success' | 'info' }> = {
  ENABLED: { label: '启用中', tag: 'success' },
  DISABLED: { label: '已暂停', tag: 'info' },
};

const rows = ref<PlanRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: PlanRow[]; total: number }>('/api/inspection/plans', {
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
      <el-input
        v-model="query.keyword"
        placeholder="计划名称 / 模板"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="planName" label="计划" min-width="180" show-overflow-tooltip />
      <el-table-column prop="templateName" label="模板" width="150" show-overflow-tooltip />
      <el-table-column prop="scopeLabel" label="范围" min-width="170" show-overflow-tooltip />
      <el-table-column prop="cycleLabel" label="周期" width="160" show-overflow-tooltip />
      <el-table-column prop="executorNames" label="执行人" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.executorNames || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.tag || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下次生成" width="110">
        <template #default="{ row }">{{ (row.nextGenerateTime || '').slice(0, 10) || '—' }}</template>
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
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
