<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';

/**
 * 备件库存管理（Vue 迁移收尾）：库存列表 + 入库/出库操作。
 * 接口：GET /api/inventory/parts、POST /api/inventory/stock/in | stock/out
 */
interface PartRow {
  id: number;
  partNo: string;
  partName: string;
  spec?: string;
  unit: string;
  unitPrice?: number;
  currentStock?: number;
  safetyStock?: number;
  belowSafety?: boolean;
  warehouseLocation?: string;
}

const rows = ref<PartRow[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' });
const opVisible = ref(false);
const opType = ref<'in' | 'out'>('in');
const opForm = reactive({ partId: 0, partName: '', quantity: 1, unitPrice: null as number | null, remark: '' });
const opSubmitting = ref(false);

async function load() {
  loading.value = true;
  try {
    const page = await http.get<never, { records: PartRow[]; total: number }>('/api/inventory/parts', {
      params: { ...query, keyword: query.keyword || undefined },
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

function openOp(type: 'in' | 'out', row: PartRow) {
  opType.value = type;
  opForm.partId = row.id;
  opForm.partName = `${row.partName}（当前 ${row.currentStock ?? 0} ${row.unit || ''}）`;
  opForm.quantity = 1;
  opForm.unitPrice = row.unitPrice ?? null;
  opForm.remark = '';
  opVisible.value = true;
}

async function submitOp() {
  if (!opForm.quantity || opForm.quantity <= 0) {
    return ElMessage.warning('请输入数量');
  }
  opSubmitting.value = true;
  try {
    await http.post(`/api/inventory/stock/${opType.value}`, {
      partId: opForm.partId,
      quantity: opForm.quantity,
      unitPrice: opForm.unitPrice,
      remark: opForm.remark.trim() || null,
    });
    ElMessage.success(opType.value === 'in' ? '入库成功' : '出库成功');
    opVisible.value = false;
    load();
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败');
  } finally {
    opSubmitting.value = false;
  }
}

function stockColor(row: PartRow) {
  if (row.currentStock == null || row.safetyStock == null) return '#94a3b8';
  if (row.currentStock <= row.safetyStock) return '#dc2626';
  if (row.currentStock <= row.safetyStock * 1.5) return '#d97706';
  return '#16845b';
}

onMounted(load);
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="备件编码 / 名称" clearable style="width: 220px"
        @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="partNo" label="编码" width="140">
        <template #default="{ row }"><span class="mono">{{ row.partNo }}</span></template>
      </el-table-column>
      <el-table-column prop="partName" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.spec || '—' }}</template>
      </el-table-column>
      <el-table-column label="当前库存" width="100" align="center">
        <template #default="{ row }">
          <b :style="{ color: stockColor(row as PartRow) }">{{ row.currentStock ?? '—' }}{{ row.unit || '' }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="safetyStock" label="安全库存" width="90" align="center" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.belowSafety ? 'danger' : 'success'" size="small">
            {{ row.belowSafety ? '低于安全线' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="warehouseLocation" label="库位" width="110">
        <template #default="{ row }">{{ row.warehouseLocation || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="right">
        <template #default="{ row }">
          <el-button link type="success" @click="openOp('in', row as PartRow)">入库</el-button>
          <el-button link type="danger" @click="openOp('out', row as PartRow)">出库</el-button>
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

  <!-- 入库/出库弹窗 -->
  <el-dialog v-model="opVisible" :title="opType === 'in' ? '入库操作' : '出库操作'" width="420px">
    <p style="margin-bottom: 12px; font-weight: 600">{{ opForm.partName }}</p>
    <el-form label-position="top">
      <el-form-item :label="opType === 'in' ? '入库数量' : '出库数量'" required>
        <el-input-number v-model="opForm.quantity" :min="1" style="width: 100%" />
      </el-form-item>
      <el-form-item v-if="opType === 'in'" label="单价（可选）">
        <el-input-number v-model="opForm.unitPrice" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注（可选）">
        <el-input v-model="opForm.remark" type="textarea" :rows="2" maxlength="200" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="opVisible = false">取消</el-button>
      <el-button type="primary" :loading="opSubmitting" @click="submitOp">
        {{ opType === 'in' ? '确认入库' : '确认出库' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
