<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="$route.path === '/login'" />

    <el-container v-else class="layout">
      <a class="skip-link" href="#main-content">跳到主要内容</a>
      <div class="app-wall" aria-hidden="true">
        <i></i><i></i><i></i><i></i><i></i>
      </div>
      <el-aside
        width="232px"
        class="aside"
        :class="{ 'is-collapsed': navCollapsed }"
        aria-label="主导航"
      >
        <div class="brand">
          <div class="brand-id">
            <BrandMark :size="34" />
            <span class="brand-text">智学领航</span>
          </div>
          <button
            type="button"
            class="side-toggle"
            :aria-label="navCollapsed ? '展开侧栏' : '收起侧栏'"
            :aria-expanded="!navCollapsed"
            :title="navCollapsed ? '展开侧栏' : '收起侧栏'"
            @click="toggleNav"
          >
            <PanelLeftOpen v-if="navCollapsed" :size="16" aria-hidden="true" />
            <PanelLeftClose v-else :size="16" aria-hidden="true" />
          </button>
        </div>
        <div class="menu-caption">{{ userRole === "TEACHER" ? "教学" : "学习" }}</div>
        <el-menu :default-active="$route.path" router class="menu">
          <el-menu-item
            v-for="item in menus"
            :key="item.path"
            :index="item.path"
            :title="navCollapsed ? item.label : undefined"
            :aria-label="navCollapsed ? item.label : undefined"
          >
            <component :is="item.icon" class="menu-icon" :size="18" aria-hidden="true" />
            <span class="menu-label">{{ item.label }}</span>
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
            <span v-if="crumbGroup" class="crumb-group">{{ crumbGroup }}</span>
            <ChevronRight v-if="crumbGroup" class="crumb-sep" :size="12" aria-hidden="true" />
            <span class="title">{{ $route.meta.title }}</span>
          </span>
          <span class="user">
            <ThemeSwitcher />
            <div ref="userRoot" class="user-switch">
              <button
                type="button"
                class="user-trigger"
                aria-label="打开用户菜单"
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
        <el-main class="main" id="main-content" tabindex="-1">
          <router-view />
        </el-main>
      </el-container>

      <el-drawer v-model="navOpen" direction="ltr" size="240px" :with-header="false" class="nav-drawer" append-to-body>
        <div class="brand drawer-logo">
          <BrandMark :size="34" />
          <span class="brand-text">智学领航</span>
        </div>
        <div class="menu-caption">{{ userRole === "TEACHER" ? "教学" : "学习" }}</div>
        <el-menu
          :default-active="$route.path"
          router
          class="menu"
          @select="navOpen = false"
        >
          <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
            <component :is="item.icon" class="menu-icon" :size="18" aria-hidden="true" />
            <span class="menu-label">{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </el-drawer>
    </el-container>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { BookOpen, CalendarCheck, ChevronRight, ClipboardCheck, LayoutDashboard, Menu, Network, PanelLeftClose, PanelLeftOpen, Radar, Search, Stethoscope, Users } from "@lucide/vue";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { api } from "./api/request";
import ThemeSwitcher from "./components/ThemeSwitcher.vue";
import BrandMark from "./components/BrandMark.vue";

const router = useRouter();
const route = useRoute();
/** 顶栏面包屑：用路由 roles 推导模块归属，把"顶栏标题 = 页内 h2"的重复读成层级（V1.1 §3.10） */
const crumbGroup = computed(() => {
  const roles = route.meta.roles as string[] | undefined;
  if (!roles) return route.path === "/profile" ? "账号" : "";
  return roles.includes("TEACHER") ? "教学" : "学习";
});
const userName = ref("未登录");
const userAccount = ref("");
const userRole = ref("");
/** 移动端抽屉导航开关；桌面端由左侧栏常驻，不显示入口 */
const navOpen = ref(false);
const userMenuOpen = ref(false);
const userRoot = ref<HTMLElement | null>(null);
const NAV_STORAGE_KEY = "zhixue-nav-collapsed";
const readNavCollapsed = () => {
  try {
    return localStorage.getItem(NAV_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
};
const navCollapsed = ref(readNavCollapsed());

const toggleNav = () => {
  navCollapsed.value = !navCollapsed.value;
  try {
    localStorage.setItem(NAV_STORAGE_KEY, navCollapsed.value ? "1" : "0");
  } catch {
    // 隐私模式下只对当前会话生效
  }
};

const baseMenus = [
  { path: "/today", label: "今日工作台", icon: LayoutDashboard },
  { path: "/diagnosis", label: "错题诊断工作台", icon: Stethoscope },
  { path: "/error-book", label: "我的错题本", icon: BookOpen },
  { path: "/portrait", label: "学情画像看板", icon: Radar },
  { path: "/plan", label: "周计划与打卡", icon: CalendarCheck },
  { path: "/knowledge-graph", label: "课程知识图谱", icon: Network },
  { path: "/practice", label: "找题练习", icon: Search }
];

/** 教师端只展示已实现的教学入口；学生页面不再混进教师导航。 */
const teacherMenus = [
  { path: "/teacher", label: "教学概览", icon: LayoutDashboard },
  { path: "/teacher/reviews", label: "教师评阅", icon: ClipboardCheck },
  { path: "/teacher/class", label: "班级学情", icon: Users }
];

const menus = computed(() => (userRole.value === "TEACHER" ? teacherMenus : baseMenus));

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

/** /me 补到角色后，若当前页不属于该角色则纠正一次（清缓存后直接访问的场景） */
const enforceRoleRoute = (role: string) => {
  const roles = route.meta.roles as string[] | undefined;
  if (role && roles && !roles.includes(role)) {
    router.replace(role === "TEACHER" ? "/teacher" : "/today");
  }
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
      const cached = JSON.parse(raw) as MeUser;
      // 角色缺失时不能直接信缓存，否则守卫无法分流
      if (cached.role) {
        applyUser(cached);
        // 路由解析完成后（App 首次挂载时 route 可能仍是 /）再校正角色落点
        enforceRoleRoute(cached.role);
        return;
      }
    } catch {
      // 本地缓存坏了，走接口
    }
  }
  try {
    const me = await api.get<MeUser>("/api/auth/me");
    localStorage.setItem("zhixue_user", JSON.stringify(me));
    applyUser(me);
    enforceRoleRoute(me.role);
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
.skip-link {
  position: absolute;
  top: -48px;
  left: 12px;
  z-index: 100;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  padding: 13px 14px;
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
  color: var(--app-text);
  box-shadow: var(--app-shadow-lg);
  font-size: var(--app-fs-sm);
  text-decoration: none;
}
.skip-link:focus {
  top: 12px;
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.aside {
  background: var(--app-sidebar);
  border-right: 1px solid var(--app-border);
  padding: 14px 10px;
  overflow: hidden;
  transition: width 220ms var(--app-ease-standard),
    flex-basis 220ms var(--app-ease-standard), padding 220ms var(--app-ease-standard);
}
.aside.is-collapsed {
  width: 72px !important;
  flex: 0 0 72px !important;
  padding: 14px 8px !important;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px 18px;
}
.brand-id {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.side-toggle {
  width: 28px;
  height: 28px;
  flex: none;
  margin-left: auto;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0.5px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: color-mix(in srgb, var(--app-surface) 72%, transparent);
  color: var(--app-text-secondary);
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  cursor: pointer;
  transition: background-color var(--app-dur-fast) var(--app-ease-standard),
    border-color var(--app-dur-fast) var(--app-ease-standard),
    color var(--app-dur-fast) var(--app-ease-standard);
}
.side-toggle:hover {
  background: var(--app-surface);
  border-color: var(--app-border-strong);
  color: var(--app-text);
}
.side-toggle:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.side-toggle svg {
  display: block;
}
.brand-text {
  color: var(--app-text);
  font-size: var(--app-fs-lg);
  font-weight: 700;
  letter-spacing: -0.02em;
  white-space: nowrap;
}
.aside.is-collapsed .brand {
  flex-direction: column;
  gap: 8px;
  padding: 2px 0 14px;
}
.aside.is-collapsed .brand-text {
  display: none;
}
.aside.is-collapsed .side-toggle {
  margin-left: 0;
}
.menu-caption {
  padding: 2px 10px 8px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  font-weight: 500;
  letter-spacing: 0.14em;
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
.menu-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.aside.is-collapsed .menu {
  padding: 0;
}
.aside.is-collapsed .menu-caption {
  display: none;
}
.aside.is-collapsed .menu-icon {
  margin-right: 0;
}
.aside.is-collapsed .menu-label {
  display: none;
}
.aside.is-collapsed :deep(.el-menu-item) {
  justify-content: center;
  padding: 0 !important;
}
:deep(.el-menu-item) {
  /* 尺寸归 glass.css 单一来源；此处只保留非尺寸的属性，避免“看似生效”的静默失效 */
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
  /* 高度归 glass.css（52px）单一来源，避免此处静默失效 */
  padding: 0 18px;
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
html[data-theme] .title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
  font-weight: 500;
  letter-spacing: 0.01em;
}
.crumb-group {
  flex: none;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  letter-spacing: 0.02em;
  opacity: 0.8;
}
.crumb-sep {
  flex: none;
  color: var(--app-text-muted);
  opacity: 0.45;
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
  border-radius: var(--app-radius-full);
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
  font-size: var(--app-fs-sm);
  font-weight: 600;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--app-primary) 78%, white),
    var(--app-primary-strong)
  );
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.32);
}
/* 深色主题下压深头像底色，保证白色首字母 ≥4.5:1（原 78% white 叠加仅约 1.4:1） */
html[data-theme="glass-dark"] .avatar {
  background: linear-gradient(
    160deg,
    var(--app-primary-strong),
    color-mix(in srgb, var(--app-primary-strong) 78%, black)
  );
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
  font-size: var(--app-fs-sm);
  font-weight: 600;
}
.user-meta small {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
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
  border-radius: var(--app-radius-md);
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
  font-size: var(--app-fs-sm);
  font-weight: 600;
  color: var(--app-text);
}
.user-menu-head span {
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
}
.user-menu-item {
  padding: 8px 10px;
  border: none;
  border-radius: var(--app-radius-sm);
  background: transparent;
  color: var(--app-text);
  font: inherit;
  font-size: var(--app-fs-sm);
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
  padding: 20px;
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
    min-width: 44px;
    height: 44px;
  }
  .main {
    padding: 12px;
  }
}
</style>
