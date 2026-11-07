<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 设备台账（Vue 第二批迁移）：搜索 / 状态筛选 / 分页 / 健康分着色。
 * 详情、编辑、二维码等操作页仍在旧版前端（顶部「完整功能」入口），逐步迁移。
 */
interface EquipmentRow {
  id: number;
  assetNo: string;
  equipmentName: string;
  categoryName?: string;
  locationName?: string;
  status: string;
  healthScore?: number;
  lastInspectTime?: string;
}

const STATUS: Record<string, { label: string; tag: 'success' | 'warning' | 'danger' | 'info' }> = {
  IN_USE: { label: '在用', tag: 'success' },
  IDLE: { label: '停用/闲置', tag: 'info' },
  REPAIRING: { label: '维修中', tag: 'warning' },
  SCRAPPED: { label: '已报废', tag: 'danger' },
};

const rows = ref<EquipmentRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: EquipmentRow[]; total: number }>(
      '/api/asset/equipment',
      { params: { ...query, keyword: query.keyword || undefined, status: query.status || undefined } },
    );
    rows.value = page.records || [];
    total.value = page.total || 0;
  } finally {
    loading.value = false;
  }
}

function healthColor(v?: number) {
  if (v == null) return '#94a3b8';
  return v < 60 ? '#dc2626' : v < 75 ? '#d97706' : '#16845b';
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
        placeholder="资产编号 / 名称 / 序列号"
        clearable
        style="width: 240px"
        @keyup.enter="search"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <el-option v-for="(meta, code) in STATUS" :key="code" :label="meta.label" :value="code" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="assetNo" label="资产编号" width="150">
        <template #default="{ row }"><span class="mono">{{ row.assetNo }}</span></template>
      </el-table-column>
      <el-table-column prop="equipmentName" label="设备名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" width="110" />
      <el-table-column prop="locationName" label="位置" min-width="130" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.tag || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="健康分" width="90" align="center">
        <template #default="{ row }">
          <b :style="{ color: healthColor(row.healthScore) }">{{ row.healthScore ?? '—' }}</b>
        </template>
      </el-table-column>
      <el-table-column label="最近巡检" width="160">
        <template #default="{ row }">{{ (row.lastInspectTime || '').replace('T', ' ').slice(0, 16) || '—' }}</template>
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
