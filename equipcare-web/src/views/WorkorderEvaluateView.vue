<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';

/**
 * 工单服务评价（Vue 第八批）：态度/质量/速度三项评分 + 文字评价。
 * 接口：POST /api/workorder/{id}/evaluate（EvaluateDTO）。
 */
const route = useRoute();
const router = useRouter();
const id = computed(() => Number(route.params.id));

const orderNo = ref('');
const loading = ref(true);
const submitting = ref(false);
const form = reactive({ attitudeScore: 5, qualityScore: 5, speedScore: 5, content: '' });

const labels = ['很不满意', '不满意', '一般', '满意', '非常满意'];

async function load() {
  loading.value = true;
  try {
    const d = await http.get<never, { orderNo: string; title: string; status: string; evaluated?: boolean }>(
      `/api/workorder/${id.value}`);
    orderNo.value = d.orderNo;
    if (d.status !== 'COMPLETED') {
      ElMessage.warning('仅已完成的工单可以评价');
      router.replace(`/workorder/${id.value}`);
      return;
    }
    if (d.evaluated) {
      ElMessage.warning('该工单已评价过');
      router.replace(`/workorder/${id.value}`);
    }
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!form.content.trim() || form.content.trim().length < 5) {
    return ElMessage.warning('请填写不少于 5 个字的评价内容');
  }
  submitting.value = true;
  try {
    await http.post(`/api/workorder/${id.value}/evaluate`, { ...form, content: form.content.trim() });
    ElMessage.success('评价已提交，感谢反馈');
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
        <h3 style="margin:0">评价工单 {{ orderNo }}</h3>
        <el-button @click="router.replace(`/workorder/${id}`)">← 返回详情</el-button>
      </div>
    </el-card>
    <el-card shadow="never" class="blk">
      <el-form label-position="top">
        <el-form-item label="服务态度" required>
          <el-rate v-model="form.attitudeScore" :max="5" :texts="labels" show-text />
        </el-form-item>
        <el-form-item label="维修质量" required>
          <el-rate v-model="form.qualityScore" :max="5" :texts="labels" show-text />
        </el-form-item>
        <el-form-item label="响应速度" required>
          <el-rate v-model="form.speedScore" :max="5" :texts="labels" show-text />
        </el-form-item>
        <el-form-item label="评价内容（不少于 5 字）" required>
          <el-input v-model="form.content" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.blk { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: center; }
</style>
