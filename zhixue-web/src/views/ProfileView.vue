<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">ACCOUNT</div>
        <h2>个人中心</h2>
        <p class="sub">账号身份与登录信息来自统一认证，仅供本人查看。</p>
      </div>
    </div>

    <PageState :loading="loading" :error="error" :empty="false" @retry="load">
      <div class="profile-grid">
        <el-card class="identity" shadow="never">
          <div class="avatar-lg" aria-hidden="true">{{ initial }}</div>
          <h3>{{ profile.realName || profile.username || "未登录" }}</h3>
          <p class="identity-sub">{{ roleLabel }}<template v-if="profile.username"> · {{ profile.username }}</template></p>
          <el-tag type="success" size="small">已登录</el-tag>
          <div class="identity-actions">
            <el-button type="primary" @click="goHome">进入工作台</el-button>
            <el-button @click="logout">退出登录</el-button>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="card-head"><span>账号信息</span><span class="card-sub">只读</span></div>
          </template>
          <dl class="info-list">
            <div class="info-row">
              <dt>姓名</dt>
              <dd>{{ profile.realName || "—" }}</dd>
            </div>
            <div class="info-row">
              <dt>账号</dt>
              <dd>{{ profile.username || "—" }}</dd>
            </div>
            <div class="info-row">
              <dt>身份</dt>
              <dd>{{ roleLabel }}</dd>
            </div>
            <div class="info-row">
              <dt>用户编号</dt>
              <dd>{{ profile.userId ?? "—" }}</dd>
            </div>
            <div class="info-row">
              <dt>登录状态</dt>
              <dd><el-tag type="success" size="small">已登录</el-tag></dd>
            </div>
          </dl>
        </el-card>

        <el-card class="security-card" shadow="never">
          <template #header>
            <div class="card-head"><span>账号与安全</span><span class="card-sub">统一认证</span></div>
          </template>
          <p>
            账号由课程教师统一分配，姓名与身份信息以教务数据为准。如需重置密码或变更身份，
            请联系课程管理员处理。
          </p>
          <p class="security-note">
            请勿向他人转借账号；在公共设备使用后，请及时退出登录。
          </p>
        </el-card>
      </div>
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PageState from "../components/PageState.vue";
import { api } from "../api/request";

interface MeUser {
  userId: number;
  username: string;
  realName: string;
  role: string;
}

const router = useRouter();
const loading = ref(false);
const error = ref("");
const profile = ref<MeUser>({ userId: 0, username: "", realName: "", role: "" });

const initial = computed(() => (profile.value.realName || profile.value.username || "访").slice(0, 1));
const roleLabel = computed(() => {
  if (profile.value.role === "TEACHER") return "教师";
  if (profile.value.role === "STUDENT") return "学生";
  return "未分配";
});

const cacheProfile = () => {
  const raw = localStorage.getItem("zhixue_user");
  if (!raw) return;
  try {
    profile.value = JSON.parse(raw) as MeUser;
  } catch {
    // 缓存损坏时等待接口返回
  }
};

const load = async () => {
  loading.value = true;
  error.value = "";
  try {
    const me = await api.get<MeUser>("/api/auth/me");
    profile.value = me;
    localStorage.setItem("zhixue_user", JSON.stringify(me));
  } catch (e) {
    error.value = (e as Error).message || "账号信息加载失败";
  } finally {
    loading.value = false;
  }
};

const goHome = () => {
  router.push(profile.value.role === "TEACHER" ? "/teacher/reviews" : "/diagnosis");
};

const logout = async () => {
  try {
    await api.post("/api/auth/logout");
  } catch {
    // 登出失败也要清本地态
  } finally {
    localStorage.removeItem("satoken");
    localStorage.removeItem("zhixue_user");
    await router.replace("/login");
  }
};

onMounted(() => {
  cacheProfile();
  load();
});
</script>

<style scoped>
.page {
  max-width: 960px;
}
.eyebrow {
  font-size: var(--app-fs-xs);
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--app-text-muted);
}
.page-head h2 {
  margin: 6px 0 3px;
  font-size: var(--app-fs-xl);
  letter-spacing: -0.012em;
}
.sub {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.profile-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}
.identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 10px 0;
}
.avatar-lg {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: var(--app-fs-xl);
  font-weight: 600;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--app-primary) 78%, white),
    var(--app-primary-strong)
  );
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.32),
    0 8px 20px -8px color-mix(in srgb, var(--app-primary) 45%, transparent);
}
.identity h3 {
  margin: 14px 0 4px;
  font-size: var(--app-fs-lg);
  font-weight: 650;
  color: var(--app-text);
}
.identity-sub {
  margin: 0 0 10px;
  font-size: var(--app-fs-sm);
  color: var(--app-text-muted);
}
.identity-actions {
  display: flex;
  gap: 8px;
  margin-top: 18px;
  flex-wrap: wrap;
  justify-content: center;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.card-sub {
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
  font-weight: 400;
}
.info-list {
  margin: 0;
}
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 0;
  border-bottom: 0.5px solid var(--app-border);
}
.info-row:last-child {
  border-bottom: none;
}
.info-row dt {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.info-row dd {
  margin: 0;
  color: var(--app-text);
  font-size: var(--app-fs-sm);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.security-card {
  grid-column: 1 / -1;
}
.security-card p {
  margin: 0;
  font-size: var(--app-fs-sm);
  line-height: 1.75;
  color: var(--app-text-secondary);
}
.security-card .security-note {
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
@media (max-width: 760px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
