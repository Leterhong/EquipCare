<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import { http } from '../api/http';

/**
 * 工单报修表单（Vue 迁移收尾）：设备远程搜索、故障类型/等级、描述、现场照片。
 * 接口：POST /api/workorder（WorkOrderCreateDTO：equipmentId/title/faultType/
 * faultDesc/faultImageIds/level）。
 */
interface EquipmentOption {
  id: number;
  assetNo?: string;
  equipmentName: string;
  status?: string;
}

const FAULT_TYPES = [
  { code: 'MECHANICAL', label: '机械故障' },
  { code: 'ELECTRIC', label: '电气故障' },
  { code: 'HYDRAULIC', label: '液压故障' },
  { code: 'PNEUMATIC', label: '气动故障' },
  { code: 'INSTRUMENT', label: '仪表/控制' },
  { code: 'LEAKAGE', label: '跑冒滴漏' },
  { code: 'OTHER', label: '其他' },
];
const LEVELS = [
  { code: 'P1', label: 'P1 紧急' },
  { code: 'P2', label: 'P2 高' },
  { code: 'P3', label: 'P3 中' },
  { code: 'P4', label: 'P4 低' },
];

const router = useRouter();
const submitting = ref(false);
const form = reactive({
  equipmentId: null as number | null,
  title: '',
  faultType: 'MECHANICAL',
  faultDesc: '',
  level: 'P3',
});
const photos = ref<UploadUserFile[]>([]);

const equipmentOptions = ref<EquipmentOption[]>([]);
const equipmentLoading = ref(false);

async function searchEquipment(keyword: string) {
  equipmentLoading.value = true;
  try {
    const page = await http.get<never, { records: EquipmentOption[] }>('/api/asset/equipment', {
      params: { pageNum: 1, pageSize: 50, keyword: keyword || undefined },
    });
    equipmentOptions.value = page.records || [];
  } finally {
    equipmentLoading.value = false;
  }
}

async function uploadPhotos(): Promise<string | null> {
  const ids: number[] = [];
  for (const f of photos.value) {
    if (!f.raw) continue;
    const form = new FormData();
    form.append('file', f.raw);
    form.append('bizType', 'WORK_ORDER');
    const data = await http.post<never, { fileId: number }>('/api/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    ids.push(data.fileId);
  }
  return ids.length ? ids.join(',') : null;
}

async function submit() {
  if (!form.equipmentId) return ElMessage.warning('请选择设备');
  if (!form.title.trim()) return ElMessage.warning('请填写工单标题');
  if (!form.faultDesc.trim() || form.faultDesc.trim().length < 5) {
    return ElMessage.warning('请填写故障描述（不少于 5 字）');
  }
  if (!checkPhotos()) return;
  submitting.value = true;
  try {
    const faultImageIds = await uploadPhotos();
    const newId = await http.post<never, number>('/api/workorder', {
      equipmentId: form.equipmentId,
      title: form.title.trim(),
      faultType: form.faultType,
      faultDesc: form.faultDesc.trim(),
      level: form.level,
      faultImageIds: faultImageIds,
    });
    ElMessage.success('报修已提交，等待派单');
    router.replace(`/workorder/${newId}`);
  } catch (e) {
    ElMessage.error((e as Error).message || '提交失败');
  } finally {
    submitting.value = false;
  }
}

function checkPhotos() {
  for (const f of photos.value) {
    if (f.raw && !/\.(jpe?g|png|gif|webp)$/i.test(f.name)) {
      ElMessage.warning('现场照片仅支持 jpg/png/gif/webp');
      return false;
    }
  }
  return true;
}

onMounted(() => searchEquipment(''));
</script>

<template>
  <el-card shadow="never">
    <template #header><b>报修 · 新建维修工单</b></template>
    <el-form label-position="top" style="max-width: 720px">
      <el-form-item label="设备" required>
        <el-select
          v-model="form.equipmentId"
          filterable
          remote
          :remote-method="searchEquipment"
          :loading="equipmentLoading"
          placeholder="输入资产编号 / 名称搜索"
          style="width: 100%"
        >
          <el-option
            v-for="eq in equipmentOptions"
            :key="eq.id"
            :label="`${eq.equipmentName}（${eq.assetNo || '—'}）`"
            :value="eq.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="工单标题" required>
        <el-input v-model="form.title" maxlength="120" placeholder="如：3 号空压机异响" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="故障类型" required>
            <el-select v-model="form.faultType" style="width: 100%">
              <el-option v-for="t in FAULT_TYPES" :key="t.code" :label="t.label" :value="t.code" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="故障等级" required>
            <el-select v-model="form.level" style="width: 100%">
              <el-option v-for="l in LEVELS" :key="l.code" :label="l.label" :value="l.code" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="故障描述（不少于 5 字）" required>
        <el-input v-model="form.faultDesc" type="textarea" :rows="4" maxlength="1000"
          placeholder="现象、发生时间、影响范围…" />
      </el-form-item>
      <el-form-item label="现场照片（可选，jpg/png/gif/webp）">
        <el-upload v-model:file-list="photos" multiple accept=".jpg,.jpeg,.png,.gif,.webp"
          :auto-upload="false" list-type="picture-card">
          <el-icon>+</el-icon>
        </el-upload>
      </el-form-item>
      <el-button type="primary" size="large" :loading="submitting" @click="submit">提交报修</el-button>
    </el-form>
  </el-card>
</template>

<style scoped>
</style>
