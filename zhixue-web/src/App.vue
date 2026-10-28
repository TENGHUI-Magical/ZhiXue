<template>
  <router-view v-if="$route.path === '/login'" />

  <el-container v-else class="layout">
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

    <el-container>
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
          <el-tag
            v-if="userRole"
            size="small"
            effect="plain"
            :type="userRole === 'TEACHER' ? 'warning' : 'info'"
            class="role-chip"
          >
            {{ roleLabel }}
          </el-tag>
          <span class="user-name">{{ userName }}</span>
          <el-button link type="primary" @click="logout">退出</el-button>
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
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { BookOpen, CalendarCheck, ClipboardCheck, Menu, Network, Radar, Stethoscope } from "@lucide/vue";
import { api } from "./api/request";

const router = useRouter();
const route = useRoute();
const userName = ref("未登录");
const userRole = ref("");
/** 移动端抽屉导航开关；桌面端由左侧栏常驻，不显示入口 */
const navOpen = ref(false);

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

interface MeUser {
  userId: number;
  username: string;
  realName: string;
  role: string;
}

const applyUser = (user: MeUser) => {
  userName.value = user.realName || user.username;
  userRole.value = user.role || "";
};

/** 路由变化时刷新用户名；本地有 token 但缺 user 信息时回查 /api/auth/me */
const refreshUser = async () => {
  if (!localStorage.getItem("satoken")) {
    userName.value = "未登录";
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
    userRole.value = "";
  }
};

watch(() => route.path, refreshUser, { immediate: true });

const logout = async () => {
  try {
    await api.post("/api/auth/logout");
  } catch {
    // 登出失败也要清本地态
  } finally {
    localStorage.removeItem("satoken");
    localStorage.removeItem("zhixue_user");
    userName.value = "未登录";
    userRole.value = "";
    await router.replace("/login");
  }
};
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
  height: 60px;
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
  gap: 8px;
}
.role-chip {
  flex: none;
}
.user-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--app-text-secondary);
  font-size: 13px;
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
    font-size: 12px;
  }
  .user-name {
    display: none;
  }
  .main {
    padding: 12px;
  }
}
</style>
