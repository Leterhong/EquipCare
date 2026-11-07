<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';

/**
 * 租户管理（多租户第三步，仅 ADMIN）。
 * 接口：/api/system/tenants（列表/开通/启停，后端 hasRole('ADMIN') 双重防护）。
 * 单租户模式：仅显示平台预置租户（id=0）。
 */
interface TenantRow {
  id: number;
  tenantCode: string;
  tenantName: string;
  plan: string;
  status: number;
  createdAt?: string;
}

const PLAN: Record<string, string> = {
  STANDARD: '标准版', PRO: '专业版', PRIVATE: '私有化',
};

const auth = useAuthStore();
const rows = ref<TenantRow[]>([]);
const loading = ref(false);
const isAdmin = ref(false);

async function load() {
  loading.value = true;
  try {
    rows.value = await http.get<never, TenantRow[]>('/api/system/tenants');
  } catch (e) {
    // 非 ADMIN：后端 403，页面降级为只读提示
    rows.value = [];
    if ((e as Error).message?.includes('403') || (e as Error).message?.includes('权限')) {
      isAdmin.value = false;
    }
  } finally {
    loading.value = false;
  }
}

async function toggle(row: TenantRow) {
  const action = row.status === 1 ? '停用' : '启用';
  try {
    await ElMessageBox.confirm(
      `${action}租户「${row.tenantName}」？${row.status === 1 ? '停用后该租户全部用户无法登录。' : ''}`,
      '提示',
      { type: 'warning' },
    );
    await http.post(`/api/system/tenants/${row.id}/status`);
    ElMessage.success(`已${action}`);
    load();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '操作失败');
  }
}

async function createTenant() {
  try {
    const { value } = await ElMessageBox.prompt(
      '租户编码（小写字母/数字/连字符，2~32 位，如 acme-corp）',
      '开通租户 · 第 1 步：编码',
      { inputPattern: /^[a-z0-9-]{2,32}$/, inputErrorMessage: '编码格式不正确' },
    );
    const code = value.trim();
    const { value: name } = await ElMessageBox.prompt('租户名称（企业/组织名）', '开通租户 · 第 2 步：名称', {
      inputPattern: /^.{2,64}$/,
      inputErrorMessage: '名称 2~64 字',
    });
    await http.post('/api/system/tenants', { tenantCode: code, tenantName: name.trim(), plan: 'STANDARD' });
    ElMessage.success('租户已开通');
    load();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message || '开通失败');
  }
}

onMounted(async () => {
  isAdmin.value = auth.user?.roles?.includes('ADMIN') || false;
  await load();
});
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <span>租户列表（多租户第三步：管理/启停；配额与注册入口为后续迭代）</span>
      <div class="spacer" />
      <el-button v-if="isAdmin" type="primary" @click="createTenant">+ 开通租户</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="租户 ID" width="90" align="center">
        <template #default="{ row }">{{ row.id }}</template>
      </el-table-column>
      <el-table-column prop="tenantCode" label="编码" width="150">
        <template #default="{ row }"><span class="mono">{{ row.tenantCode }}</span></template>
      </el-table-column>
      <el-table-column prop="tenantName" label="名称" min-width="180" />
      <el-table-column label="套餐" width="100">
        <template #default="{ row }">{{ PLAN[row.plan] || row.plan }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开通时间" width="160">
        <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="right">
        <template #default="{ row }">
          <el-button
            v-if="isAdmin && row.id !== 0"
            link
            :type="row.status === 1 ? 'danger' : 'success'"
            @click="toggle(row as TenantRow)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <span v-else-if="row.id === 0" style="color:#c0c8d4;font-size:12px">预置</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; align-items: center; font-size: 13px; color: #64748b; }
.spacer { flex: 1; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
</style>
