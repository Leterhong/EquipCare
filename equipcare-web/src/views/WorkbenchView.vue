<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';

/**
 * 维修工作台（Vue 迁移最后一页，28/28）：
 * 一次请求聚合「我该处理什么」——六个状态分栏各取前 N 条，看全量走列表页。
 * 接口：GET /api/workorder/workbench（WorkOrderWorkbenchVO）。
 */
interface WorkOrderRow {
  id: number;
  orderNo?: string;
  title?: string;
  status?: string;
  level?: string;
  equipmentName?: string;
  assigneeName?: string;
  remainingMinutes?: number;
  remainingLabel?: string;
}

interface StatusCount {
  status: string;
  total: number;
}

const STATUS_LABEL: Record<string, string> = {
  PENDING_ASSIGN: '待派单', PENDING_ACCEPT: '待接单', IN_PROGRESS: '维修中',
  PENDING_REVIEW: '待验收', COMPLETED: '已完成', REJECTED: '已驳回', CANCELLED: '已取消',
};

const LEVEL_COLOR: Record<string, string> = {
  P1: '#dc2626', P2: '#d97706', P3: '#2563eb', P4: '#94a3b8',
};

const auth = useAuthStore();
const router = useRouter();
const loading = ref(true);
const counts = ref<StatusCount[]>([]);
const sections = ref<{ key: string; label: string; path: string; rows: WorkOrderRow[] }[]>([]);

const countOf = (status: string) =>
  counts.value.find((c) => c.status === status)?.total ?? 0;

const statCards = computed(() => [
  { label: '待派单', value: countOf('PENDING_ASSIGN'), path: '/workorder' },
  { label: '待接单', value: countOf('PENDING_ACCEPT'), path: '/workorder' },
  { label: '维修中', value: countOf('IN_PROGRESS'), path: '/workorder' },
  { label: '待验收', value: countOf('PENDING_REVIEW'), path: '/workorder' },
]);

function rowCard(section: { key: string; label: string; path: string; rows: WorkOrderRow[] }) {
  return { key: section.key, label: section.label, rows: section.rows, path: section.path };
}

function openOrder(row: WorkOrderRow) {
  router.push(`/workorder/${row.id}`);
}

onMounted(async () => {
  try {
    const wb = await http.get<never, {
      counts: StatusCount[];
      pendingAssign: WorkOrderRow[];
      rejected: WorkOrderRow[];
      pendingAccept: WorkOrderRow[];
      repairing: WorkOrderRow[];
      pendingReview: WorkOrderRow[];
      completed: WorkOrderRow[];
    }>('/api/workorder/workbench', { params: { limit: 8 } });
    counts.value = wb.counts || [];
    sections.value = [
      rowCard({ key: 'pendingAssign', label: '待派单', path: '/workorder', rows: wb.pendingAssign || [] }),
      rowCard({ key: 'pendingAccept', label: '待我接单', path: '/workorder', rows: wb.pendingAccept || [] }),
      rowCard({ key: 'repairing', label: '维修中', path: '/workorder', rows: wb.repairing || [] }),
      rowCard({ key: 'pendingReview', label: '待验收', path: '/workorder', rows: wb.pendingReview || [] }),
      rowCard({ key: 'rejected', label: '已驳回', path: '/workorder', rows: wb.rejected || [] }),
      rowCard({ key: 'completed', label: '最近完成', path: '/workorder', rows: wb.completed || [] }),
    ];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-loading="loading">
    <!-- 指标卡 -->
    <el-row :gutter="14" class="cards">
      <el-col v-for="card in statCards" :key="card.label" :span="6">
        <el-card shadow="hover" class="stat" @click="router.push(card.path)">
          <span class="stat-label">{{ card.label }}</span>
          <b class="stat-value" :style="{ color: card.value > 0 ? '#d97706' : '#16845b' }">{{ card.value }}</b>
        </el-card>
      </el-col>
    </el-row>

    <!-- 六态分栏 -->
    <el-row :gutter="12">
      <el-col v-for="sec in sections" :key="sec.key" :span="8" style="margin-bottom: 14px">
        <el-card shadow="never" class="lane">
          <template #header>
            <div class="lane-head">
              <b>{{ sec.label }}</b>
              <span class="lane-count">{{ sec.rows.length }}</span>
            </div>
          </template>
          <el-empty v-if="!sec.rows.length" description="暂无工单" :image-size="40" />
          <div v-for="row in sec.rows" :key="row.id" class="wo-card" @click="openOrder(row)">
            <div class="wo-title">{{ row.title || '—' }}</div>
            <div class="wo-sub">
              <span class="mono">{{ row.orderNo }}</span>
              <span v-if="row.level" :style="{ color: LEVEL_COLOR[row.level] }">{{ row.level }}</span>
            </div>
            <div class="wo-sub">
              {{ row.equipmentName || '' }}
              <span v-if="row.assigneeName">· {{ row.assigneeName }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat { text-align: center; cursor: pointer; margin-bottom: 14px; }
.stat-label { display: block; font-size: 13px; color: #64748b; }
.stat-value { font-size: 26px; font-weight: 800; }
.lane { min-height: 200px; }
.lane-head { display: flex; justify-content: space-between; align-items: center; }
.lane-count { color: #94a3b8; font-size: 12px; }
.wo-card { padding: 8px 10px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; margin-bottom: 8px; cursor: pointer; transition: border-color 0.15s; }
.wo-card:hover { border-color: var(--el-color-primary); }
.wo-title { font-size: 13.5px; font-weight: 600; color: #263445; }
.wo-sub { font-size: 12px; color: #94a3b8; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
</style>
