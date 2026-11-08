<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
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

interface PermNode {
  id: number;
  parentId: number;
  name: string;
  children?: PermNode[];
}

const permVisible = ref(false);
const permSaving = ref(false);
const permRole = ref<RoleRow | null>(null);
const permRoleName = computed(() => permRole.value?.roleName || '');
const permTreeData = ref<PermNode[]>([]);
const permChecked = ref<number[]>([]);
const permTree = ref();

/** 全量权限平铺 → 组树（parentId，根为 0） */
function buildTree(list: { id: number; parentId: number; name: string }[]): PermNode[] {
  const byParent = new Map<number, { id: number; parentId: number; name: string }[]>();
  for (const n of list) {
    const key = n.parentId || 0;
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key)!.push(n);
  }
  const walk = (parent: number): PermNode[] =>
    (byParent.get(parent) || []).map((n) => ({
      ...n,
      children: byParent.has(n.id) ? walk(n.id) : undefined,
    }));
  return walk(0);
}

async function openPermMatrix(row: RoleRow) {
  permRole.value = row;
  try {
    const [detail, all] = await Promise.all([
      http.get<never, RoleRow & { permissionIds?: number[] }>(`/api/system/roles/${row.id}`),
      http.get<never, { id: number; parentId: number; name: string }[]>('/api/system/permissions'),
    ]);
    permTreeData.value = buildTree(all);
    permChecked.value = detail.permissionIds || [];
    permVisible.value = true;
    await nextTick();
    permTree.value?.setCheckedKeys(permChecked.value);
  } catch (e) {
    ElMessage.error((e as Error).message || '权限加载失败');
  }
}

async function savePerms() {
  if (!permRole.value) return;
  // el-tree 半选节点需手动并入（父节点半选时其 id 也属授权范围）
  const checked = permTree.value?.getCheckedKeys() as number[];
  const half = permTree.value?.getHalfCheckedKeys() as number[];
  const ids = Array.from(new Set([...checked, ...half]));
  permSaving.value = true;
  try {
    await http.put(`/api/system/roles/${permRole.value.id}/permissions`, { permissionIds: ids });
    ElMessage.success(`已保存 ${ids.length} 项权限分配`);
    permVisible.value = false;
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败');
  } finally {
    permSaving.value = false;
  }
}

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
    <div class="toolbar">
      <span>共 {{ rows.length }} 个角色</span>
      <span class="hint">权限矩阵：双击角色行勾选权限（保存为覆盖式分配，内置 ADMIN 禁改）</span>
    </div>
    <el-table v-loading="loading" :data="rows" stripe @row-dblclick="openPermMatrix">
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

    <!-- 权限矩阵弹窗 -->
    <el-dialog v-model="permVisible" :title="`权限矩阵 · ${permRoleName}`" width="560px">
      <el-tree
        ref="permTree"
        :data="permTreeData"
        show-checkbox
        node-key="id"
        :default-checked-keys="permChecked"
        :props="{ label: 'name', children: 'children' }"
        style="max-height: 420px; overflow-y: auto"
      />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerms">保存分配</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; align-items: center; }
.hint { color: #94a3b8; font-size: 12px; margin-left: auto; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
</style>
