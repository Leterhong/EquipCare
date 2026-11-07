<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 备件台账（Vue 第三批迁移）：搜索 / 库存着色 / 分页。
 * 出入库、工单领用等操作页仍在旧版前端，逐步迁移。
 */
interface PartRow {
  id: number;
  partNo: string;
  partName: string;
  spec?: string;
  unit: string;
  unitPrice?: number;
  currentStock?: number;
  safetyStock?: number;
}

const rows = ref<PartRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: PartRow[]; total: number }>('/api/inventory/parts', {
      params: { ...query, keyword: query.keyword || undefined },
    });
    rows.value = page.records || [];
    total.value = page.total || 0;
  } finally {
    loading.value = false;
  }
}

function stockColor(row: PartRow) {
  if (row.currentStock == null || row.safetyStock == null) return '#94a3b8';
  if (row.currentStock <= row.safetyStock) return '#dc2626'; // 低于安全库存
  if (row.currentStock <= row.safetyStock * 1.5) return '#d97706';
  return '#16845b';
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
        placeholder="备件编码 / 名称"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="partNo" label="编码" width="150">
        <template #default="{ row }"><span class="mono">{{ row.partNo }}</span></template>
      </el-table-column>
      <el-table-column prop="partName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格型号" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.spec || '—' }}</template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column label="单价" width="100" align="right">
        <template #default="{ row }">
          {{ row.unitPrice != null ? '¥' + Number(row.unitPrice).toFixed(2) : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="当前库存" width="110" align="center">
        <template #default="{ row }">
          <b :style="{ color: stockColor(row) }">
            {{ row.currentStock ?? '—' }}{{ row.unit ? ' ' + row.unit : '' }}
          </b>
        </template>
      </el-table-column>
      <el-table-column label="安全库存" width="90" align="center">
        <template #default="{ row }">{{ row.safetyStock ?? '—' }}</template>
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
