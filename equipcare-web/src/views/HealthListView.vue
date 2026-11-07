<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { http } from '../api/http';

/**
 * 设备健康列表（Vue 第四批迁移）：阈值 0 全量拉取，按健康分升序展示。
 * 与旧版 /health 页数据源一致（/api/analytics/health/at-risk?threshold=0）。
 */
interface HealthRow {
  equipmentId: number;
  equipmentName?: string;
  assetNo?: string;
  healthScore?: number;
  level: string;
  openWoCount?: number;
  idleDays?: number;
  workOrderCount?: number;
  anomalyCount?: number;
}

const LEVEL: Record<string, { label: string; tag: 'success' | 'primary' | 'warning' | 'danger' }> = {
  HEALTHY: { label: '健康', tag: 'success' },
  SUB_HEALTHY: { label: '亚健康', tag: 'primary' },
  WARNING: { label: '预警', tag: 'warning' },
  DANGER: { label: '危险', tag: 'danger' },
};

const rows = ref<HealthRow[]>([]);
const loading = ref(false);

const scoreColor = (v?: number) =>
  v == null ? '#94a3b8' : v < 60 ? '#dc2626' : v < 75 ? '#d97706' : '#16845b';

onMounted(async () => {
  loading.value = true;
  try {
    rows.value = await http.get<never, HealthRow[]>('/api/analytics/health/at-risk', {
      params: { threshold: 0, limit: 200 },
    });
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <el-card shadow="never">
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="设备" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.equipmentName || `#${row.equipmentId}` }}</template>
      </el-table-column>
      <el-table-column label="资产编号" width="150">
        <template #default="{ row }"><span class="mono">{{ row.assetNo || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="健康分" width="90" align="center">
        <template #default="{ row }">
          <b :style="{ color: scoreColor(row.healthScore) }">
            {{ row.healthScore == null ? '—' : Number(row.healthScore).toFixed(0) }}
          </b>
        </template>
      </el-table-column>
      <el-table-column label="等级" width="90">
        <template #default="{ row }">
          <el-tag :type="LEVEL[row.level]?.tag || 'info'">{{ LEVEL[row.level]?.label || row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="在途工单" width="90" align="center">
        <template #default="{ row }">{{ row.openWoCount ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="距上次维修" width="100" align="center">
        <template #default="{ row }">{{ row.idleDays == null ? '无记录' : row.idleDays + ' 天' }}</template>
      </el-table-column>
      <el-table-column label="工单数(30天)" width="110" align="center">
        <template #default="{ row }">{{ row.workOrderCount ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="异常数(30天)" width="110" align="center">
        <template #default="{ row }">{{ row.anomalyCount ?? '—' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
</style>
