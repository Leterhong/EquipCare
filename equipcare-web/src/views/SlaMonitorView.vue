<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';

/** SLA 监控（Vue 迁移收尾）：活跃工单 SLA 状态全景 + 百分比进度条。 */
interface SlaOrder {
  workOrderId: number;
  orderNo: string;
  title: string;
  equipmentName?: string;
  level?: string;
  levelLabel?: string;
  stage?: string;
  stageLabel?: string;
  slaStatus?: string;
  dueTime?: string;
  elapsedMinutes?: number;
  totalMinutes?: number;
  percent?: number;
}

const rows = ref<SlaOrder[]>([]);
const loading = ref(true);

const slaTag = (s?: string) => (s === 'OVERDUE' ? 'danger' : s === 'WARN' ? 'warning' : 'success');
const slaText = (s?: string) => (s === 'OVERDUE' ? '已超时' : s === 'WARN' ? '预警' : '正常');

const barColor = (p?: number) => (p == null ? '#94a3b8' : p >= 100 ? '#dc2626' : p >= 80 ? '#d97706' : '#16845b');

function fmt(minutes?: number) {
  if (minutes == null) return '—';
  if (minutes >= 60) return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
  return `${minutes}m`;
}

onMounted(async () => {
  try {
    rows.value = await http.get<never, SlaOrder[]>('/api/sla/active-orders');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <el-card shadow="never">
    <template #header><b>SLA 活跃工单监控</b><span style="margin-left:10px;color:#94a3b8;font-size:12px">共 {{ rows.length }} 张进行中</span></template>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="工单" min-width="180">
        <template #default="{ row }">
          <span class="mono">{{ row.orderNo }}</span>
          <div style="font-size:12px;color:#94a3b8">{{ row.title }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="equipmentName" label="设备" min-width="130" show-overflow-tooltip />
      <el-table-column label="等级" width="70" align="center">
        <template #default="{ row }">{{ row.levelLabel || row.level || '—' }}</template>
      </el-table-column>
      <el-table-column prop="stageLabel" label="阶段" width="90" />
      <el-table-column label="SLA 状态" width="90">
        <template #default="{ row }">
          <el-tag :type="slaTag(row.slaStatus)" size="small">{{ slaText(row.slaStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="160">
        <template #default="{ row }">
          <el-progress :percentage="row.percent ?? 0" :stroke-width="12"
            :color="barColor(row.percent)" :format="() => `${row.percent ?? 0}%`" />
          <div style="font-size:11px;color:#94a3b8;margin-top:2px">
            已用 {{ fmt(row.elapsedMinutes) }} / 总 {{ fmt(row.totalMinutes) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" width="150">
        <template #default="{ row }">{{ (row.dueTime || '').replace('T', ' ').slice(0, 16) || '—' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
</style>
