<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { http, httpDel } from '../api/http';

/** 设备分类管理（Vue 迁移收尾）：树形展示 + 新建/编辑/删除。 */
interface CategoryNode {
  id: number;
  parentId: number;
  categoryCode: string;
  categoryName: string;
  maintainCycleDays?: number;
  sort?: number;
  children?: CategoryNode[];
}

const tree = ref<CategoryNode[]>([]);
const loading = ref(true);

const load = async () => {
  loading.value = true;
  try {
    tree.value = await http.get<never, CategoryNode[]>('/api/asset/categories/tree');
  } finally {
    loading.value = false;
  }
};

function promptAdd(parentId: number | null) {
  ElMessageBox.prompt('分类名称', '新建分类', { inputPattern: /^.{2,64}$/, inputErrorMessage: '2~64 字' })
    .then(async ({ value }) => {
      await http.post('/api/asset/categories', {
        parentId, categoryName: value.trim(), categoryCode: 'CAT-' + Date.now() % 100000,
      });
      ElMessage.success('已创建');
      load();
    }).catch(() => {});
}

function promptEdit(node: CategoryNode) {
  ElMessageBox.prompt('分类名称', '编辑分类', { inputValue: node.categoryName })
    .then(async ({ value }) => {
      await http.put(`/api/asset/categories/${node.id}`, {
        parentId: node.parentId, categoryCode: node.categoryCode,
        categoryName: value.trim(), sort: node.sort,
      });
      ElMessage.success('已更新');
      load();
    }).catch(() => {});
}

async function remove(node: CategoryNode) {
  try {
    await ElMessageBox.confirm(`删除分类「${node.categoryName}」？有设备的分类会被拒绝。`, '删除确认', { type: 'warning' });
    await httpDel(`/api/asset/categories/${node.id}`);
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
      <span>设备分类树（分类用于模板关联与设备归类）</span>
      <el-button type="primary" size="small" @click="promptAdd(null)">+ 根分类</el-button>
    </div>
    <el-tree :data="tree" :props="{ label: 'categoryName', children: 'children' }" default-expand-all node-key="id">
      <template #default="{ data }">
        <div class="node-row">
          <span><b>{{ data.categoryName }}</b>
            <span class="hint mono" style="margin-left:6px">{{ data.categoryCode }}</span>
            <span v-if="data.maintainCycleDays" class="hint"> · 保养周期 {{ data.maintainCycleDays }} 天</span>
          </span>
          <span>
            <el-button link size="small" @click.stop="promptAdd(data.id)">+ 子分类</el-button>
            <el-button link size="small" @click.stop="promptEdit(data)">编辑</el-button>
            <el-button link size="small" type="danger" @click.stop="remove(data)">删除</el-button>
          </span>
        </div>
      </template>
    </el-tree>
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-size: 13px; color: #64748b; }
.node-row { display: flex; justify-content: space-between; align-items: center; width: 100%; padding-right: 8px; font-size: 13px; }
.hint { color: #94a3b8; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
</style>
