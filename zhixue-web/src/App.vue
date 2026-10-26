<template>
  <router-view v-if="$route.path === '/login'" />

  <el-container v-else class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">智学领航</div>
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
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
            <span class="nav-icon" aria-hidden="true">☰</span>
          </el-button>
          <span class="title">{{ $route.meta.title }}</span>
        </span>
        <span class="user">
          {{ userName }}
          <el-button link type="primary" @click="logout">退出</el-button>
        </span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-drawer v-model="navOpen" direction="ltr" size="240px" :with-header="false" class="nav-drawer">
      <div class="logo drawer-logo">智学领航</div>
      <el-menu
        :default-active="$route.path"
        router
        class="menu"
        @select="navOpen = false"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "./api/request";

const router = useRouter();
const route = useRoute();
const userName = ref("未登录");
/** 移动端抽屉导航开关；桌面端由左侧栏常驻，不显示入口 */
const navOpen = ref(false);

const menus = [
  { path: "/diagnosis", label: "错题诊断工作台" },
  { path: "/error-book", label: "我的错题本" },
  { path: "/portrait", label: "学情画像看板" },
  { path: "/plan", label: "周计划与打卡" },
  { path: "/knowledge-graph", label: "课程知识图谱" }
];

interface MeUser {
  userId: number;
  username: string;
  realName: string;
  role: string;
}

const applyUser = (user: MeUser) => {
  userName.value = `${user.realName || user.username}（${user.role}）`;
};

/** 路由变化时刷新用户名；本地有 token 但缺 user 信息时回查 /api/auth/me */
const refreshUser = async () => {
  if (!localStorage.getItem("satoken")) {
    userName.value = "未登录";
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
    await router.replace("/login");
  }
};
</script>

<style scoped>
.layout {
  height: 100vh;
  height: 100dvh;
}
.aside {
  border-right: 1px solid #e5e7eb;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: #3b5bdb;
  padding: 18px 24px;
}
.menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-bottom: 1px solid #e5e7eb;
  font-weight: 600;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-toggle {
  display: none;
  flex-shrink: 0;
}
.nav-icon {
  font-size: 18px;
  line-height: 1;
}
.user {
  flex-shrink: 0;
  font-size: 13px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 8px;
}
.main {
  background: #f5f6fa;
}
.drawer-logo {
  padding: 8px 0 12px;
  font-size: 18px;
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
  .main {
    padding: 12px;
  }
}
</style>
