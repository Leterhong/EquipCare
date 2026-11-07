<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { http } from '../api/http';

/**
 * 消息中心（Vue 第五批迁移）：全部/未读切换、单条与批量标记已读、全部已读。
 * 接口与旧前端一致（/api/system/messages*，仅本人消息，无权限码）。
 */
interface MessageRow {
  id: number;
  title: string;
  content?: string;
  msgType?: string;
  bizType?: string;
  bizId?: number;
  readFlag: number;
  createTime?: string;
}

const MSG_TYPE: Record<string, string> = {
  WORK_ORDER: '工单', TASK: '巡检', SLA: 'SLA', SYSTEM: '系统', STOCK: '库存',
};

const rows = ref<MessageRow[]>([]);
const total = ref(0);
const unread = ref(0);
const loading = ref(false);
const tab = ref<'all' | 'unread'>('all');
const query = reactive({ pageNum: 1, pageSize: 10 });
const selected = ref<number[]>([]);

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: MessageRow[]; total: number }>('/api/system/messages', {
      params: {
        pageNum: query.pageNum,
        pageSize: query.pageSize,
        readFlag: tab.value === 'unread' ? 0 : undefined,
      },
    });
    rows.value = page.records || [];
    total.value = page.total || 0;
    unread.value = await http.get<never, number>('/api/system/messages/unread-count');
  } finally {
    loading.value = false;
  }
}

const allRead = computed(() => rows.value.every((r) => r.readFlag === 1));

async function markRead(row: MessageRow) {
  if (row.readFlag === 1) return;
  await http.patch(`/api/system/messages/${row.id}/read`);
  row.readFlag = 1;
  unread.value = Math.max(0, unread.value - 1);
}

async function markSelectedRead() {
  const ids = selected.value.filter((id) => {
    const row = rows.value.find((r) => r.id === id);
    return row && row.readFlag === 0;
  });
  if (!ids.length) {
    ElMessage.info('所选均为已读');
    return;
  }
  await http.patch('/api/system/messages/read-batch', ids);
  ElMessage.success(`已标记 ${ids.length} 条`);
  load();
}

async function markAllRead() {
  await ElMessageBox.confirm('将全部消息标记为已读？', '提示', { type: 'warning' });
  await http.patch('/api/system/messages/read-all');
  ElMessage.success('已全部标记已读');
  load();
}

function switchTab(t: 'all' | 'unread') {
  tab.value = t;
  query.pageNum = 1;
  load();
}

function rowClassName({ row }: { row: MessageRow }) {
  return row.readFlag === 0 ? 'unread-row' : '';
}

function onNewMessage() {
  if (tab.value === 'unread') {
    query.pageNum = 1;
  }
  load();
}

onMounted(() => {
  load();
  window.addEventListener('ec:vue-message-new', onNewMessage);
});
onUnmounted(() => window.removeEventListener('ec:vue-message-new', onNewMessage));
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-radio-group :model-value="tab" @update:model-value="switchTab($event as 'all' | 'unread')">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="unread">未读（{{ unread }}）</el-radio-button>
      </el-radio-group>
      <div class="spacer" />
      <el-button :disabled="allRead" @click="markSelectedRead">标记所选已读</el-button>
      <el-button type="primary" plain :disabled="unread === 0" @click="markAllRead">全部已读</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      :row-class-name="rowClassName"
      @selection-change="(s: MessageRow[]) => (selected = s.map((r) => r.id))"
    >
      <el-table-column type="selection" width="42" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ MSG_TYPE[row.msgType || ''] || row.msgType || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">
          <b v-if="row.readFlag === 0">{{ row.title }}</b>
          <span v-else style="color: #64748b">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="right">
        <template #default="{ row }">
          <el-button v-if="row.readFlag === 0" link type="primary" @click="markRead(row as MessageRow)">标已读</el-button>
          <span v-else style="color: #c0c8d4">已读</span>
        </template>
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
.spacer { flex: 1; }
.pager { margin-top: 14px; justify-content: flex-end; }
:deep(.unread-row) { background: #f0f9f4 !important; }
</style>
