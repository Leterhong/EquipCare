<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { http, httpDel } from '../api/http';

/** 设备位置管理（Vue 迁移收尾）：树形展示 + 新建/编辑/删除。 */
interface LocationNode {
  id: number;
  parentId: number;
  locationCode: string;
  locationName: string;
  locationType?: string;
  sort?: number;
  fullPath?: string;
  children?: LocationNode[];
}

const tree = ref<LocationNode[]>([]);
const loading = ref(true);

const load = async () => {
  loading.value = true;
  try {
    tree.value = await http.get<never, LocationNode[]>('/api/asset/locations/tree');
  } finally {
    loading.value = false;
  }
};

function promptAdd(parentId: number | null) {
  ElMessageBox.prompt('位置名称', '新建位置', { inputPattern: /^.{2,64}$/, inputErrorMessage: '2~64 字' })
    .then(async ({ value }) => {
      await http.post('/api/asset/locations', {
        parentId, locationName: value.trim(), locationCode: 'LOC-' + Date.now() % 100000,
      });
      ElMessage.success('已创建');
      load();
    }).catch(() => {});
}

function promptEdit(node: LocationNode) {
  ElMessageBox.prompt('位置名称', '编辑位置', { inputValue: node.locationName })
    .then(async ({ value }) => {
      await http.put(`/api/asset/locations/${node.id}`, {
        parentId: node.parentId, locationCode: node.locationCode,
        locationName: value.trim(), sort: node.sort,
      });
      ElMessage.success('已更新');
      load();
    }).catch(() => {});
}

async function remove(node: LocationNode) {
  try {
    await ElMessageBox.confirm(`删除位置「${node.locationName}」？有设备的分类会被拒绝。`, '删除确认', { type: 'warning' });
    await httpDel(`/api/asset/locations/${node.id}`);
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
      <span>设备位置树（位置用于设备归类与巡检路线）</span>
      <el-button type="primary" size="small" @click="promptAdd(null)">+ 根位置</el-button>
    </div>
    <el-tree :data="tree" :props="{ label: 'locationName', children: 'children' }" default-expand-all node-key="id">
      <template #default="{ data }">
        <div class="node-row">
          <span><b>{{ data.locationName }}</b>
            <span class="hint mono" style="margin-left:6px">{{ data.locationCode }}</span>
          </span>
          <span>
            <el-button link size="small" @click.stop="promptAdd(data.id)">+ 子位置</el-button>
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
