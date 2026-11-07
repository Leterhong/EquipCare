<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';

/**
 * 维修验收（Vue 第八批）：通过/不通过 + 质量评分 + 备注。
 * 接口：POST /api/workorder/{id}/verify-pass | verify-reject（VerifyDTO）。
 */
const route = useRoute();
const router = useRouter();
const id = computed(() => Number(route.params.id));

const orderNo = ref('');
const title = ref('');
const repairSummary = ref('');
const loading = ref(true);
const submitting = ref(false);
const form = reactive({ qualityScore: 5, remark: '' });

async function load() {
  loading.value = true;
  try {
    const d = await http.get<never, { orderNo: string; title: string; repairSummary?: string; status: string }>(
      `/api/workorder/${id.value}`);
    orderNo.value = d.orderNo;
    title.value = d.title;
    repairSummary.value = d.repairSummary || '';
    if (d.status !== 'PENDING_REVIEW') {
      ElMessage.warning('工单不在待验收状态');
      router.replace(`/workorder/${id.value}`);
    }
  } finally {
    loading.value = false;
  }
}

async function submit(pass: boolean) {
  if (!pass && form.remark.trim().length < 5) {
    return ElMessage.warning('验收不通过必须填写原因（不少于 5 字）');
  }
  if (pass && (form.qualityScore == null || form.qualityScore < 1 || form.qualityScore > 5)) {
    return ElMessage.warning('请给出 1~5 的质量评分');
  }
  submitting.value = true;
  try {
    await http.post(`/api/workorder/${id.value}/${pass ? 'verify-pass' : 'verify-reject'}`, {
      qualityScore: pass ? form.qualityScore : null,
      remark: form.remark.trim() || null,
    });
    ElMessage.success(pass ? '验收通过' : '已驳回');
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
    <el-card shadow="never" class="blk">
      <div class="head">
        <div>
          <h3 style="margin:0">{{ orderNo }} · {{ title }}</h3>
          <span class="hint">维修总结：{{ repairSummary || '（未填写）' }}</span>
        </div>
        <el-button @click="router.replace(`/workorder/${id}`)">← 返回详情</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="blk">
      <template #header><b>验收结论</b></template>
      <el-form label-position="top">
        <el-form-item label="质量评分（1~5）" required>
          <el-rate v-model="form.qualityScore" :max="5" show-score />
        </el-form-item>
        <el-form-item label="验收备注 / 不通过原因">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500"
            placeholder="验收通过可填写评语；不通过必须填写原因（不少于 5 字）" />
        </el-form-item>
        <div style="display:flex;gap:10px">
          <el-button type="primary" :loading="submitting" @click="submit(true)">✓ 验收通过</el-button>
          <el-button type="danger" :loading="submitting" @click="submit(false)">✕ 验收不通过（驳回维修）</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.blk { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.hint { color: #94a3b8; font-size: 12.5px; }
</style>
