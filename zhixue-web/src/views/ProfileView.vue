<template>
  <div class="page">
    <div class="page-head">
      <div>
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

        <el-card v-if="isStudent" class="learning-card" shadow="never">
          <el-alert
            v-if="portraitError"
            type="warning"
            :title="portraitError"
            :closable="false"
            show-icon
            style="margin-bottom: var(--app-space-3)"
          />
          <div class="learning-main">
            <div class="learning-copy">
              <div class="card-head">
                <span>学习概览</span>
                <span class="card-sub">{{ portrait?.statDate || "暂无数据" }}</span>
              </div>
              <h3>{{ learningTitle }}</h3>
              <p>{{ learningSub }}</p>
              <div class="learning-actions">
                <el-button link type="primary" @click="router.push('/portrait')">查看画像</el-button>
                <el-button link type="primary" @click="router.push('/plan')">去周计划</el-button>
              </div>
            </div>
            <div class="learning-stats">
              <div><span>练习样本</span><strong>{{ portrait?.totalPractice ?? 0 }}</strong></div>
              <div><span>整体掌握度</span><strong>{{ masteryText }}</strong></div>
              <div><span>已确认错题</span><strong>{{ portrait?.totalError ?? 0 }}</strong></div>
            </div>
          </div>
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

        <el-card class="appearance-card" shadow="never">
          <template #header>
            <div class="card-head"><span>外观</span><span class="card-sub">浅色 / 深色 / 跟随系统</span></div>
          </template>
          <div class="appearance-list" role="radiogroup" aria-label="外观主题">
            <button
              v-for="option in THEME_OPTIONS"
              :key="option.key"
              type="button"
              role="radio"
              class="appearance-item"
              :class="{ 'is-active': pref === option.key }"
              :aria-checked="pref === option.key"
              @click="setPref(option.key)"
            >
              <ThemeIcon class="ap-icon" :name="option.key" :size="18" />
              <span class="ap-copy">
                <strong>{{ option.label }}</strong>
                <small>{{ option.description }}</small>
              </span>
              <svg
                v-if="pref === option.key"
                class="ap-check"
                viewBox="0 0 24 24"
                width="15"
                height="15"
                fill="none"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="m20 6-11 11-5-5" />
              </svg>
            </button>
          </div>
          <p class="appearance-note">
            选「跟随系统」时，界面会随设备外观自动切换；也可随时用右上角快捷切换。
          </p>
        </el-card>

        <el-card class="quick-card" shadow="never">
          <template #header>
            <div class="card-head"><span>快捷入口</span><span class="card-sub">按角色</span></div>
          </template>
          <div class="quick-links">
            <template v-if="profile.role === 'TEACHER'">
              <el-button class="quick-link" @click="router.push('/teacher')">
                <span class="ql-no">01</span>
                <span class="ql-copy"><strong>教学概览</strong><span>本课程最该讲的知识点</span></span>
              </el-button>
              <el-button class="quick-link" @click="router.push('/teacher/reviews')">
                <span class="ql-no">02</span>
                <span class="ql-copy"><strong>教学评阅</strong><span>抽样评阅 AI 讲解</span></span>
              </el-button>
              <el-button class="quick-link" @click="router.push('/teacher/class')">
                <span class="ql-no">03</span>
                <span class="ql-copy"><strong>班级学情</strong><span>学生练习与掌握度</span></span>
              </el-button>
            </template>
            <template v-else>
              <el-button class="quick-link" @click="router.push('/today')">
                <span class="ql-no">01</span>
                <span class="ql-copy"><strong>今日工作台</strong><span>先做最值得的一件事</span></span>
              </el-button>
              <el-button class="quick-link" @click="router.push('/portrait')">
                <span class="ql-no">02</span>
                <span class="ql-copy"><strong>学情画像</strong><span>掌握度与薄弱知识点</span></span>
              </el-button>
              <el-button class="quick-link" @click="router.push('/plan')">
                <span class="ql-no">03</span>
                <span class="ql-copy"><strong>周计划</strong><span>按天完成练习与打卡</span></span>
              </el-button>
            </template>
          </div>
          <p class="quick-note">按角色提供最短路径；更多入口见左侧导航。</p>
        </el-card>
      </div>
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PageState from "../components/PageState.vue";
import ThemeIcon from "../components/ThemeIcon.vue";
import { THEME_OPTIONS, useTheme } from "../composables/useTheme";
import { api } from "../api/request";
import { clearSession, getUser, saveUser } from "../utils/authSession";
import { getPortrait, type Portrait } from "../api/portrait";
import { reportError } from "../utils/telemetry";
import { pct } from "../utils/format";
import type { MeUser } from "../types/me";

const router = useRouter();
/** 外观设置与顶部切换器共用同一份偏好状态（useTheme 为模块级单例） */
const { pref, setPref } = useTheme();
const loading = ref(false);
const error = ref("");
const portraitError = ref("");
const profile = ref<MeUser>({ userId: 0, username: "", realName: "", role: "" });
const portrait = ref<Portrait | null>(null);

const initial = computed(() => (profile.value.realName || profile.value.username || "访").slice(0, 1));
const isStudent = computed(() => profile.value.role === "STUDENT");
const overallMastery = computed(() => {
  const values = Object.values(portrait.value?.dimensionScores ?? {}).filter(
    (value): value is number => typeof value === "number"
  );
  if (!values.length) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
});
const masteryText = computed(() => pct(overallMastery.value));
const weakest = computed(() => portrait.value?.weakestKps?.[0] ?? null);
const learningTitle = computed(() =>
  weakest.value
    ? `最该补：${weakest.value.name || weakest.value.kpId || "暂无"}`
    : "还没有足够的学习数据"
);
const learningSub = computed(() => {
  if (!portrait.value) return "完成一次错题诊断并确认后，这里会出现你的学习概览。";
  if (!weakest.value) return "已完成练习，但样本还不足以排出薄弱点；继续完成诊断与计划。";
  return `画像数据（截至 ${portrait.value.statDate || "—"}）· 训练样本 ${portrait.value.totalPractice ?? 0} 次 · 已确认错题 ${portrait.value.totalError ?? 0} 条`;
});
const roleLabel = computed(() => {
  if (profile.value.role === "TEACHER") return "教师";
  if (profile.value.role === "STUDENT") return "学生";
  return "未分配";
});

const cacheProfile = () => {
  const cached = getUser();
  // 缓存同时给 userId/username/realName/role，缺字段的旧缓存不直接渲染
  if (cached && cached.role && cached.userId) profile.value = cached as MeUser;
};

let loadSeq = 0;
const load = async () => {
  const seq = ++loadSeq;
  loading.value = true;
  error.value = "";
  portraitError.value = "";
  try {
    // 页面用 PageState 内联呈现错误，silent 避免 toast 与内联重复
    const me = await api.get<MeUser>("/api/auth/me", undefined, { silent: true });
    if (seq !== loadSeq) return;
    profile.value = me;
    saveUser(me as MeUser);
    if (me.role === "STUDENT") {
      try {
        // 学习概览卡有独立 el-alert 内联位，首屏失败不再叠加全局 toast
        const data = await getPortrait({ silent: true });
        if (seq !== loadSeq) return;
        portrait.value = data;
      } catch (e) {
        if (seq !== loadSeq) return;
        portrait.value = null;
        reportError(e, "profile.loadPortrait");
        portraitError.value = (e as Error).message || "学习概览加载失败，可稍后重试";
      }
    } else {
      portrait.value = null;
    }
  } catch (e) {
    if (seq !== loadSeq) return;
    error.value = (e as Error).message || "账号信息加载失败";
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
};

const goHome = () => {
  router.push(profile.value.role === "TEACHER" ? "/teacher" : "/today");
};

const logout = async () => {
  try {
    await api.post("/api/auth/logout");
  } catch {
    // 登出失败也要清本地态
  } finally {
    clearSession();
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
  max-width: 1080px;
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
/* C 阅读承载面：账号 / 学习 / 安全 / 快捷入口为近实底 + 发丝边，外层大容器 24px */
.el-card.identity,
.el-card.learning-card,
.el-card.security-card,
.el-card.quick-card {
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-2xl);
  background: color-mix(in srgb, var(--app-surface) 97%, transparent);
  box-shadow: var(--app-shadow-sm);
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
  /* 与顶栏 .avatar 同源问题：浅色渐变顶色标上白字不足 4.5:1；
     描边用与主色混合的深色（双主题都为暗色），保证白字在整段渐变上都可读 */
  -webkit-text-stroke: 0.6px color-mix(in srgb, var(--app-primary-strong) 70%, black);
  text-shadow: 0 0 1px color-mix(in srgb, var(--app-primary-strong) 70%, black);
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
  font-weight: 600;
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
  border-bottom: 1px solid var(--app-hairline);
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
.learning-card {
  grid-column: 1 / -1;
}
.learning-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--app-space-6);
}
.learning-copy h3 {
  margin: var(--app-space-2) 0;
  font-size: var(--app-fs-xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--app-text);
}
.learning-copy p {
  margin: 0 0 var(--app-space-2);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
  line-height: 1.6;
}
.learning-actions {
  display: flex;
  gap: var(--app-space-2);
  flex-wrap: wrap;
}
.learning-stats {
  display: flex;
  gap: var(--app-space-3);
}
/* 子卡 18px：学习概览的三块数字各自成面，避免在大卡里"飘" */
.learning-stats > div {
  padding: 8px 12px;
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-inner);
  background: var(--app-surface-muted);
}
.learning-stats span {
  display: block;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  white-space: nowrap;
}
.learning-stats strong {
  display: block;
  margin-top: 4px;
  color: var(--app-text);
  font-size: var(--app-fs-xl);
  font-weight: 600;
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
/* 外观：三档卡片选择器（桌面三列 / 移动端单列），与顶部切换器共享同一份偏好状态 */
/* B 焦点玻璃卡（本屏仅此 1 张）：半透明磨砂观感 + 顶部高光边 + 同心圆角。
   .content 已有一层 backdrop-filter，卡内不再叠加模糊，只靠半透明实底透出环境光。 */
.el-card.appearance-card {
  grid-column: 1 / -1;
  border: 1px solid color-mix(in srgb, var(--app-primary) 18%, var(--app-hairline));
  border-radius: var(--app-radius-2xl);
  background:
    linear-gradient(180deg, var(--app-glass-edge-hi), transparent 46%),
    color-mix(in srgb, var(--app-surface-raised) 84%, transparent);
  box-shadow: var(--app-shadow-md), inset 0 1px 0 var(--app-glass-edge-hi);
}
.appearance-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--app-space-3);
}
.appearance-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  padding: 12px 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-inner);
  background: color-mix(in srgb, var(--app-surface) 82%, transparent);
  color: var(--app-text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform var(--app-dur-fast) var(--app-ease-spring),
    border-color var(--app-dur-fast) var(--app-ease-standard),
    background-color var(--app-dur-fast) var(--app-ease-standard),
    box-shadow var(--app-dur-fast) var(--app-ease-standard);
}
.appearance-item:hover {
  border-color: color-mix(in srgb, var(--app-primary) 40%, var(--app-border));
  color: var(--app-text);
  box-shadow: var(--app-shadow-sm);
}
.appearance-item:active {
  transform: scale(0.97);
}
.appearance-item:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.appearance-item.is-active {
  border-color: color-mix(in srgb, var(--app-primary) 46%, var(--app-border));
  /* 用 primary-soft 而非 5% color-mix：深色下 5% 蓝叠 #121a28 几乎看不出选中底，
     而 primary-soft 已是双主题标定好的值（浅 10% / 深 16%） */
  background: var(--app-primary-soft);
  color: var(--app-primary);
  box-shadow: inset 0 1px 0 var(--app-glass-edge-hi);
}
.ap-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.ap-copy strong {
  color: var(--app-text);
  font-size: var(--app-fs-sm);
  font-weight: 600;
}
.ap-copy small {
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  white-space: nowrap;
  text-overflow: ellipsis;
}
.ap-check {
  flex: none;
  color: var(--app-primary);
}
.appearance-note {
  margin: 12px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.quick-card {
  grid-column: 1 / -1;
}
.quick-links {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--app-space-3);
}
html[data-theme] .quick-link {
  height: auto;
  min-height: 76px;
  padding: var(--app-space-3) var(--app-space-4);
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--app-space-3);
  text-align: left;
  white-space: normal;
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-inner);
  background: var(--app-surface-muted);
  color: var(--app-text);
  transition: transform var(--app-dur-fast) var(--app-ease-spring),
    background-color var(--app-dur-fast) var(--app-ease-standard),
    border-color var(--app-dur-fast) var(--app-ease-standard),
    box-shadow var(--app-dur-fast) var(--app-ease-standard);
}
html[data-theme] .quick-link:hover {
  border-color: color-mix(in srgb, var(--app-primary) 40%, var(--app-border));
  background: color-mix(in srgb, var(--app-primary) 5%, var(--app-surface));
  box-shadow: var(--app-shadow-sm);
}
/* 提一级特异性，压过 glass.css 的 .el-button 按压 0.985，统一为 0.97 */
html[data-theme] .quick-links .quick-link.el-button:active {
  transform: scale(0.97);
}
.quick-link .ql-no {
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 50%;
  border: 1px solid var(--app-border-strong);
  display: grid;
  place-items: center;
  color: var(--app-primary);
  font-size: var(--app-fs-xs);
  font-variant-numeric: tabular-nums;
}
.quick-link .ql-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.quick-link .ql-copy strong {
  font-size: var(--app-fs-sm);
  font-weight: 600;
  color: var(--app-text);
}
.quick-link .ql-copy span {
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
  line-height: 1.5;
}
.quick-note {
  margin: 12px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
@media (hover: hover) and (pointer: fine) {
  .appearance-item:hover,
  html[data-theme] .quick-link:hover {
    transform: translateY(-1px);
  }
}
@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
  /* 手机：B 卡退化为半透明实底，文字对比度优先 */
  .el-card.appearance-card {
    background: color-mix(in srgb, var(--app-surface) 96%, transparent);
  }
  .appearance-list {
    grid-template-columns: 1fr;
  }
  .learning-main {
    grid-template-columns: 1fr;
    gap: var(--app-space-4);
  }
  .learning-stats {
    gap: var(--app-space-2);
    justify-content: space-between;
  }
  .learning-stats > div {
    padding: 8px 10px;
  }
  .quick-links {
    grid-template-columns: 1fr;
  }
}
</style>
