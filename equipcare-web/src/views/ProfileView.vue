<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';

/** 个人中心（Vue 迁移收尾）：资料编辑 + 修改密码。 */
const auth = useAuthStore();
const router = useRouter();

const profile = reactive({
  realName: '', phone: '', email: '',
});
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' });
const savingProfile = ref(false);
const savingPwd = ref(false);

async function saveProfile() {
  if (!profile.realName.trim()) return ElMessage.warning('请填写姓名');
  savingProfile.value = true;
  try {
    await http.put('/api/auth/profile', {
      realName: profile.realName.trim(),
      phone: profile.phone.trim() || null,
      email: profile.email.trim() || null,
    });
    auth.saveUser({ ...auth.user!, realName: profile.realName.trim(), phone: profile.phone.trim(), email: profile.email.trim() });
    ElMessage.success('资料已更新');
  } catch (e) {
    ElMessage.error((e as Error).message || '更新失败');
  } finally {
    savingProfile.value = false;
  }
}

async function changePassword() {
  if (!pwd.oldPassword) return ElMessage.warning('请输入当前密码');
  if (!pwd.newPassword || pwd.newPassword.length < 8) return ElMessage.warning('新密码不少于 8 位');
  if (pwd.newPassword !== pwd.confirm) return ElMessage.warning('两次密码不一致');
  savingPwd.value = true;
  try {
    await http.put('/api/auth/password', {
      oldPassword: pwd.oldPassword,
      newPassword: pwd.newPassword,
    });
    ElMessage.success('密码已修改，请重新登录');
    auth.clear();
    router.push('/login');
  } catch (e) {
    ElMessage.error((e as Error).message || '修改失败');
  } finally {
    savingPwd.value = false;
  }
}

onMounted(async () => {
  try {
    const me = await http.get<never, { realName?: string; phone?: string; email?: string }>('/api/auth/me');
    profile.realName = me.realName || '';
    profile.phone = me.phone || '';
    profile.email = me.email || '';
  } catch {
    /* 静默 */
  }
});
</script>


<template>
  <el-row :gutter="14">
    <el-col :span="12">
      <el-card shadow="never" class="blk">
        <template #header><b>个人资料</b></template>
        <el-form label-position="top" style="max-width: 420px">
          <el-form-item label="姓名" required>
            <el-input v-model="profile.realName" maxlength="50" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="profile.phone" maxlength="20" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profile.email" maxlength="100" />
          </el-form-item>
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
        </el-form>
      </el-card>
    </el-col>
    <el-col :span="12">
      <el-card shadow="never" class="blk">
        <template #header><b>修改密码</b></template>
        <el-form label-position="top" style="max-width: 420px">
          <el-form-item label="当前密码" required>
            <el-input v-model="pwd.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码（不少于 8 位）" required>
            <el-input v-model="pwd.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认新密码" required>
            <el-input v-model="pwd.confirm" type="password" show-password />
          </el-form-item>
          <el-button type="primary" :loading="savingPwd" @click="changePassword">修改密码</el-button>
        </el-form>
      </el-card>
    </el-col>
  </el-row>
</template>

<style scoped>
.blk { margin-bottom: 14px; }
</style>
