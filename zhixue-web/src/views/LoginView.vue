<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-brand">
        <span class="brand-mark" aria-hidden="true">智</span>
        <div>
          <strong>智学领航</strong>
          <span>《数据库系统概论》课程学情诊断</span>
        </div>
      </div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号（学号 / 工号）">
          <el-input v-model="username" autocomplete="username" placeholder="请输入学号或工号" />
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
      <p class="login-notice">账号由课程教师统一分配，如无法登录请联系课程管理员。</p>
      <div v-if="isDev" class="login-hint">本地开发种子账号见 zhixue-server/README.md</div>
    </el-card>
    <p class="login-foot">智学领航 · 校级大学生创新创业训练计划项目 GSD2026225</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
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
const route = useRoute();
const username = ref("");
const password = ref("");
const loading = ref(false);
const isDev = import.meta.env.DEV;

/** 登录后回跳：仅接受站内相对路径，防止开放重定向；否则回角色首页。 */
const resolveRedirect = (role: string) => {
  const raw = route.query.redirect;
  const target = Array.isArray(raw) ? raw[0] : raw;
  if (typeof target === "string" && target.startsWith("/") && !target.startsWith("//")
      && !target.startsWith("/login")) {
    return target;
  }
  return role === "TEACHER" ? "/teacher/reviews" : "/today";
};

const submit = async () => {
  if (loading.value) return;
  const account = username.value.trim();
  if (!account || !password.value) {
    ElMessage.warning("请输入账号和密码");
    return;
  }
  loading.value = true;
  try {
    const data = await api.post<LoginResult>("/api/auth/login", {
      username: account,
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
    await router.replace(resolveRedirect(data.role));
  } catch (e) {
    ElMessage.error((e as Error).message || "登录失败");
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
  padding: 24px;
  background:
    radial-gradient(520px 280px at 12% 8%, color-mix(in srgb, var(--app-primary) 13%, transparent), transparent 65%),
    radial-gradient(460px 280px at 92% 92%, color-mix(in srgb, var(--app-success) 9%, transparent), transparent 65%),
    var(--app-bg);
}
.login-card {
  width: min(420px, calc(100vw - 32px));
  border-radius: var(--app-radius-xl);
  box-shadow: var(--app-shadow-lg);
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: var(--app-radius-md);
  display: grid;
  place-items: center;
  flex: none;
  color: var(--app-accent-contrast);
  font-size: var(--app-fs-lg);
  font-weight: 700;
  line-height: 1;
  background: linear-gradient(135deg, var(--app-primary), var(--app-primary-strong));
  box-shadow: var(--app-shadow-sm);
}
.login-brand strong {
  display: block;
  color: var(--app-text);
  font-size: var(--app-fs-lg);
  letter-spacing: -0.02em;
}
.login-brand div span {
  display: block;
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.login-btn {
  width: 100%;
  height: 40px;
}
.login-notice {
  margin: 14px 0 0;
  font-size: var(--app-fs-xs);
  line-height: 1.6;
  color: var(--app-text-muted);
  text-align: center;
}
.login-hint {
  margin-top: 8px;
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
  text-align: center;
}
.login-foot {
  position: relative;
  z-index: 1;
  margin: 18px 0 0;
  font-size: var(--app-fs-xs);
  letter-spacing: 0.02em;
  color: var(--app-text-muted);
  text-align: center;
}
</style>
