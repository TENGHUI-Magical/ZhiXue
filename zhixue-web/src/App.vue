<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="$route.path === '/login'" />

    <el-container v-else class="layout">
      <div class="app-wall" aria-hidden="true">
        <i></i><i></i><i></i><i></i><i></i>
      </div>
      <el-aside width="232px" class="aside">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">智</span>
          <span class="brand-text">智学领航</span>
        </div>
        <el-menu :default-active="$route.path" router class="menu">
          <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
            <component :is="item.icon" class="menu-icon" :size="18" aria-hidden="true" />
            {{ item.label }}
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container class="content" direction="vertical">
        <el-header class="header">
          <span class="header-title">
            <el-button
              class="nav-toggle"
              text
              circle
              aria-label="打开导航菜单"
              @click="navOpen = true"
            >
              <span class="nav-icon" aria-hidden="true">
                <Menu :size="20" />
              </span>
            </el-button>
            <span class="title">{{ $route.meta.title }}</span>
          </span>
          <span class="user">
            <ThemeSwitcher />
            <div ref="userRoot" class="user-switch">
              <button
                type="button"
                class="user-trigger"
                :aria-expanded="userMenuOpen"
                aria-haspopup="menu"
                @click="userMenuOpen = !userMenuOpen"
              >
                <span class="avatar" aria-hidden="true">{{ avatarText }}</span>
                <span class="user-meta">
                  <b>{{ userName }}</b>
                  <small>{{ roleLabel }}</small>
                </span>
                <svg class="caret" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
                  <path d="m6 9 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2"
                        stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
              <div v-if="userMenuOpen" class="user-menu" role="menu">
                <div class="user-menu-head">
                  <strong>{{ userName }}</strong>
                  <span>{{ roleLabel }}<template v-if="userAccount"> · {{ userAccount }}</template></span>
                </div>
                <button type="button" role="menuitem" class="user-menu-item" @click="goProfile">
                  个人中心
                </button>
                <button type="button" role="menuitem" class="user-menu-item danger" @click="logout">
                  退出登录
                </button>
              </div>
            </div>
          </span>
        </el-header>
        <el-main class="main">
          <router-view />
        </el-main>
      </el-container>

      <el-drawer v-model="navOpen" direction="ltr" size="240px" :with-header="false" class="nav-drawer">
        <div class="brand drawer-logo">
          <span class="brand-mark" aria-hidden="true">智</span>
          <span class="brand-text">智学领航</span>
        </div>
        <el-menu
          :default-active="$route.path"
          router
          class="menu"
          @select="navOpen = false"
        >
          <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
            <component :is="item.icon" class="menu-icon" :size="18" aria-hidden="true" />
            {{ item.label }}
          </el-menu-item>
        </el-menu>
      </el-drawer>
    </el-container>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { BookOpen, CalendarCheck, ClipboardCheck, Menu, Network, Radar, Stethoscope } from "@lucide/vue";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { api } from "./api/request";
import ThemeSwitcher from "./components/ThemeSwitcher.vue";

const router = useRouter();
const route = useRoute();
const userName = ref("未登录");
const userAccount = ref("");
const userRole = ref("");
/** 移动端抽屉导航开关；桌面端由左侧栏常驻，不显示入口 */
const navOpen = ref(false);
const userMenuOpen = ref(false);
const userRoot = ref<HTMLElement | null>(null);

const baseMenus = [
  { path: "/diagnosis", label: "错题诊断工作台", icon: Stethoscope },
  { path: "/error-book", label: "我的错题本", icon: BookOpen },
  { path: "/portrait", label: "学情画像看板", icon: Radar },
  { path: "/plan", label: "周计划与打卡", icon: CalendarCheck },
  { path: "/knowledge-graph", label: "课程知识图谱", icon: Network }
];

const menus = computed(() =>
  userRole.value === "TEACHER"
    ? [...baseMenus, { path: "/teacher/reviews", label: "教师评阅", icon: ClipboardCheck }]
    : baseMenus
);

const roleLabel = computed(() => {
  if (userRole.value === "TEACHER") return "教师";
  if (userRole.value === "STUDENT") return "学生";
  return "访客";
});

const avatarText = computed(() => (userName.value || "访").slice(0, 1));

interface MeUser {
  userId: number;
  username: string;
  realName: string;
  role: string;
}

const applyUser = (user: MeUser) => {
  userName.value = user.realName || user.username;
  userAccount.value = user.username || "";
  userRole.value = user.role || "";
};

/** 路由变化时刷新用户名；本地有 token 但缺 user 信息时回查 /api/auth/me */
const refreshUser = async () => {
  if (!localStorage.getItem("satoken")) {
    userName.value = "未登录";
    userAccount.value = "";
    userRole.value = "";
    return;
  }
  const raw = localStorage.getItem("zhixue_user");
  if (raw) {
    try {
      applyUser(JSON.parse(raw) as MeUser);
      return;
    } catch {
      // 本地缓存坏了，走接口
    }
  }
  try {
    const me = await api.get<MeUser>("/api/auth/me");
    localStorage.setItem("zhixue_user", JSON.stringify(me));
    applyUser(me);
  } catch {
    userName.value = "未登录";
    userAccount.value = "";
    userRole.value = "";
  }
};

watch(() => route.path, refreshUser, { immediate: true });
watch(() => route.path, () => (userMenuOpen.value = false));

const goProfile = () => {
  userMenuOpen.value = false;
  router.push("/profile");
};

const logout = async () => {
  userMenuOpen.value = false;
  try {
    await api.post("/api/auth/logout");
  } catch {
    // 登出失败也要清本地态
  } finally {
    localStorage.removeItem("satoken");
    localStorage.removeItem("zhixue_user");
    userName.value = "未登录";
    userAccount.value = "";
    userRole.value = "";
    await router.replace("/login");
  }
};

const onDocClick = (event: MouseEvent) => {
  if (userMenuOpen.value && userRoot.value && !userRoot.value.contains(event.target as Node)) {
    userMenuOpen.value = false;
  }
};
const onKeydown = (event: KeyboardEvent) => {
  if (event.key === "Escape") userMenuOpen.value = false;
};

onMounted(() => {
  document.addEventListener("click", onDocClick);
  document.addEventListener("keydown", onKeydown);
});
onBeforeUnmount(() => {
  document.removeEventListener("click", onDocClick);
  document.removeEventListener("keydown", onKeydown);
});
</script>

<style scoped>
.layout {
  height: 100vh;
  height: 100dvh;
  background: var(--app-bg);
}
.aside {
  background: var(--app-sidebar);
  border-right: 1px solid var(--app-border);
  padding: 14px 10px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px 18px;
}
.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex: none;
  color: var(--app-accent-contrast);
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  background: linear-gradient(135deg, var(--app-primary), var(--app-primary-strong));
  box-shadow: var(--app-shadow-sm);
}
.brand-text {
  color: var(--app-text);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.menu {
  border-right: none;
  background: transparent;
  padding: 0 4px;
}
.menu-icon {
  margin-right: 10px;
  flex: none;
}
:deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 3px 0;
  border-radius: var(--app-radius-sm);
  color: var(--app-text-secondary);
  font-weight: 500;
  transition: background-color var(--app-dur-fast) var(--app-ease-standard),
    color var(--app-dur-fast) var(--app-ease-standard);
}
:deep(.el-menu-item:hover) {
  background: var(--app-surface-muted);
  color: var(--app-text);
}
:deep(.el-menu-item.is-active) {
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-weight: 600;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 56px;
  padding: 0 24px;
  border-bottom: 1px solid var(--app-border);
  background: var(--app-header);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 20;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 600;
}
.nav-toggle {
  display: none;
  flex-shrink: 0;
}
.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
.user {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-switch {
  position: relative;
}
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 8px 0 4px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--app-text);
  font: inherit;
  cursor: pointer;
  transition: background-color var(--app-dur-fast) var(--app-ease-standard),
    border-color var(--app-dur-fast) var(--app-ease-standard);
}
.user-trigger:hover,
.user-trigger[aria-expanded="true"] {
  border-color: var(--app-border);
  background: var(--app-surface);
}
.user-trigger:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.avatar {
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--app-primary) 78%, white),
    var(--app-primary-strong)
  );
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.32);
}
.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
  min-width: 0;
}
.user-meta b {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
}
.user-meta small {
  color: var(--app-text-muted);
  font-size: 10.5px;
}
.caret {
  flex: none;
  opacity: 0.6;
}
.user-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  min-width: 200px;
  padding: 6px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: 12px;
  box-shadow: var(--app-shadow-lg);
}
.user-menu-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px 10px;
  border-bottom: 1px solid var(--app-divider);
  margin-bottom: 4px;
}
.user-menu-head strong {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--app-text);
}
.user-menu-head span {
  font-size: 11px;
  color: var(--app-text-muted);
}
.user-menu-item {
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text);
  font: inherit;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}
.user-menu-item:hover {
  background: var(--app-surface-muted);
}
.user-menu-item.danger {
  color: var(--app-danger);
}
.user-menu-item:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: -2px;
}
.main {
  background: var(--app-bg);
  padding: 24px;
  overflow-y: auto;
}
.drawer-logo {
  padding: 8px 10px 16px;
}

@media (max-width: 768px) {
  .aside {
    display: none;
  }
  .nav-toggle {
    display: inline-flex;
  }
  .header {
    padding: 0 12px;
  }
  .user {
    gap: 6px;
  }
  .user-meta,
  .caret {
    display: none;
  }
  .user-trigger {
    padding: 0 2px;
  }
  .main {
    padding: 12px;
  }
}
</style>
