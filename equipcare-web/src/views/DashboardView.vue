<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { http } from '../api/http';

/**
 * 数据驾驶舱（Vue 版首批迁移）：指标卡 + 设备/工单状态分布。
 * 后端接口与旧前端完全一致（/api/analytics/dashboard/summary + /charts）。
 */
interface Summary {
  equipmentTotal: number;
  equipmentNormal: number;
  equipmentFault: number;
  pendingWorkOrders: number;
  inProgressWorkOrders?: number;
  todayInspections?: number;
}
interface Charts {
  equipmentStatusDist: { name: string; value: number }[];
  workOrderStatusDist: { name: string; value: number }[];
}
const summary = ref<Summary | null>(null);
const charts = ref<Charts | null>(null);
const loading = ref(true);

const EQ_LABEL: Record<string, string> = {
  IN_USE: '在用', IDLE: '停用/闲置', REPAIRING: '维修中', SCRAPPED: '已报废',
};
const WO_LABEL: Record<string, string> = {
  PENDING_ASSIGN: '待派单', PENDING_ACCEPT: '待接单', IN_PROGRESS: '维修中',
  PENDING_REVIEW: '待验收', COMPLETED: '已完成', REJECTED: '已驳回', CANCELLED: '已取消',
};
const label = (code: string, map: Record<string, string>) => map[code] || code;
const percent = (v: number, total: number) => (total ? Math.round((v / total) * 100) : 0);

onMounted(async () => {
  try {
    const [s, c] = await Promise.all([
      http.get<never, Summary>('/api/analytics/dashboard/summary'),
      http.get<never, Charts>('/api/analytics/dashboard/charts'),
    ]);
    summary.value = s;
    charts.value = c;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-loading="loading">
    <el-row :gutter="14" class="cards">
      <el-col :span="6">
        <el-card shadow="hover"><div class="kpi"><span>设备总数</span><b>{{ summary?.equipmentTotal ?? '—' }}</b></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="kpi"><span>正常运行的设备</span><b style="color: #16845b">{{ summary?.equipmentNormal ?? '—' }}</b></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="kpi"><span>故障设备</span><b style="color: #d97706">{{ summary?.equipmentFault ?? '—' }}</b></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="kpi"><span>待处理工单</span><b style="color: #dc2626">{{ summary?.pendingWorkOrders ?? '—' }}</b></div></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>设备状态分布</template>
          <div v-if="charts">
            <div v-for="item in charts.equipmentStatusDist" :key="item.name" class="dist-row">
              <span class="dist-name">{{ label(item.name, EQ_LABEL) }}</span>
              <el-progress
                :percentage="percent(item.value, summary?.equipmentTotal || 1)"
                :stroke-width="14"
                :format="() => String(item.value)"
              />
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>工单状态分布</template>
          <div v-if="charts">
            <div v-for="item in charts.workOrderStatusDist" :key="item.name" class="dist-row">
              <span class="dist-name">{{ label(item.name, WO_LABEL) }}</span>
              <el-progress
                :percentage="percent(item.value, charts.workOrderStatusDist.reduce((a, b) => a + b.value, 0) || 1)"
                :stroke-width="14"
                :format="() => String(item.value)"
              />
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.cards { margin-bottom: 14px; }
.kpi { text-align: center; }
.kpi span { display: block; font-size: 13px; color: #64748b; }
.kpi b { font-size: 30px; font-weight: 800; color: #263445; }
.dist-row { margin-bottom: 14px; }
.dist-name { display: inline-block; width: 90px; font-size: 13px; color: #475569; }
.dist-row .el-progress { width: calc(100% - 100px); display: inline-flex; vertical-align: middle; }
</style>
