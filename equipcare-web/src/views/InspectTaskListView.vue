<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { http } from '../api/http';

/**
 * 巡检任务列表（Vue 第三批迁移）：状态筛选 / 只看我的 / 逾期标记。
 * 执行、详情、异常转单等操作页仍在旧版前端，逐步迁移。
 */
interface TaskRow {
  id: number;
  taskCode: string;
  equipmentName?: string;
  assetNo?: string;
  planName?: string;
  planDate?: string;
  finishedItemCount?: number;
  totalItemCount?: number;
  assigneeName?: string;
  status: string;
  overdueFlag?: number;
  slaDueTime?: string;
}

const STATUS: Record<string, { label: string; tag: 'info' | 'primary' | 'success' | 'danger' | 'warning' }> = {
  PENDING: { label: '待执行', tag: 'info' },
  IN_PROGRESS: { label: '执行中', tag: 'primary' },
  SUBMITTED: { label: '已提交', tag: 'success' },
  EXPIRED: { label: '已过期', tag: 'danger' },
  COMPLETED: { label: '已完成', tag: 'success' },
  OVERDUE: { label: '已逾期', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'info' },
};

const rows = ref<TaskRow[]>([]);
const total = ref(0);
const loading = ref(false);
const mineOnly = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: TaskRow[]; total: number }>('/api/inspection/tasks', {
      params: {
        ...query,
        keyword: query.keyword || undefined,
        status: query.status || undefined,
        // 与旧前端约定一致：assigneeId=0 由后端翻译为当前登录人
        assigneeId: mineOnly.value ? 0 : undefined,
      },
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
        placeholder="任务号 / 设备 / 计划"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
        <el-option v-for="(meta, code) in STATUS" :key="code" :label="meta.label" :value="code" />
      </el-select>
      <el-checkbox v-model="mineOnly" @change="search">只看我的</el-checkbox>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="taskCode" label="任务号" width="140">
        <template #default="{ row }"><span class="mono">{{ row.taskCode }}</span></template>
      </el-table-column>
      <el-table-column label="设备" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.equipmentName }}
          <span v-if="row.assetNo" class="mono sub">{{ row.assetNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="planName" label="来自计划" width="150" show-overflow-tooltip />
      <el-table-column prop="planDate" label="计划日" width="100" />
      <el-table-column label="进度" width="90" align="center">
        <template #default="{ row }">{{ row.finishedItemCount ?? 0 }}/{{ row.totalItemCount ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="assigneeName" label="执行人" width="95">
        <template #default="{ row }">{{ row.assigneeName || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.tag || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
          <el-tag v-if="row.overdueFlag === 1 && row.status !== 'EXPIRED'" type="danger" size="small" style="margin-left:4px">
            超时
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时限" width="150">
        <template #default="{ row }">{{ (row.slaDueTime || '').replace('T', ' ').slice(0, 16) || '—' }}</template>
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
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
.sub { display: block; color: #94a3b8; font-size: 11.5px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
