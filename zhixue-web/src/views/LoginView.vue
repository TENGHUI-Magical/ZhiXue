<template>
  <div class="login-page">
    <el-card class="login-card" header="智学领航 · 登录">
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号（学号 / 工号）">
          <el-input v-model="username" autocomplete="username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>
      <div class="login-hint">本地开发种子账号见 zhixue-server/README.md</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { api } from "../api/request";

/** openapi LoginResult：token + userId + username + role 平铺 */
interface LoginResult {
  token: string;
  userId: number;
  username: string;
  role: string;
}

/** openapi UserInfo（/api/auth/me） */
interface MeUser {
  userId: number;
  username: string;
  realName: string;
  role: string;
}

const router = useRouter();
const username = ref("");
const password = ref("");
const loading = ref(false);

const submit = async () => {
  if (!username.value || !password.value) {
    ElMessage.warning("请输入账号和密码");
    return;
  }
  loading.value = true;
  try {
    const data = await api.post<LoginResult>("/api/auth/login", {
      username: username.value,
      password: password.value
    });
    localStorage.setItem("satoken", data.token);
    try {
      // 登录响应按 openapi 只含 userId/username/role；昵称从 me 补齐
      const me = await api.get<MeUser>("/api/auth/me");
      localStorage.setItem("zhixue_user", JSON.stringify(me));
    } catch {
      // /me 失败不阻断登录，用登录返回字段兜底
      localStorage.setItem(
        "zhixue_user",
        JSON.stringify({
          userId: data.userId,
          username: data.username,
          realName: data.username,
          role: data.role
        })
      );
    }
    await router.replace("/diagnosis");
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 55%, #ecfeff 100%);
}
.login-card {
  width: min(420px, calc(100vw - 32px));
}
.login-btn {
  width: 100%;
}
.login-hint {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
