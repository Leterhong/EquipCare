<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';

/**
 * 设备新建/编辑表单（Vue 迁移收尾）。
 * 新建 POST /api/asset/equipment | 编辑 PUT /api/asset/equipment/{id}
 */
interface CategoryNode { id: number; categoryName: string; children?: CategoryNode[] }
interface LocationNode { id: number; fullPath: string; children?: LocationNode[] }
interface UserOption { id: number; realName?: string; username?: string }

const route = useRoute();
const router = useRouter();
const editId = computed(() => (route.params.id ? Number(route.params.id) : null));

const categories = ref<{ id: number; label: string }[]>([]);
const locations = ref<{ id: number; label: string }[]>([]);
const users = ref<UserOption[]>([]);
const loading = ref(true);
const saving = ref(false);

const form = reactive({
  assetNo: '', equipmentName: '', categoryId: null as number | null,
  locationId: null as number | null, deptId: null as number | null,
  brand: '', model: '', spec: '', serialNo: '', manufacturer: '', supplier: '',
  purchaseDate: '', installDate: '', warrantyEndDate: '',
  originalValue: null as number | null, responsibleUserId: null as number | null,
  remark: '',
});

function flatten(nodes: { id: number; categoryName?: string; fullPath?: string; children?: any }[], labelKey: string, prefix: string) {
  const out: { id: number; label: string }[] = [];
  for (const n of nodes) {
    const label = prefix ? `${prefix} / ${labelKey === 'categoryName' ? n.categoryName : n.fullPath}` : (labelKey === 'categoryName' ? n.categoryName : n.fullPath) || `#${n.id}`;
    out.push({ id: n.id, label });
    if (n.children?.length) out.push(...flatten(n.children, labelKey, label));
  }
  return out;
}

const categoryOptions = computed(() => flatten(categories.value as { id: number; categoryName?: string; children?: any }[], 'categoryName', ''));
const locationOptions = computed(() => flatten(locations.value as { id: number; fullPath?: string; children?: any }[], 'fullPath', ''));

async function save() {
  if (!form.equipmentName.trim()) return ElMessage.warning('请填写设备名称');
  if (!form.categoryId) return ElMessage.warning('请选择设备分类');
  if (!form.locationId) return ElMessage.warning('请选择所在位置');
  saving.value = true;
  try {
    const payload = {
      assetNo: form.assetNo.trim() || null,
      equipmentName: form.equipmentName.trim(),
      categoryId: form.categoryId,
      locationId: form.locationId,
      deptId: form.deptId,
      brand: form.brand.trim() || null,
      model: form.model.trim() || null,
      spec: form.spec.trim() || null,
      serialNo: form.serialNo.trim() || null,
      manufacturer: form.manufacturer.trim() || null,
      supplier: form.supplier.trim() || null,
      purchaseDate: form.purchaseDate || null,
      installDate: form.installDate || null,
      warrantyEndDate: form.warrantyEndDate || null,
      originalValue: form.originalValue,
      responsibleUserId: form.responsibleUserId,
      remark: form.remark.trim() || null,
    };
    if (editId.value) {
      await http.put(`/api/asset/equipment/${editId.value}`, payload);
      ElMessage.success('设备已更新');
    } else {
      await http.post('/api/asset/equipment', payload);
      ElMessage.success('设备已创建');
    }
    router.push('/equipment');
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败');
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  try {
    const [cats, locs, usrs] = await Promise.all([
      http.get<never, any[]>('/api/asset/categories/tree'),
      http.get<never, any[]>('/api/asset/locations/tree'),
      http.get<never, UserOption[]>('/api/workorder/assignees').catch(() => []),
    ]);
    categories.value = cats;
    locations.value = locs;
    users.value = usrs;
    if (editId.value) {
      const d = await http.get<never, Record<string, unknown>>(`/api/asset/equipment/${editId.value}`);
      Object.assign(form, {
        assetNo: (d.assetNo as string) || '',
        equipmentName: (d.equipmentName as string) || '',
        categoryId: (d.categoryId as number) ?? null,
        locationId: (d.locationId as number) ?? null,
        deptId: (d.deptId as number) ?? null,
        brand: (d.brand as string) || '',
        model: (d.model as string) || '',
        spec: (d.spec as string) || '',
        serialNo: (d.serialNo as string) || '',
        manufacturer: (d.manufacturer as string) || '',
        supplier: (d.supplier as string) || '',
        purchaseDate: (d.purchaseDate as string)?.slice(0, 10) || '',
        installDate: (d.installDate as string)?.slice(0, 10) || '',
        warrantyEndDate: (d.warrantyEndDate as string)?.slice(0, 10) || '',
        originalValue: (d.originalValue as number) ?? null,
        responsibleUserId: (d.responsibleUserId as number) ?? null,
        remark: (d.remark as string) || '',
      });
    }
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-loading="loading">
    <el-card shadow="never" class="blk">
      <template #header><b>{{ editId ? '编辑设备' : '新建设备' }}</b></template>
      <el-form label-position="top" style="max-width: 720px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="设备名称" required><el-input v-model="form.equipmentName" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="资产编号（留空自动生成）"><el-input v-model="form.assetNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="序列号"><el-input v-model="form.serialNo" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="设备分类" required><el-select v-model="form.categoryId" filterable style="width:100%"><el-option v-for="c in categoryOptions" :key="c.id" :label="c.label" :value="c.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="所在位置" required><el-select v-model="form.locationId" filterable style="width:100%"><el-option v-for="l in locationOptions" :key="l.id" :label="l.label" :value="l.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="责任人"><el-select v-model="form.responsibleUserId" filterable clearable style="width:100%"><el-option v-for="u in users" :key="u.id" :label="u.realName || u.username" :value="u.id" /></el-select></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="型号"><el-input v-model="form.model" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="规格"><el-input v-model="form.spec" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="制造商"><el-input v-model="form.manufacturer" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="采购日期"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="安装日期"><el-date-picker v-model="form.installDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="保修截止"><el-date-picker v-model="form.warrantyEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="资产原值（元）"><el-input-number v-model="form.originalValue" :min="0" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="300" /></el-form-item>
        <el-button type="primary" :loading="saving" @click="save">{{ editId ? '保存修改' : '创建设备' }}</el-button>
        <el-button @click="router.back()">取消</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.blk { margin-bottom: 14px; }
</style>
