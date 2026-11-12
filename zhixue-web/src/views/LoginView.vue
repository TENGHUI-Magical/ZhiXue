<template>
  <div class="login-page">
    <BrandMark :size="380" tone="mono" class="login-watermark" />
    <div class="login-shell">
      <section class="login-story">
        <div class="story-brand">
          <BrandMark :size="40" />
          <div>
            <strong>智学领航</strong>
            <span>《数据库系统概论》课程学情诊断</span>
          </div>
        </div>
        <h1 class="story-title">让每一次错题，<br />都变成下一步该做的事。</h1>
        <p class="story-sub">
          从错题诊断到知识点定位、周计划与教师评阅，学与教都有证据可循。
        </p>
        <ul class="story-points">
          <li>
            <span class="point-no">01</span>
            <div>
              <strong>定位到知识点</strong>
              <p>拍照或粘贴题干，匹配课程知识点并生成有出处的讲解。</p>
            </div>
          </li>
          <li>
            <span class="point-no">02</span>
            <div>
              <strong>看到下一步</strong>
              <p>画像、周计划与练习推荐，把薄弱点变成今天的行动。</p>
            </div>
          </li>
          <li>
            <span class="point-no">03</span>
            <div>
              <strong>留下评阅证据</strong>
              <p>教师抽样评阅 AI 讲解，结论回写讲解质量与后续教学。</p>
            </div>
          </li>
        </ul>
      </section>

      <el-card class="login-card">
        <div class="login-head">
          <h2>登录</h2>
          <p>使用课程账号继续</p>
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
    </div>
    <p class="login-foot">智学领航 · 校级大学生创新创业训练计划项目 GSD2026225</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import BrandMark from "../components/BrandMark.vue";
import { api } from "../api/request";
import { saveSession, saveUser } from "../utils/authSession";

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
  return role === "TEACHER" ? "/teacher" : "/today";
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
    const data = await api.post<LoginResult>(
      "/api/auth/login",
      { username: account, password: password.value },
      // 40100（账号或密码错误）由本页 catch 内联提示，请求层不再重复 toast
      { silent: true }
    );
    saveSession(data.token, null);
    try {
      // 登录响应按 openapi 只含 userId/username/role；昵称从 me 补齐
      const me = await api.get<MeUser>("/api/auth/me", undefined, { silent: true });
      saveUser({
        userId: me.userId ?? data.userId,
        username: me.username ?? data.username ?? account,
        realName: me.realName ?? data.username ?? account,
        role: me.role ?? data.role ?? ""
      });
    } catch {
      // /me 失败不阻断登录，用登录返回字段兜底
      saveUser({
        userId: data.userId,
        username: data.username ?? account,
        realName: data.username ?? account,
        role: data.role ?? ""
      });
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
/* 水印贴边但不产生横向滚动 */
.login-page {
  overflow: hidden;
}
/* 环境光斑：只加一层低饱和主色气团，不压到登录卡文字与错误提示之上 */
.login-page::after {
  content: "";
  position: absolute;
  top: -14%;
  right: -8%;
  z-index: 0;
  width: min(620px, 78vw);
  height: min(620px, 78vw);
  border-radius: 50%;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--app-primary) 16%, transparent),
    transparent 64%
  );
  pointer-events: none;
}
.login-shell {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  align-items: center;
  gap: clamp(32px, 6vw, 88px);
}
.login-story {
  max-width: 560px;
}
.story-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 40px;
}
.login-watermark {
  position: absolute;
  right: -6%;
  bottom: -6%;
  z-index: 0;
  color: var(--app-primary);
  opacity: 0.045;
  pointer-events: none;
}
.story-brand strong {
  display: block;
  color: var(--app-text);
  font-size: var(--app-fs-lg);
  letter-spacing: -0.02em;
}
.story-brand div span {
  display: block;
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.story-title {
  margin: 0 0 18px;
  color: var(--app-text);
  font-size: clamp(30px, 3.4vw, 44px);
  font-weight: 600;
  letter-spacing: -0.03em;
  line-height: 1.16;
}
.story-sub {
  max-width: 480px;
  margin: 0 0 30px;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-md);
  line-height: 1.8;
}
.story-points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--app-space-4);
}
.story-points li {
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: 12px;
  align-items: start;
}
.point-no {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid var(--app-border-strong);
  background: var(--app-surface);
  display: grid;
  place-items: center;
  color: var(--app-primary);
  font-size: var(--app-fs-xs);
  font-variant-numeric: tabular-nums;
}
.story-points strong {
  display: block;
  color: var(--app-text);
  font-size: var(--app-fs-sm);
}
.story-points p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.65;
}
/* B 层焦点玻璃卡：半透明磨砂 + 顶部高光 + inset 1px 高光边 + 24px 圆角。
   模糊由 glass.css 全局 .login-card 提供，这里不再叠 backdrop-filter（禁止嵌套模糊）。 */
.login-shell .login-card {
  width: 420px;
  max-width: 100%;
  border: 1px solid var(--app-glass-edge);
  border-radius: var(--app-radius-2xl);
  background:
    linear-gradient(180deg, var(--app-glass-edge-hi), transparent 46%),
    var(--app-glass-fill);
  box-shadow:
    inset 0 1px 0 var(--app-glass-hi),
    var(--app-shadow-lg);
  transition: box-shadow var(--app-dur-slow) var(--app-ease-spring),
    border-color var(--app-dur-base) var(--app-ease-standard);
}
.login-shell .login-card :deep(.el-card__body) {
  padding: var(--app-space-6);
}
.login-head h2 {
  margin: 0;
  color: var(--app-text);
  font-size: var(--app-fs-xl);
  letter-spacing: -0.01em;
}
.login-head p {
  margin: 6px 0 24px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.login-shell .login-btn {
  width: 100%;
  height: 44px;
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
  margin: 28px 0 0;
  font-size: var(--app-fs-xs);
  letter-spacing: 0.02em;
  color: var(--app-text-muted);
  text-align: center;
}
@media (max-width: 1024px) {
  .login-shell {
    width: min(820px, 100%);
    grid-template-columns: minmax(0, 1fr) 380px;
    gap: 40px;
  }
  .story-title {
    font-size: var(--app-fs-hero);
  }
  .login-shell .login-card {
    width: 380px;
  }
}
@media (max-width: 768px) {
  .login-shell {
    width: 100%;
    grid-template-columns: 1fr;
    gap: var(--app-space-6);
  }
  .login-story {
    max-width: none;
  }
  .story-brand {
    margin-bottom: 22px;
  }
  .story-title {
    margin-bottom: 12px;
    font-size: var(--app-fs-xl);
  }
  .story-sub {
    margin-bottom: 0;
    font-size: var(--app-fs-sm);
  }
  .story-points {
    display: none;
  }
  .login-shell .login-card {
    width: 100%;
  }
  .login-shell .login-card :deep(.el-card__body) {
    padding: 18px;
  }
  /* 手机输入字号 ≥16px：避免 iOS 聚焦自动放大 */
  .login-shell :deep(.el-input__inner) {
    font-size: 16px;
  }
  .login-shell :deep(.el-input__wrapper) {
    min-height: 44px;
  }
  .login-foot {
    margin-top: 20px;
  }
}
</style>
