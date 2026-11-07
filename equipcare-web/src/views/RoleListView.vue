<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { http } from '../api/http';

/**
 * 角色管理（Vue 第八批，只读列表）。权限分配矩阵等管理操作在旧版前端。
 * 权限：system:role:view（RoleController /api/system/roles）。
 */
interface RoleRow {
  id: number;
  roleCode: string;
  roleName: string;
  dataScope?: string;
  status: number;
  remark?: string;
}

const DATA_SCOPE: Record<string, string> = {
  ALL: '全部数据', DEPT: '本部门', SELF: '仅本人',
};

const rows = ref<RoleRow[]>([]);
const loading = ref(false);

onMounted(async () => {
  loading.value = true;
  try {
    rows.value = await http.get<never, RoleRow[]>('/api/system/roles');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <span>共 {{ rows.length }} 个角色</span>
      <span class="hint">权限矩阵分配请使用旧版管理页</span>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="roleCode" label="角色编码" width="150">
        <template #default="{ row }"><span class="mono">{{ row.roleCode }}</span></template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色名称" width="130" />
      <el-table-column label="数据范围" width="110">
        <template #default="{ row }">{{ DATA_SCOPE[row.dataScope] || row.dataScope || '—' }}</template>
      </el-table-column>
      <el-table-column label="内置" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" size="small" type="success">启用</el-tag>
          <el-tag v-else size="small" type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="职责说明" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; align-items: center; }
.hint { color: #94a3b8; font-size: 12px; margin-left: auto; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
</style>
