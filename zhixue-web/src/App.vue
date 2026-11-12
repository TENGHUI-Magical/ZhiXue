<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="$route.path === '/login'" />

    <el-container v-else class="layout">
      <a class="skip-link" href="#main-content">跳到主要内容</a>
      <div class="app-wall" aria-hidden="true">
        <i></i><i></i><i></i><i></i>
      </div>
      <el-aside
        width="232px"
        class="aside"
        :class="{ 'is-collapsed': railCollapsed }"
        aria-label="主导航"
      >
        <div class="brand">
          <div class="brand-id">
            <BrandMark :size="34" />
            <span class="brand-text">智学领航</span>
          </div>
          <button
            v-if="!isTablet"
            type="button"
            class="side-toggle"
            :aria-label="railCollapsed ? '展开侧栏' : '收起侧栏'"
            :aria-expanded="!railCollapsed"
            :title="railCollapsed ? '展开侧栏' : '收起侧栏'"
            @click="toggleNav"
          >
            <PanelLeftOpen v-if="railCollapsed" :size="16" aria-hidden="true" />
            <PanelLeftClose v-else :size="16" aria-hidden="true" />
          </button>
        </div>
        <div class="menu-caption">{{ userRole === "TEACHER" ? "教学" : "学习" }}</div>
        <el-menu :default-active="$route.path" router class="menu">
          <el-menu-item
            v-for="item in menus"
            :key="item.path"
            :index="item.path"
            :title="railCollapsed ? item.label : undefined"
            :aria-label="railCollapsed ? item.label : undefined"
          >
            <component :is="item.icon" class="menu-icon" :size="18" aria-hidden="true" />
            <span class="menu-label">{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container class="content" direction="vertical">
        <el-header class="header" :class="{ 'is-scrolled': headerCondensed }">
          <span class="header-title">
            <span v-if="crumbGroup" class="crumb-group">{{ crumbGroup }}</span>
            <ChevronRight v-if="crumbGroup" class="crumb-sep" :size="12" aria-hidden="true" />
            <span class="title">{{ $route.meta.title }}</span>
          </span>
          <span class="user">
            <ThemeSwitcher />
            <div ref="userRoot" class="user-switch">
              <button
                ref="userTrigger"
                type="button"
                class="user-trigger"
                aria-label="打开用户菜单"
                :aria-expanded="userMenuOpen"
                aria-haspopup="menu"
                @click="toggleUserMenu"
                @keydown.down.prevent="openUserMenu('first')"
                @keydown.up.prevent="openUserMenu('last')"
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
              <div
                v-if="userMenuOpen"
                ref="userMenuEl"
                class="user-menu"
                role="menu"
                aria-label="账号菜单"
                @keydown="onUserMenuKeydown"
              >
                <div class="user-menu-head" role="presentation">
                  <strong>{{ userName }}</strong>
                  <span>{{ roleLabel }}<template v-if="userAccount"> · {{ userAccount }}</template></span>
                </div>
                <button type="button" role="menuitem" tabindex="-1" class="user-menu-item" @click="goProfile">
                  个人中心
                </button>
                <button type="button" role="menuitem" tabindex="-1" class="user-menu-item danger" @click="logout">
                  退出登录
                </button>
              </div>
            </div>
          </span>
        </el-header>
        <el-main class="main" id="main-content" tabindex="-1" @scroll.passive="onMainScroll">
          <router-view />
        </el-main>
      </el-container>

      <!-- 手机端：底部悬浮胶囊 TabBar（桌面/平板不渲染） -->
      <nav v-if="isMobile" class="mobile-tabbar" aria-label="主导航">
        <button
          v-for="item in tabItems"
          :key="item.key"
          type="button"
          class="tab-item"
          :class="{ 'is-active': item.key === 'more' ? moreOpen : isTabActive(item.path) }"
          :aria-current="item.key !== 'more' && isTabActive(item.path) ? 'page' : undefined"
          @click="onTabClick(item)"
        >
          <component :is="item.icon" :size="22" aria-hidden="true" />
          <span class="tab-label">{{ item.label }}</span>
        </button>
      </nav>

      <!-- 更多：底部玻璃 Sheet（周计划 / 图谱 / 找题 / 个人中心 / 外观主题 / 退出） -->
      <el-drawer
        v-model="moreOpen"
        direction="btt"
        size="auto"
        :with-header="false"
        class="glass-sheet more-sheet"
        append-to-body
      >
        <div class="sheet-grabber" aria-hidden="true"></div>
        <h3 class="sheet-title">更多</h3>
        <div class="more-grid">
          <button
            v-for="item in moreItems"
            :key="item.path"
            type="button"
            class="more-item"
            @click="goPath(item.path)"
          >
            <component :is="item.icon" :size="20" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </button>
        </div>
        <div class="more-theme">
          <span class="more-theme-label">外观主题</span>
          <ThemeSwitcher />
        </div>
        <button type="button" class="more-item more-logout" @click="logout">退出登录</button>
      </el-drawer>
    </el-container>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { BookOpen, CalendarCheck, ChevronRight, ClipboardCheck, Ellipsis, LayoutDashboard, Network, PanelLeftClose, PanelLeftOpen, Radar, Search, Stethoscope, User, Users } from "@lucide/vue";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { api } from "./api/request";
import ThemeSwitcher from "./components/ThemeSwitcher.vue";
import BrandMark from "./components/BrandMark.vue";
import { useIsMobile, useIsTablet } from "./composables/useBreakpoint";
import {
  clearSession,
  getUser,
  hasSession,
  onSessionChange,
  saveUser
} from "./utils/authSession";

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
/** 手机端“更多”底部 Sheet 开关；桌面/平板不渲染 TabBar */
const moreOpen = ref(false);
/** 顶栏随内容滚动后收缩标题字号（只改字号/透明度，不改高度） */
const headerCondensed = ref(false);
const isMobile = useIsMobile();
const isTablet = useIsTablet();
const userMenuOpen = ref(false);
const userRoot = ref<HTMLElement | null>(null);
const userTrigger = ref<HTMLButtonElement | null>(null);
const NAV_STORAGE_KEY = "zhixue-nav-collapsed";
const readNavCollapsed = () => {
  try {
    return localStorage.getItem(NAV_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
};
const navCollapsed = ref(readNavCollapsed());
/** 平板（768–1023）强制 72px 图标侧栏；桌面尊重用户收起偏好 */
const railCollapsed = computed(() => navCollapsed.value || isTablet.value);

const onMainScroll = (event: Event) => {
  headerCondensed.value = (event.target as HTMLElement).scrollTop > 8;
};

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

/** 手机端底部 TabBar：学生 5 槽（更多打开 Sheet），教师 4 槽（不为凑数留空槽） */
const tabItems = computed(() =>
  userRole.value === "TEACHER"
    ? [
        { key: "teacher", path: "/teacher", label: "概览", icon: LayoutDashboard },
        { key: "reviews", path: "/teacher/reviews", label: "评阅", icon: ClipboardCheck },
        { key: "class", path: "/teacher/class", label: "班级", icon: Users },
        { key: "profile", path: "/profile", label: "我的", icon: User }
      ]
    : [
        { key: "today", path: "/today", label: "今日", icon: LayoutDashboard },
        { key: "diagnosis", path: "/diagnosis", label: "诊断", icon: Stethoscope },
        { key: "error-book", path: "/error-book", label: "错题本", icon: BookOpen },
        { key: "portrait", path: "/portrait", label: "画像", icon: Radar },
        { key: "more", path: "", label: "更多", icon: Ellipsis }
      ]
);

/** “更多”Sheet：学生端其余四个入口 + 外观主题 + 退出登录 */
const moreItems = [
  { path: "/plan", label: "周计划", icon: CalendarCheck },
  { path: "/knowledge-graph", label: "知识图谱", icon: Network },
  { path: "/practice", label: "找题练习", icon: Search },
  { path: "/profile", label: "个人中心", icon: User }
];

const isTabActive = (path: string) => Boolean(path) && route.path === path;

const onTabClick = (item: { key: string; path: string }) => {
  if (item.key === "more") {
    moreOpen.value = true;
    return;
  }
  void router.push(item.path);
};

const goPath = (path: string) => {
  moreOpen.value = false;
  void router.push(path);
};

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
  if (!hasSession()) {
    userName.value = "未登录";
    userAccount.value = "";
    userRole.value = "";
    return;
  }
  const cached = getUser();
  // 角色缺失时不能直接信缓存，否则守卫无法分流
  if (cached?.role) {
    applyUser(cached);
    // 路由解析完成后（App 首次挂载时 route 可能仍是 /）再校正角色落点
    enforceRoleRoute(cached.role);
    return;
  }
  try {
    const me = await api.get<MeUser>("/api/auth/me");
    saveUser(me);
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
/** 路由切换回到页首：`.main` 是常驻滚动容器，不重置会带着上一页的滚动位置进新页 */
watch(() => route.path, () => {
  document.getElementById("main-content")?.scrollTo({ top: 0 });
});

const goProfile = () => {
  userMenuOpen.value = false;
  router.push("/profile");
};

const logout = async () => {
  userMenuOpen.value = false;
  moreOpen.value = false;
  try {
    await api.post("/api/auth/logout");
  } catch {
    // 登出失败也要清本地态
  } finally {
    clearSession();
    userName.value = "未登录";
    userAccount.value = "";
    userRole.value = "";
    await router.replace("/login");
  }
};

// ---- 用户菜单：WAI-ARIA menu button 键盘范式（方向键 / Home / End / Esc + 焦点归还） ----
const userMenuEl = ref<HTMLElement | null>(null);
const menuItems = () =>
  Array.from(userMenuEl.value?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? []);

const focusMenuItem = (index: number) => {
  const items = menuItems();
  if (!items.length) return;
  items[(index + items.length) % items.length].focus();
};

const openUserMenu = (position: "first" | "last" = "first") => {
  userMenuOpen.value = true;
  void nextTick(() => focusMenuItem(position === "last" ? menuItems().length - 1 : 0));
};

const closeUserMenu = (restoreFocus = true) => {
  userMenuOpen.value = false;
  // v-if 卸载后焦点会落到 body，关闭时把焦点还给触发按钮
  if (restoreFocus) userTrigger.value?.focus();
};

const toggleUserMenu = () => {
  if (userMenuOpen.value) closeUserMenu(false);
  else openUserMenu("first");
};

const onUserMenuKeydown = (event: KeyboardEvent) => {
  const items = menuItems();
  const index = items.indexOf(document.activeElement as HTMLButtonElement);
  switch (event.key) {
    case "ArrowDown":
      event.preventDefault();
      focusMenuItem(index + 1);
      break;
    case "ArrowUp":
      event.preventDefault();
      focusMenuItem(index - 1);
      break;
    case "Home":
      event.preventDefault();
      focusMenuItem(0);
      break;
    case "End":
      event.preventDefault();
      focusMenuItem(items.length - 1);
      break;
    case "Escape":
      event.preventDefault();
      closeUserMenu();
      break;
    case "Tab":
      // 菜单不是模态层：Tab 关闭并把焦点交还正常文档流
      userMenuOpen.value = false;
      break;
    default:
      break;
  }
};

const onDocClick = (event: MouseEvent) => {
  if (userMenuOpen.value && userRoot.value && !userRoot.value.contains(event.target as Node)) {
    userMenuOpen.value = false;
  }
};
const onKeydown = (event: KeyboardEvent) => {
  if (event.key === "Escape" && userMenuOpen.value) closeUserMenu();
};

let stopSessionWatch: (() => void) | null = null;

onMounted(() => {
  // 刷新/直达时浏览器可能恢复上次滚动位置，首屏统一回到页首
  document.getElementById("main-content")?.scrollTo({ top: 0 });
  document.addEventListener("click", onDocClick);
  document.addEventListener("keydown", onKeydown);
  // 跨标签页登出：另一个标签页清掉 token 后，这里立即回到未登录态，避免继续显示旧身份
  stopSessionWatch = onSessionChange((loggedIn) => {
    if (loggedIn) {
      void refreshUser();
      return;
    }
    clearSession();
    userName.value = "未登录";
    userAccount.value = "";
    userRole.value = "";
    void router.replace("/login");
  });
});
onBeforeUnmount(() => {
  document.removeEventListener("click", onDocClick);
  document.removeEventListener("keydown", onKeydown);
  stopSessionWatch?.();
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
  letter-spacing: -0.01em;
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
/* 激活态（当前路由）只在 glass.css 声明一次：
   同为 scoped 时 :deep 优先级低于 html[data-theme] 前缀规则，
   在此重复声明会形成“看似生效、实际被覆盖”的第二来源。 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  /* 高度/内边距/背景归 glass.css 单一来源；此处只保留吸顶与层级 */
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
  color: var(--app-text);
  font-size: var(--app-fs-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
  transition: font-size var(--app-dur-base) var(--app-ease-spring),
    opacity var(--app-dur-base) var(--app-ease-spring);
}
/* 滚动后只收缩字号，不改变 header 高度，避免内容跳动 */
html[data-theme] .header.is-scrolled .title {
  font-size: var(--app-fs-md);
  opacity: 0.86;
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
  /* 浅色主题顶色标 78% white 时白字仅约 3.5:1：深色描边 + 暗晕保证字母在整段渐变上 ≥4.5:1，
     不改令牌色值 / color-mix 比例；深色主题底色已压深，白字本身达标，下面撤销描边。 */
  -webkit-text-stroke: 0.6px color-mix(in srgb, var(--app-primary-strong) 70%, black);
  text-shadow: 0 0 1px color-mix(in srgb, var(--app-primary-strong) 70%, black);
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
  -webkit-text-stroke: 0;
  text-shadow: none;
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
  /* 数据刷新/图谱重绘时不让浏览器自动补偿滚动位置，避免页面被“锚”到底部 */
  overflow-anchor: none;
}

/* ---------- 手机底部胶囊 TabBar（A 层；叠在已模糊的 .content 上，用高不透明实底 + 镜面边） ---------- */
.mobile-tabbar {
  display: none;
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: calc(12px + env(safe-area-inset-bottom, 0px));
  z-index: 90;
  height: 56px;
  padding: 4px;
  border: 1px solid var(--app-glass-edge-hi);
  border-radius: var(--app-radius-3xl);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.5), transparent 46%),
    color-mix(in srgb, var(--app-surface) 92%, transparent);
  box-shadow: 0 12px 30px -12px rgba(16, 24, 40, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.6);
}
html[data-theme="glass-dark"] .mobile-tabbar {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent 46%),
    color-mix(in srgb, var(--app-surface) 92%, transparent);
  box-shadow: 0 18px 44px -16px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.12);
}
.tab-item {
  flex: 1 1 0;
  min-width: 44px;
  min-height: 44px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 0;
  border: 0;
  border-radius: var(--app-radius-pill);
  background: transparent;
  color: var(--app-text-muted);
  font: inherit;
  font-size: var(--app-fs-xs);
  font-weight: 500;
  cursor: pointer;
  touch-action: manipulation;
  transition: background-color var(--app-dur-fast) var(--app-ease-standard),
    color var(--app-dur-fast) var(--app-ease-standard);
}
.tab-item.is-active {
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-weight: 600;
}
.tab-item:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.tab-item:active {
  transform: scale(0.97);
}
.tab-label {
  line-height: 1.1;
}

/* ---------- “更多”底部 Sheet：Grabber + 双列入口 + 外观主题 ---------- */
.more-sheet {
  max-height: 92dvh;
}
.more-sheet :deep(.el-drawer__body) {
  padding: 4px 16px 12px;
  overflow-y: auto;
}
.sheet-grabber {
  width: 36px;
  height: 4px;
  margin: 6px auto 10px;
  border-radius: var(--app-radius-pill);
  background: color-mix(in srgb, var(--app-text) 22%, transparent);
}
.sheet-title {
  margin: 0 0 12px;
  color: var(--app-text);
  font-size: var(--app-fs-md);
  font-weight: 600;
}
.more-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.more-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-inner);
  background: var(--app-surface-muted);
  color: var(--app-text);
  font: inherit;
  font-size: var(--app-fs-sm);
  text-align: left;
  cursor: pointer;
  touch-action: manipulation;
}
.more-item:active {
  transform: scale(0.97);
}
.more-item:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.more-theme {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0;
  padding: 10px 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-inner);
  background: var(--app-surface-muted);
}
.more-theme-label {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.more-logout {
  width: 100%;
  justify-content: center;
  color: var(--app-danger);
}

/* 触控目标：粗指针设备（平板横屏、手机横屏）也要 ≥44px，不能只判宽度 */
@media (max-width: 768px), (pointer: coarse) {
  .user-trigger {
    min-width: 44px;
    height: 44px;
  }
}

@media (max-width: 767px) {
  .aside {
    display: none;
  }
  .mobile-tabbar {
    display: flex;
    gap: 2px;
  }
  .header {
    padding: 0 12px;
  }
  .crumb-group,
  .crumb-sep {
    display: none;
  }
  /* 手机端外观主题收进“更多”Sheet，顶栏只保留标题与头像，避免 390px 挤压溢出 */
  .header :deep(.theme-seg) {
    display: none;
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
    padding: 12px 12px calc(84px + env(safe-area-inset-bottom, 0px));
  }
}
</style>
