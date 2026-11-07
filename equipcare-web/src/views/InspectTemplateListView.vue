<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { http, httpDel } from '../api/http';

/**
 * 巡检模板列表（Vue 迁移收尾）：分页 + 发布/停用/编辑/删除。
 * 编辑入口跳站内模板编辑页；发布后才能被巡检计划引用。
 */
interface TemplateRow {
  id: number;
  templateCode: string;
  templateName: string;
  categoryName?: string;
  version: number;
  status: string;
  itemCount: number;
  description?: string;
  publishTime?: string;
  planCount?: number;
}

const STATUS: Record<string, { label: string; tag: 'info' | 'success' | 'warning' }> = {
  DRAFT: { label: '草稿', tag: 'info' },
  PUBLISHED: { label: '已发布', tag: 'success' },
  DISABLED: { label: '已停用', tag: 'warning' },
};

const router = useRouter();
const rows = ref<TemplateRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: TemplateRow[]; total: number }>(
      '/api/inspection/templates',
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

async function toggleStatus(row: TemplateRow) {
  const action = row.status === 'PUBLISHED' ? 'disable' : 'publish';
  const label = action === 'publish' ? '发布' : '停用';
  try {
    await ElMessageBox.confirm(
      `确认${label}模板「${row.templateName}」？${action === 'publish' ? '发布后才能被巡检计划引用。' : '停用后计划不再生成任务。'}`,
      '提示',
      { type: 'warning' },
    );
    await http.post(`/api/inspection/templates/${row.id}/${action}`);
    ElMessage.success(`已${label}`);
    load();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '操作失败');
  }
}

async function remove(row: TemplateRow) {
  try {
    await ElMessageBox.confirm(
      `删除模板「${row.templateName}」？被计划引用时将拒绝删除。`,
      '删除确认',
      { type: 'warning' },
    );
    await httpDel(`/api/inspection/templates/${row.id}`);
    ElMessage.success('已删除');
    load();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '删除失败');
  }
}

onMounted(load);
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="模板编码 / 名称" clearable style="width: 220px"
        @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="search">
        <el-option v-for="(meta, code) in STATUS" :key="code" :label="meta.label" :value="code" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="router.push('/inspect-template-new')">+ 新建模板</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="templateCode" label="编码" width="150">
        <template #default="{ row }"><span class="mono">{{ row.templateCode }}</span></template>
      </el-table-column>
      <el-table-column prop="templateName" label="模板名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" width="110" />
      <el-table-column label="检查项" width="80" align="center">
        <template #default="{ row }">{{ row.itemCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="版本" width="70" align="center">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.tag || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="被计划引用" width="95" align="center">
        <template #default="{ row }">{{ row.planCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/inspect-template-edit/${row.id}`)">编辑</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" link type="success" @click="toggleStatus(row as TemplateRow)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="toggleStatus(row as TemplateRow)">停用</el-button>
          <el-button link type="danger" @click="remove(row as TemplateRow)">删除</el-button>
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
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
