<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';

/**
 * 维修过程记录（Vue 第八批，第一个操作页）：
 * 开始维修（原因+诊断+维修前照片）→ 过程记录（诊断/过程/完工，完工必须维修后照片）
 * → 提交验收（维修总结+工时）。接口与旧版 workorder-repair.js 完全一致。
 */
interface ImageRef { id: number; fileName?: string }
interface RepairRecord {
  recordType: string;
  content?: string;
  operatorName?: string;
  createTime?: string;
  costMinutes?: number;
  beforeImages?: ImageRef[];
  afterImages?: ImageRef[];
}
interface Detail {
  id: number;
  orderNo: string;
  title: string;
  status: string;
  equipmentName?: string;
  faultDesc?: string;
  assigneeId?: number;
  repairStarted?: boolean;
  repairRecords?: RepairRecord[];
  spareCost?: number;
}

const RECORD_TYPE: Record<string, string> = {
  DIAGNOSE: '诊断', PROCESS: '过程', START: '开始维修', FINISH: '完工', OTHER: '其他',
};

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const id = computed(() => Number(route.params.id));
const detail = ref<Detail | null>(null);
const loading = ref(true);
const submitting = ref(false);
const appliedPartCount = ref(0); // 待审批备件申请数（后端提交验收会校验，这里仅展示提醒）

// ---------- 开始维修 ----------
const startForm = reactive({ reason: '', diagnosis: '', beforePhotos: [] as UploadUserFile[] });

// ---------- 过程记录 ----------
const RECORD_TYPES = [
  { value: 'DIAGNOSE', label: '诊断' },
  { value: 'PROCESS', label: '过程' },
  { value: 'FINISH', label: '完工' },
  { value: 'OTHER', label: '其他' },
];
const recordForm = reactive({
  recordType: 'PROCESS',
  content: '',
  costMinutes: null as number | null,
  beforePhotos: [] as UploadUserFile[],
  afterPhotos: [] as UploadUserFile[],
});

// ---------- 提交验收 ----------
const submitForm = reactive({ repairSummary: '', laborHours: null as number | null });

const hasFinishRecord = computed(() =>
  (detail.value?.repairRecords || []).some((r) => r.recordType === 'FINISH'));

async function load() {
  loading.value = true;
  try {
    detail.value = await http.get<never, Detail>(`/api/workorder/${id.value}`);
    if (detail.value.status !== 'IN_PROGRESS') {
      ElMessage.warning('工单不在维修中状态');
      router.replace(`/workorder/${id.value}`);
      return;
    }
    if (detail.value.assigneeId !== auth.user?.userId) {
      ElMessage.warning('只有工单维修人可以记录维修过程');
      router.replace(`/workorder/${id.value}`);
    }
    // 待审批备件申请数（提交验收后端会拦截，这里提前提示）
    try {
      const parts = await http.get<never, { records: { issueStatus: string }[] }>(
        `/api/workorder/${id.value}/parts`, { params: { pageNum: 1, pageSize: 50 } });
      appliedPartCount.value = (parts.records || []).filter((p) => p.issueStatus === 'APPLIED').length;
    } catch {
      appliedPartCount.value = 0;
    }
  } finally {
    loading.value = false;
  }
}

/** 上传一张照片 → fileId（bizType=WORK_ORDER，服务端重映射 contentType） */
async function uploadPhoto(file: File): Promise<number> {
  const form = new FormData();
  form.append('file', file);
  form.append('bizType', 'WORK_ORDER');
  const data = await http.post<never, { fileId: number }>('/api/files/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.fileId;
}

/** el-upload 文件列表 → 上传并收集 id（任一失败即抛错中断） */
async function uploadAll(files: UploadUserFile[]): Promise<number[]> {
  const ids: number[] = [];
  for (const f of files) {
    if (f.raw) {
      ids.push(await uploadPhoto(f.raw));
    }
  }
  return ids;
}

function checkSelected(files: UploadUserFile[], label: string) {
  for (const f of files) {
    if (f.raw && !/\.(jpe?g|png|gif|webp)$/i.test(f.name)) {
      ElMessage.warning(`${label}仅支持 jpg/png/gif/webp 图片`);
      return false;
    }
  }
  return true;
}

async function startRepair() {
  if (startForm.reason.trim().length < 5) return ElMessage.warning('维修原因不少于 5 个字');
  if (startForm.diagnosis.trim().length < 5) return ElMessage.warning('故障诊断不少于 5 个字');
  if (!checkSelected(startForm.beforePhotos, '维修前照片')) return;
  submitting.value = true;
  try {
    const ids = await uploadAll(startForm.beforePhotos);
    await http.post(`/api/workorder/${id.value}/start-repair`, {
      reason: startForm.reason.trim(),
      diagnosis: startForm.diagnosis.trim(),
      beforeImageIds: ids.length ? ids.join(',') : null,
    });
    ElMessage.success('已开始维修，计时开始');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '开始维修失败');
  } finally {
    submitting.value = false;
  }
}

async function addRecord() {
  if (!recordForm.content.trim()) return ElMessage.warning('请填写记录内容');
  if (recordForm.recordType === 'FINISH' && recordForm.afterPhotos.length === 0) {
    return ElMessage.warning('完工说明必须上传至少 1 张维修后照片');
  }
  if (!checkSelected([...recordForm.beforePhotos, ...recordForm.afterPhotos], '照片')) return;
  submitting.value = true;
  try {
    const beforeIds = await uploadAll(recordForm.beforePhotos);
    const afterIds = await uploadAll(recordForm.afterPhotos);
    await http.post(`/api/workorder/${id.value}/records`, {
      recordType: recordForm.recordType,
      content: recordForm.content.trim(),
      costMinutes: recordForm.costMinutes,
      beforeImageIds: beforeIds.length ? beforeIds.join(',') : null,
      afterImageIds: afterIds.length ? afterIds.join(',') : null,
    });
    ElMessage.success('记录已保存');
    recordForm.content = '';
    recordForm.costMinutes = null;
    recordForm.beforePhotos = [];
    recordForm.afterPhotos = [];
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function submitVerify() {
  if (submitForm.repairSummary.trim().length < 5) return ElMessage.warning('请填写维修总结');
  if (appliedPartCount.value > 0) return ElMessage.warning('有备件申请待审批，请先处理');
  if (!hasFinishRecord.value) return ElMessage.warning('缺少「完工说明」记录');
  submitting.value = true;
  try {
    await http.post(`/api/workorder/${id.value}/submit-verify`, {
      repairSummary: submitForm.repairSummary.trim(),
      laborHours: submitForm.laborHours,
    });
    ElMessage.success('已提交验收');
    router.replace(`/workorder/${id.value}`);
  } catch (e) {
    ElMessage.error((e as Error).message || '提交失败');
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading">
    <template v-if="detail">
      <el-card shadow="never" class="blk">
        <div class="head">
          <div>
            <h3 style="margin:0">{{ detail.orderNo }} · {{ detail.title }}</h3>
            <span class="hint">{{ detail.equipmentName }} · {{ detail.faultDesc }}</span>
          </div>
          <el-button @click="router.replace(`/workorder/${id}`)">← 返回详情</el-button>
        </div>
      </el-card>

      <!-- 开始维修（未开始时显示） -->
      <el-card v-if="!detail.repairStarted" shadow="never" class="blk">
        <template #header><b>开始维修</b>（提交后开始计时）</template>
        <el-form label-position="top">
          <el-form-item label="维修原因（不少于 5 字）" required>
            <el-input v-model="startForm.reason" maxlength="200" />
          </el-form-item>
          <el-form-item label="故障诊断（不少于 5 字）" required>
            <el-input v-model="startForm.diagnosis" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
          <el-form-item label="维修前照片（可选）">
            <el-upload v-model:file-list="startForm.beforePhotos" multiple accept=".jpg,.jpeg,.png,.gif,.webp"
              :auto-upload="false" list-type="picture-card" :on-change="(f: UploadUserFile, list: UploadUserFile[]) => (startForm.beforePhotos = list)">
              <el-icon>+</el-icon>
            </el-upload>
          </el-form-item>
          <el-button type="primary" :loading="submitting" @click="startRepair">确认开始维修</el-button>
        </el-form>
      </el-card>

      <!-- 过程记录（开始后显示） -->
      <el-card v-else shadow="never" class="blk">
        <template #header><b>新增过程记录</b></template>
        <el-form label-position="top">
          <el-row :gutter="12">
            <el-col :span="6">
              <el-form-item label="记录类型">
                <el-select v-model="recordForm.recordType">
                  <el-option v-for="t in RECORD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="耗时（分钟）">
                <el-input-number v-model="recordForm.costMinutes" :min="1" :max="10080" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="记录内容" required>
            <el-input v-model="recordForm.content" type="textarea" :rows="3" maxlength="1000" />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="维修前照片">
                <el-upload v-model:file-list="recordForm.beforePhotos" multiple accept=".jpg,.jpeg,.png,.gif,.webp"
                  :auto-upload="false" list-type="picture-card"
                  :on-change="(f: UploadUserFile, list: UploadUserFile[]) => (recordForm.beforePhotos = list)">
                  <el-icon>+</el-icon>
                </el-upload>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="维修后照片">
                <el-upload v-model:file-list="recordForm.afterPhotos" multiple accept=".jpg,.jpeg,.png,.gif,.webp"
                  :auto-upload="false" list-type="picture-card"
                  :on-change="(f: UploadUserFile, list: UploadUserFile[]) => (recordForm.afterPhotos = list)">
                  <el-icon>+</el-icon>
                </el-upload>
              </el-form-item>
            </el-col>
          </el-row>
          <el-button type="primary" :loading="submitting" @click="addRecord">保存记录</el-button>
        </el-form>

        <el-divider />
        <div v-for="(rec, i) in detail.repairRecords" :key="i" class="rec-row">
          <el-tag size="small">{{ RECORD_TYPE[rec.recordType] || rec.recordType }}</el-tag>
          <span class="hint" style="margin-left:6px">{{ rec.content }}</span>
        </div>
        <p v-if="!(detail.repairRecords || []).length" class="hint">还没有过程记录</p>
      </el-card>

      <!-- 提交验收 -->
      <el-card shadow="never" class="blk">
        <template #header><b>提交验收</b></template>
        <el-alert
          v-if="!hasFinishRecord || appliedPartCount > 0"
          type="warning"
          :closable="false"
          style="margin-bottom: 12px"
        >
          <template v-if="!hasFinishRecord">缺少「完工说明」记录（需先保存一条完工类型记录）。</template>
          <template v-if="appliedPartCount > 0">有 {{ appliedPartCount }} 条备件申请待审批。</template>
        </el-alert>
        <el-form label-position="top">
          <el-form-item label="维修总结（不少于 5 字）" required>
            <el-input v-model="submitForm.repairSummary" type="textarea" :rows="3" maxlength="500" />
          </el-form-item>
          <el-form-item label="维修工时（小时，可选）">
            <el-input-number v-model="submitForm.laborHours" :min="0.5" :max="720" :step="0.5" />
          </el-form-item>
          <el-button type="primary" :disabled="!hasFinishRecord || appliedPartCount > 0" :loading="submitting"
            @click="submitVerify">
            提交验收
          </el-button>
        </el-form>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.blk { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.hint { color: #94a3b8; font-size: 12.5px; }
.rec-row { padding: 6px 0; border-bottom: 1px dashed var(--el-border-color-lighter); }
</style>
