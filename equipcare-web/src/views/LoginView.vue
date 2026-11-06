<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const loading = ref(false);
const form = reactive({ username: '', password: '' });

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号与密码');
    return;
  }
  loading.value = true;
  try {
    const login = await http.post<never, { accessToken: string; refreshToken: string }>(
      '/api/auth/login',
      { username: form.username, password: form.password },
    );
    auth.save(login);
    const me = await http.get<never, import('../stores/auth').UserInfo>('/api/auth/me');
    auth.saveUser(me);
    ElMessage.success(`欢迎，${me.realName || me.username}`);
    router.push((route.query.redirect as string) || '/dashboard');
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="mark">EC</span>
        <div>
          <b>巡维云 EquipCare</b>
          <span>设备巡检维保平台 · Vue 管理端</span>
        </div>
      </div>
      <el-form label-position="top" @keyup.enter="submit">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="用户名" autofocus />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button type="primary" class="submit" size="large" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
      <p class="tip">首次部署请先用引导命令创建管理员（见仓库 README）</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background:
    radial-gradient(800px 400px at 80% -10%, rgba(52, 185, 138, 0.16), transparent 60%),
    radial-gradient(600px 380px at 10% 110%, rgba(96, 165, 250, 0.12), transparent 60%),
    #0f172a;
}
.login-card {
  width: 380px;
  background: #fff;
  border-radius: 16px;
  padding: 34px 34px 22px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.4);
}
.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 26px;
}
.mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(140deg, #1d9c6b, #10653f);
}
.brand b {
  display: block;
  font-size: 17px;
  color: #263445;
}
.brand span {
  font-size: 12px;
  color: #94a3b8;
}
.submit {
  width: 100%;
  margin-top: 6px;
}
.tip {
  margin-top: 16px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}
</style>
