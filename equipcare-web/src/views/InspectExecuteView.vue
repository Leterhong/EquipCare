<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import { http } from '../api/http';

/**
 * 巡检执行（Vue 第九批，最复杂操作页）：
 * 三态判定（正常/异常/跳过）+ 数值/枚举实测值 + 异常原因/跳过原因 + 现场照片
 * → 提交后异常项自动生成巡检异常（autoWorkOrder 项自动转维修工单）。
 * 接口与旧版 inspect-execute.js 一致（/api/inspection/tasks/{id}/submit）。
 */
interface TaskItem {
  templateItemId: number;
  itemName: string;
  itemType: string;
  unit?: string;
  minValue?: number;
  maxValue?: number;
  enumOptions?: string;
  normalValue?: string;
  required?: number;
  checkMethod?: string;
  standardRef?: string;
  abnormalRule?: string;
  photoRequired?: number;
  autoWorkOrder?: number;
  abnormalLevel?: string;
  itemValue?: string;
  result?: string;
  isAbnormal?: number;
  remark?: string;
  skipReason?: string;
  attachmentIds?: string;
}
interface EquipmentBrief {
  equipmentName?: string;
  assetNo?: string;
  statusLabel?: string;
  categoryName?: string;
  locationPath?: string;
  responsibleUserName?: string;
  qrImageUrl?: string;
}
interface TaskDetail {
  id: number;
  taskCode?: string;
  planName?: string;
  planDate?: string;
  status?: string;
  slaDueTime?: string;
  overdueFlag?: number;
  remark?: string;
  equipment?: EquipmentBrief;
  items: TaskItem[];
  skippedCount?: number;
}

const LEVEL: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' };

const route = useRoute();
const router = useRouter();
const id = computed(() => Number(route.params.id));

const detail = ref<TaskDetail | null>(null);
const loading = ref(true);
const submitting = ref(false);
const totalRemark = ref('');
const qrUrl = ref('');
const photoFiles = reactive<Record<number, UploadUserFile[]>>({}); // templateItemId → 待上传照片

/** 每项的执行状态：结果/实测值/备注/跳过原因 */
const states = reactive<Record<number, { result: string; itemValue: string; remark: string; skipReason: string }>>({});

function stateOf(item: TaskItem) {
  if (!states[item.templateItemId]) {
    states[item.templateItemId] = {
      result: item.result || '',
      itemValue: item.itemValue || '',
      remark: item.remark || '',
      skipReason: item.skipReason || '',
    };
  }
  return states[item.templateItemId];
}

const stats = computed(() => {
  let normal = 0, abnormal = 0, skipped = 0, pending = 0;
  for (const item of detail.value?.items || []) {
    const r = states[item.templateItemId]?.result;
    if (r === 'NORMAL') normal++;
    else if (r === 'ABNORMAL') abnormal++;
    else if (r === 'SKIPPED') skipped++;
    else pending++;
  }
  return { normal, abnormal, skipped, pending };
});

/** NUMBER 类型按标准范围自动建议判定（用户可改） */
function autoJudge(item: TaskItem) {
  const s = stateOf(item);
  if (item.itemType !== 'NUMBER' || !s.itemValue) return;
  const v = Number(s.itemValue);
  if (Number.isNaN(v)) return;
  const inRange =
    (item.minValue == null || v >= Number(item.minValue)) &&
    (item.maxValue == null || v <= Number(item.maxValue));
  if (s.result === '' || s.result === 'NORMAL' || s.result === 'ABNORMAL') {
    s.result = inRange ? 'NORMAL' : 'ABNORMAL';
  }
}

async function load() {
  loading.value = true;
  try {
    detail.value = await http.get<never, TaskDetail>(`/api/inspection/tasks/${id.value}`);
    if (!['PENDING', 'IN_PROGRESS'].includes(detail.value.status || '')) {
      ElMessage.warning('任务不在可执行状态');
      router.replace('/inspect-task');
      return;
    }
    // 回显历史结果/备注
    for (const item of detail.value.items) stateOf(item);
    // 设备二维码（执行页身份核对）
    try {
      const blob = await http.get<never, Blob>(`${detail.value.equipment?.qrImageUrl}`, {
        responseType: 'blob',
      } as never);
      qrUrl.value = URL.createObjectURL(blob);
    } catch {
      /* 二维码缺失不阻塞 */
    }
  } finally {
    loading.value = false;
  }
}

/** 上传照片 → attachmentIds（bizType=INSPECT，逗号拼接） */
async function uploadPhotos(itemId: number): Promise<string | null> {
  const files = photoFiles[itemId] || [];
  if (!files.length) return null;
  const ids: number[] = [];
  for (const f of files) {
    if (!f.raw) continue;
    const form = new FormData();
    form.append('file', f.raw);
    form.append('bizType', 'INSPECT');
    const data = await http.post<never, { fileId: number }>('/api/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    ids.push(data.fileId);
  }
  return ids.length ? ids.join(',') : null;
}

function validate(): string | null {
  for (const item of detail.value?.items || []) {
    const s = states[item.templateItemId];
    if (!s?.result) {
      return `「${item.itemName}」尚未判定`;
    }
    if (s.result === 'ABNORMAL' && s.remark.trim().length < 2) {
      return `「${item.itemName}」判定异常，请填写异常说明`;
    }
    if (s.result === 'NORMAL' && item.itemType === 'NUMBER' && !s.itemValue) {
      return `「${item.itemName}」为数值检查项，请填写实测值`;
    }
    if (s.result === 'SKIPPED') {
      if (item.required === 1) {
        return `「${item.itemName}」为必检项，不允许跳过`;
      }
      if (s.skipReason.trim().length < 2) {
        return `「${item.itemName}」跳过必须填写原因`;
      }
    }
  }
  return null;
}

async function submit() {
  const err = validate();
  if (err) {
    ElMessage.warning(err);
    return;
  }
  submitting.value = true;
  try {
    const items = [];
    for (const item of detail.value?.items || []) {
      const s = states[item.templateItemId];
      const attachmentIds = await uploadPhotos(item.templateItemId);
      items.push({
        templateItemId: item.templateItemId,
        itemValue: s.itemValue || null,
        result: s.result,
        abnormal: s.result === 'ABNORMAL',
        remark: s.remark.trim() || null,
        skipReason: s.result === 'SKIPPED' ? s.skipReason.trim() : null,
        attachmentIds,
      });
    }
    await http.post(`/api/inspection/tasks/${id.value}/submit`, {
      remark: totalRemark.value.trim() || null,
      items,
    });
    ElMessage.success('巡检已提交');
    if (stats.value.abnormal > 0) {
      ElMessage.info(`${stats.value.abnormal} 项异常已生成异常单，配置自动转单的项目将生成维修工单`);
    }
    router.replace('/inspect-task');
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
      <!-- 头部：任务 + 设备身份核对 -->
      <el-card shadow="never" class="blk">
        <div class="head">
          <div>
            <h3 style="margin:0">
              {{ detail.taskCode }}
              <el-tag v-if="detail.overdueFlag === 1" type="danger" size="small" style="margin-left:6px">已逾期</el-tag>
            </h3>
            <span class="hint">{{ detail.planName }} · 计划日 {{ (detail.planDate || '').slice(0, 10) }} ·
              时限 {{ (detail.slaDueTime || '').replace('T', ' ').slice(0, 16) }}</span>
          </div>
          <el-button @click="router.replace('/inspect-task')">← 返回任务列表</el-button>
        </div>
        <div v-if="detail.equipment" class="equip-row">
          <img v-if="qrUrl" :src="qrUrl" class="qr" alt="设备二维码">
          <div>
            <b>{{ detail.equipment.equipmentName }}</b>
            <div class="hint mono">{{ detail.equipment.assetNo }}</div>
            <div class="hint">{{ detail.equipment.categoryName }} · {{ detail.equipment.locationPath || '—' }} ·
              责任人 {{ detail.equipment.responsibleUserName || '—' }}</div>
          </div>
        </div>
      </el-card>

      <!-- 判定汇总条 -->
      <el-alert :closable="false" class="blk" type="info">
        <span>已判定 {{ stats.normal + stats.abnormal + stats.skipped }} / {{ detail.items.length }} ·
          正常 {{ stats.normal }} · 异常 <b style="color:#dc2626">{{ stats.abnormal }}</b> ·
          跳过 {{ stats.skipped }} · 未判定 {{ stats.pending }}</span>
      </el-alert>

      <!-- 检查项 -->
      <el-card v-for="(item, idx) in detail.items" :key="item.templateItemId" shadow="never" class="blk item-card"
        :class="{ done: states[item.templateItemId]?.result }">
        <template #header>
          <div class="item-head">
            <b>{{ idx + 1 }}. {{ item.itemName }}</b>
            <div>
              <el-tag v-if="item.autoWorkOrder === 1" type="danger" size="small" style="margin-right:4px">异常自动转工单</el-tag>
              <el-tag v-if="item.required === 1" size="small">必检</el-tag>
              <el-tag v-if="item.photoRequired === 1" type="warning" size="small">需拍照</el-tag>
            </div>
          </div>
        </template>
        <div class="std">
          <span v-if="item.itemType === 'NUMBER'">
            标准范围：{{ item.minValue ?? '-∞' }} ~ {{ item.maxValue ?? '+∞' }} {{ item.unit || '' }}
          </span>
          <span v-else-if="item.normalValue">标准：{{ item.normalValue }}</span>
          <span v-if="item.checkMethod">· 检查方法：{{ item.checkMethod }}</span>
          <span v-if="item.standardRef">· 依据：{{ item.standardRef }}</span>
        </div>
        <div v-if="item.abnormalRule" class="hint">异常判定规则：{{ item.abnormalRule }}</div>

        <!-- 实测值（数值型） -->
        <div v-if="item.itemType === 'NUMBER'" class="row">
          <span class="lbl">实测值（{{ item.unit || '' }}）：</span>
          <el-input
            v-model="stateOf(item).itemValue"
            placeholder="填写实测数值，自动判定是否超标准"
            style="max-width: 200px"
            @input="autoJudge(item)"
          />
        </div>
        <!-- 枚举型 -->
        <div v-else-if="item.enumOptions" class="row">
          <span class="lbl">实测值：</span>
          <el-radio-group v-model="stateOf(item).itemValue">
            <el-radio v-for="opt in item.enumOptions.split(',')" :key="opt" :value="opt">{{ opt }}</el-radio>
          </el-radio-group>
        </div>

        <!-- 三态判定 -->
        <div class="row">
          <span class="lbl">判定：</span>
          <el-radio-group v-model="stateOf(item).result">
            <el-radio-button value="NORMAL">✓ 正常</el-radio-button>
            <el-radio-button value="ABNORMAL">✕ 异常</el-radio-button>
            <el-radio-button value="SKIPPED" :disabled="item.required === 1">— 跳过</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="stateOf(item).result === 'ABNORMAL'" class="row">
          <span class="lbl">异常说明：</span>
          <el-input v-model="stateOf(item).remark" placeholder="必填：异常现象与初步判断" style="max-width:480px" />
          <el-tag v-if="item.autoWorkOrder === 1" type="danger" size="small" style="margin-left:8px">提交后自动生成维修工单</el-tag>
        </div>
        <div v-if="stateOf(item).result === 'SKIPPED'" class="row">
          <span class="lbl">跳过原因：</span>
          <el-input v-model="stateOf(item).skipReason" placeholder="必填：如设备停机/不具备条件" style="max-width:480px" />
        </div>
        <!-- 现场照片 -->
        <div class="row">
          <span class="lbl">现场照片{{ item.photoRequired === 1 ? '（必传）' : '（可选）' }}：</span>
          <el-upload
            v-model:file-list="photoFiles[item.templateItemId]"
            multiple accept=".jpg,.jpeg,.png,.gif,.webp" :auto-upload="false" list-type="picture-card">
            <el-icon>+</el-icon>
          </el-upload>
        </div>
      </el-card>

      <!-- 总备注 + 提交 -->
      <el-card shadow="never" class="blk">
        <el-form label-position="top">
          <el-form-item label="本次巡检总体备注（可选）">
            <el-input v-model="totalRemark" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="submit">提交巡检结果</el-button>
        </el-form>
      </el-card>
    </template>
  </div>
</template>

<script lang="ts">
/** 序号辅助（模板内使用） */
function seqNo(item: { checkMethod?: string }) {
  void item;
  return '';
}
export default { name: 'InspectExecuteView' };
</script>

<style scoped>
.blk { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 10px; }
.equip-row { display: flex; gap: 14px; align-items: center; margin-top: 12px; }
.qr { width: 84px; height: 84px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.item-card.done { border-left: 3px solid var(--el-color-primary); }
.item-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.std { font-size: 13px; color: #475569; margin-bottom: 8px; }
.row { display: flex; align-items: center; gap: 8px; margin: 8px 0; flex-wrap: wrap; }
.lbl { font-size: 13px; color: #475569; min-width: 96px; }
.hint { color: #94a3b8; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
</style>
